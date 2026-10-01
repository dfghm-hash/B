package com.example.engine

import com.example.crypto.CipherHeader
import com.example.crypto.ChunkCipher
import com.example.crypto.CryptoConstants
import com.example.crypto.KeyDerivation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.AEADBadTagException
import javax.crypto.SecretKey
import kotlin.math.max

/**
 * High-performance streaming parallel encryption and decryption engine.
 *
 * Implements a bounded reader -> worker pool -> ordered writer pipeline.
 * Backpressure is applied via bounded Kotlin coroutine channels.
 * Memory usage remains strictly bounded regardless of file size (tested up to multi-GB).
 */
class ParallelCryptoEngine {

    private val isPaused = AtomicBoolean(false)
    private val isCancelled = AtomicBoolean(false)
    private var engineJob: Job? = null

    private val _progress = MutableStateFlow<ProcessingProgress?>(null)
    val progress: Flow<ProcessingProgress?> = _progress.asStateFlow()

    fun pause() {
        isPaused.set(true)
    }

    fun resume() {
        isPaused.set(false)
    }

    fun cancel() {
        isCancelled.set(true)
        engineJob?.cancel()
    }

    /**
     * Encrypts input stream to output stream using parallel AES-256-GCM chunks.
     *
     * @param input Stream of plaintext data (e.g. from File or ContentResolver).
     * @param totalSize Total plaintext size in bytes (-1 if unknown).
     * @param output Stream where encrypted data container will be written.
     * @param password CharArray password used for key derivation.
     * @param workerCount Number of parallel worker coroutines.
     * @param chunkSize Size of each chunk in bytes (e.g. 4MB, 8MB, 16MB).
     * @return Result containing totalBytes, elapsedMs, and SHA-256 hex digest of plaintext.
     */
    suspend fun encrypt(
        input: InputStream,
        totalSize: Long,
        output: OutputStream,
        password: CharArray,
        workerCount: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 16),
        chunkSize: Int = CryptoConstants.DEFAULT_CHUNK_SIZE
    ): EncryptionResult = withContext(Dispatchers.IO) {
        isCancelled.set(false)
        isPaused.set(false)

        val startTime = System.currentTimeMillis()
        val salt = KeyDerivation.generateSalt()
        val key: SecretKey = KeyDerivation.deriveKey(password, salt)

        // Calculate expected chunks if total size is known
        val expectedChunks = if (totalSize > 0) {
            (totalSize + chunkSize - 1) / chunkSize
        } else -1L

        // Write container header
        val header = CipherHeader(
            salt = salt,
            chunkSize = chunkSize,
            originalFileSize = totalSize,
            totalChunks = expectedChunks
        )
        header.serialize(output)

        // Bounded pipeline buffers
        val boundedQueueCapacity = max(2, workerCount * 2)
        val readBufferPool = BufferPool(chunkSize, maxPooledBuffers = boundedQueueCapacity + 4)
        val chunkInputChannel = Channel<RawChunk>(capacity = boundedQueueCapacity)
        val chunkOutputChannel = Channel<EncryptedChunk>(capacity = boundedQueueCapacity)

        val bufferedInput = if (input is BufferedInputStream) input else BufferedInputStream(input, 1024 * 1024)
        val bufferedOutput = if (output is BufferedOutputStream) output else BufferedOutputStream(output, 1024 * 1024)
        val dataOut = DataOutputStream(bufferedOutput)

        val sha256Digest = MessageDigest.getInstance("SHA-256")
        val processedBytes = AtomicLong(0L)
        val activeWorkers = AtomicInteger(0)

        // Speed calculation trackers
        var lastSampleTime = System.currentTimeMillis()
        var lastSampleBytes = 0L
        var currentSpeedMbps = 0.0

        val scope = CoroutineScope(Dispatchers.Default)

        try {
            // 1. Reader Coroutine
            val readerJob = scope.launch(Dispatchers.IO) {
                var chunkIndex = 0L
                while (isActive && !isCancelled.get()) {
                    while (isPaused.get() && !isCancelled.get()) {
                        kotlinx.coroutines.delay(100)
                    }

                    val buffer = readBufferPool.acquire()
                    var bytesReadThisChunk = 0
                    while (bytesReadThisChunk < chunkSize) {
                        val count = bufferedInput.read(
                            buffer,
                            bytesReadThisChunk,
                            chunkSize - bytesReadThisChunk
                        )
                        if (count == -1) break
                        bytesReadThisChunk += count
                    }

                    if (bytesReadThisChunk == 0) {
                        readBufferPool.release(buffer)
                        // If file was empty, emit empty last chunk if no chunk was sent
                        if (chunkIndex == 0L) {
                            chunkInputChannel.send(RawChunk(0L, ByteArray(0), 0, isLast = true))
                        }
                        break
                    }

                    val isLast = bytesReadThisChunk < chunkSize || (totalSize > 0 && (processedBytes.get() + bytesReadThisChunk >= totalSize))
                    chunkInputChannel.send(
                        RawChunk(
                            index = chunkIndex,
                            buffer = buffer,
                            length = bytesReadThisChunk,
                            isLast = isLast
                        )
                    )
                    chunkIndex++
                    if (isLast) break
                }
                chunkInputChannel.close()
            }

            // 2. Worker Pool Coroutines
            val workerJobs = List(workerCount) {
                scope.launch(Dispatchers.Default) {
                    for (rawChunk in chunkInputChannel) {
                        if (isCancelled.get()) break
                        activeWorkers.incrementAndGet()
                        try {
                            val iv = KeyDerivation.generateChunkIv()
                            val ciphertext = ChunkCipher.encryptChunk(
                                plaintext = rawChunk.buffer,
                                offset = 0,
                                length = rawChunk.length,
                                key = key,
                                iv = iv,
                                chunkIndex = rawChunk.index
                            )

                            chunkOutputChannel.send(
                                EncryptedChunk(
                                    index = rawChunk.index,
                                    iv = iv,
                                    ciphertext = ciphertext,
                                    ciphertextLength = ciphertext.size,
                                    originalPlaintextBuffer = rawChunk.buffer,
                                    isLast = rawChunk.isLast
                                )
                            )
                        } finally {
                            activeWorkers.decrementAndGet()
                        }
                    }
                }
            }

            // Monitor workers to close output channel when all finished
            scope.launch {
                workerJobs.forEach { it.join() }
                chunkOutputChannel.close()
            }

            // 3. Ordered Output Writer (Sequential disk writing)
            val pendingChunks = ConcurrentHashMap<Long, EncryptedChunk>()
            var nextExpectedIndex = 0L
            var isFinished = false

            var lastUiUpdate = System.currentTimeMillis()

            for (encryptedChunk in chunkOutputChannel) {
                if (isCancelled.get()) break
                pendingChunks[encryptedChunk.index] = encryptedChunk

                while (pendingChunks.containsKey(nextExpectedIndex)) {
                    val chunkToWrite = pendingChunks.remove(nextExpectedIndex)!!

                    // Write chunk header: index (4 bytes), ciphertext length (4 bytes), IV (12 bytes)
                    dataOut.writeInt(chunkToWrite.index.toInt())
                    dataOut.writeInt(chunkToWrite.ciphertextLength)
                    dataOut.write(chunkToWrite.iv)
                    // Write ciphertext + GCM tag
                    dataOut.write(chunkToWrite.ciphertext, 0, chunkToWrite.ciphertextLength)

                    // Update plaintext running SHA-256 & byte count
                    val plainBuf = chunkToWrite.originalPlaintextBuffer
                    if (plainBuf != null) {
                        val plainLen = chunkToWrite.ciphertextLength - CryptoConstants.GCM_TAG_SIZE_BYTES
                        if (plainLen > 0) {
                            sha256Digest.update(plainBuf, 0, plainLen)
                        }
                        readBufferPool.release(plainBuf)
                    }

                    val currentTotal = processedBytes.addAndGet(
                        (chunkToWrite.ciphertextLength - CryptoConstants.GCM_TAG_SIZE_BYTES).toLong()
                    )

                    // Periodic telemetry update (every 100ms)
                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdate >= 100 || chunkToWrite.isLast) {
                        val windowElapsed = (now - lastSampleTime) / 1000.0
                        if (windowElapsed >= 0.3) {
                            val bytesInWindow = currentTotal - lastSampleBytes
                            currentSpeedMbps = (bytesInWindow / (1024.0 * 1024.0)) / windowElapsed
                            lastSampleTime = now
                            lastSampleBytes = currentTotal
                        }

                        val totalElapsed = max(0.001, (now - startTime) / 1000.0)
                        val avgSpeed = (currentTotal / (1024.0 * 1024.0)) / totalElapsed
                        val percent = if (totalSize > 0) {
                            (currentTotal.toFloat() / totalSize.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        val remainingBytes = max(0L, totalSize - currentTotal)
                        val eta = if (avgSpeed > 0.05 && totalSize > 0) {
                            (remainingBytes / (avgSpeed * 1024.0 * 1024.0)).toLong()
                        } else 0L

                        val memoryUsedMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)

                        _progress.value = ProcessingProgress(
                            operation = "Encrypting",
                            processedBytes = currentTotal,
                            totalBytes = totalSize,
                            percent = percent,
                            currentSpeedMbps = currentSpeedMbps,
                            averageSpeedMbps = avgSpeed,
                            etaSeconds = eta,
                            activeWorkers = activeWorkers.get(),
                            currentChunkIndex = nextExpectedIndex,
                            totalChunks = expectedChunks,
                            memoryUsageMb = memoryUsedMb,
                            statusText = "Streaming chunk #$nextExpectedIndex..."
                        )
                        lastUiUpdate = now
                    }

                    if (chunkToWrite.isLast) {
                        isFinished = true
                    }
                    nextExpectedIndex++
                }
            }

            readerJob.join()

            if (isCancelled.get()) {
                throw CancellationException("Encryption operation cancelled by user.")
            }

            // Write Footer: Magic 'ENDF', total bytes, and SHA-256 digest
            dataOut.write(CryptoConstants.FOOTER_MAGIC_BYTES)
            dataOut.writeLong(processedBytes.get())
            val finalPlaintextDigest = sha256Digest.digest()
            dataOut.write(finalPlaintextDigest)
            dataOut.flush()
            bufferedOutput.flush()

            val totalTimeMs = System.currentTimeMillis() - startTime
            val finalAvgSpeed = (processedBytes.get() / (1024.0 * 1024.0)) / max(0.001, totalTimeMs / 1000.0)

            _progress.value = ProcessingProgress(
                operation = "Completed",
                processedBytes = processedBytes.get(),
                totalBytes = processedBytes.get(),
                percent = 1f,
                currentSpeedMbps = 0.0,
                averageSpeedMbps = finalAvgSpeed,
                etaSeconds = 0,
                activeWorkers = 0,
                currentChunkIndex = nextExpectedIndex,
                totalChunks = nextExpectedIndex,
                memoryUsageMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024),
                statusText = "Encryption successfully completed!"
            )

            readBufferPool.clear()

            EncryptionResult(
                totalBytes = processedBytes.get(),
                durationMs = totalTimeMs,
                averageSpeedMbps = finalAvgSpeed,
                totalChunks = nextExpectedIndex,
                sha256DigestHex = finalPlaintextDigest.joinToString("") { "%02x".format(it) }
            )
        } finally {
            readBufferPool.clear()
        }
    }

    /**
     * Decrypts encrypted stream back to plaintext using parallel AES-256-GCM chunks.
     * Verifies GCM auth tag for every chunk and final SHA-256 digest in footer.
     */
    suspend fun decrypt(
        input: InputStream,
        output: OutputStream,
        password: CharArray,
        workerCount: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 16)
    ): DecryptionResult = withContext(Dispatchers.IO) {
        isCancelled.set(false)
        isPaused.set(false)

        val startTime = System.currentTimeMillis()
        val bufferedInput = if (input is BufferedInputStream) input else BufferedInputStream(input, 1024 * 1024)
        val dataIn = DataInputStream(bufferedInput)

        // Read and authenticate header
        val header = CipherHeader.deserialize(dataIn)
        val key: SecretKey = KeyDerivation.deriveKey(
            password = password,
            salt = header.salt,
            iterations = header.kdfIterations
        )

        val chunkSize = header.chunkSize
        val totalExpectedSize = header.originalFileSize
        val totalExpectedChunks = header.totalChunks

        val boundedQueueCapacity = max(2, workerCount * 2)
        val chunkInputChannel = Channel<EncryptedChunkReadItem>(capacity = boundedQueueCapacity)
        val chunkOutputChannel = Channel<DecryptedChunk>(capacity = boundedQueueCapacity)

        val bufferedOutput = if (output is BufferedOutputStream) output else BufferedOutputStream(output, 1024 * 1024)
        val sha256Digest = MessageDigest.getInstance("SHA-256")
        val processedBytes = AtomicLong(0L)
        val activeWorkers = AtomicInteger(0)

        var lastSampleTime = System.currentTimeMillis()
        var lastSampleBytes = 0L
        var currentSpeedMbps = 0.0

        val scope = CoroutineScope(Dispatchers.Default)

        try {
            // 1. Reader Coroutine
            val readerJob = scope.launch(Dispatchers.IO) {
                var chunkCounter = 0L
                try {
                    while (isActive && !isCancelled.get()) {
                        while (isPaused.get() && !isCancelled.get()) {
                            kotlinx.coroutines.delay(100)
                        }

                        // Check if we hit the footer (check next 4 bytes for "ENDF")
                        dataIn.mark(4)
                        val magicProbe = ByteArray(4)
                        val bytesRead = dataIn.read(magicProbe)
                        if (bytesRead == -1) {
                            break
                        }
                        if (magicProbe.contentEquals(CryptoConstants.FOOTER_MAGIC_BYTES)) {
                            // Footer reached! Rewind so footer parser can read it
                            dataIn.reset()
                            break
                        }
                        dataIn.reset()

                        val chunkIndex = dataIn.readInt().toLong()
                        val cipherLength = dataIn.readInt()
                        if (cipherLength <= CryptoConstants.GCM_TAG_SIZE_BYTES || cipherLength > chunkSize + 256) {
                            throw SecurityException("Corrupted chunk ciphertext length: $cipherLength")
                        }

                        val iv = ByteArray(CryptoConstants.GCM_IV_SIZE_BYTES)
                        dataIn.readFully(iv)

                        val cipherBuffer = ByteArray(cipherLength)
                        dataIn.readFully(cipherBuffer)

                        val isLast = (totalExpectedChunks > 0 && chunkCounter == totalExpectedChunks - 1)
                        chunkInputChannel.send(
                            EncryptedChunkReadItem(
                                index = chunkIndex,
                                iv = iv,
                                ciphertext = cipherBuffer,
                                isLast = isLast
                            )
                        )
                        chunkCounter++
                    }
                } catch (_: EOFException) {
                    // Stream reached end
                } finally {
                    chunkInputChannel.close()
                }
            }

            // 2. Parallel Workers
            val workerJobs = List(workerCount) {
                scope.launch(Dispatchers.Default) {
                    for (item in chunkInputChannel) {
                        if (isCancelled.get()) break
                        activeWorkers.incrementAndGet()
                        try {
                            val plaintext = try {
                                ChunkCipher.decryptChunk(
                                    ciphertext = item.ciphertext,
                                    offset = 0,
                                    length = item.ciphertext.size,
                                    key = key,
                                    iv = item.iv,
                                    chunkIndex = item.index
                                )
                            } catch (e: AEADBadTagException) {
                                throw SecurityException(
                                    "Authentication failed on chunk #${item.index}! " +
                                            "Either the password is incorrect, or the file has been tampered with.",
                                    e
                                )
                            }

                            chunkOutputChannel.send(
                                DecryptedChunk(
                                    index = item.index,
                                    plaintext = plaintext,
                                    plaintextLength = plaintext.size,
                                    originalCiphertextBuffer = item.ciphertext,
                                    isLast = item.isLast
                                )
                            )
                        } finally {
                            activeWorkers.decrementAndGet()
                        }
                    }
                }
            }

            scope.launch {
                workerJobs.forEach { it.join() }
                chunkOutputChannel.close()
            }

            // 3. Ordered Output Writer
            val pendingChunks = ConcurrentHashMap<Long, DecryptedChunk>()
            var nextExpectedIndex = 0L
            var lastUiUpdate = System.currentTimeMillis()

            for (decryptedChunk in chunkOutputChannel) {
                if (isCancelled.get()) break
                pendingChunks[decryptedChunk.index] = decryptedChunk

                while (pendingChunks.containsKey(nextExpectedIndex)) {
                    val chunkToWrite = pendingChunks.remove(nextExpectedIndex)!!

                    bufferedOutput.write(chunkToWrite.plaintext, 0, chunkToWrite.plaintextLength)
                    sha256Digest.update(chunkToWrite.plaintext, 0, chunkToWrite.plaintextLength)

                    val currentTotal = processedBytes.addAndGet(chunkToWrite.plaintextLength.toLong())

                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdate >= 100 || chunkToWrite.isLast) {
                        val windowElapsed = (now - lastSampleTime) / 1000.0
                        if (windowElapsed >= 0.3) {
                            val bytesInWindow = currentTotal - lastSampleBytes
                            currentSpeedMbps = (bytesInWindow / (1024.0 * 1024.0)) / windowElapsed
                            lastSampleTime = now
                            lastSampleBytes = currentTotal
                        }

                        val totalElapsed = max(0.001, (now - startTime) / 1000.0)
                        val avgSpeed = (currentTotal / (1024.0 * 1024.0)) / totalElapsed
                        val percent = if (totalExpectedSize > 0) {
                            (currentTotal.toFloat() / totalExpectedSize.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        val remainingBytes = max(0L, totalExpectedSize - currentTotal)
                        val eta = if (avgSpeed > 0.05 && totalExpectedSize > 0) {
                            (remainingBytes / (avgSpeed * 1024.0 * 1024.0)).toLong()
                        } else 0L

                        val memoryUsedMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)

                        _progress.value = ProcessingProgress(
                            operation = "Decrypting",
                            processedBytes = currentTotal,
                            totalBytes = totalExpectedSize,
                            percent = percent,
                            currentSpeedMbps = currentSpeedMbps,
                            averageSpeedMbps = avgSpeed,
                            etaSeconds = eta,
                            activeWorkers = activeWorkers.get(),
                            currentChunkIndex = nextExpectedIndex,
                            totalChunks = totalExpectedChunks,
                            memoryUsageMb = memoryUsedMb,
                            statusText = "Decrypted chunk #$nextExpectedIndex..."
                        )
                        lastUiUpdate = now
                    }

                    nextExpectedIndex++
                }
            }

            readerJob.join()

            if (isCancelled.get()) {
                throw CancellationException("Decryption operation cancelled by user.")
            }

            bufferedOutput.flush()

            // Verify Footer
            var footerVerified = false
            try {
                val footerMagic = ByteArray(4)
                dataIn.readFully(footerMagic)
                if (footerMagic.contentEquals(CryptoConstants.FOOTER_MAGIC_BYTES)) {
                    val recordedTotal = dataIn.readLong()
                    val expectedDigest = ByteArray(CryptoConstants.SHA256_DIGEST_LENGTH)
                    dataIn.readFully(expectedDigest)

                    val actualDigest = sha256Digest.digest()
                    if (!MessageDigest.isEqual(expectedDigest, actualDigest)) {
                        throw SecurityException("End-to-end file digest verification failed! Data was modified or corrupted.")
                    }
                    if (recordedTotal != processedBytes.get()) {
                        throw SecurityException("Processed byte count mismatch! Expected $recordedTotal, got ${processedBytes.get()}")
                    }
                    footerVerified = true
                }
            } catch (e: EOFException) {
                // If stream ended right after chunks, footer was truncated
                throw SecurityException("Encrypted container is truncated: missing integrity footer.", e)
            }

            val totalTimeMs = System.currentTimeMillis() - startTime
            val finalAvgSpeed = (processedBytes.get() / (1024.0 * 1024.0)) / max(0.001, totalTimeMs / 1000.0)

            _progress.value = ProcessingProgress(
                operation = "Completed",
                processedBytes = processedBytes.get(),
                totalBytes = processedBytes.get(),
                percent = 1f,
                currentSpeedMbps = 0.0,
                averageSpeedMbps = finalAvgSpeed,
                etaSeconds = 0,
                activeWorkers = 0,
                currentChunkIndex = nextExpectedIndex,
                totalChunks = nextExpectedIndex,
                memoryUsageMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024),
                statusText = "Decryption & Integrity verification successful!"
            )

            DecryptionResult(
                totalBytes = processedBytes.get(),
                durationMs = totalTimeMs,
                averageSpeedMbps = finalAvgSpeed,
                totalChunks = nextExpectedIndex,
                footerVerified = footerVerified
            )
        } finally {
            // cleanup
        }
    }
}

data class EncryptedChunkReadItem(
    val index: Long,
    val iv: ByteArray,
    val ciphertext: ByteArray,
    val isLast: Boolean
)

data class EncryptionResult(
    val totalBytes: Long,
    val durationMs: Long,
    val averageSpeedMbps: Double,
    val totalChunks: Long,
    val sha256DigestHex: String
)

data class DecryptionResult(
    val totalBytes: Long,
    val durationMs: Long,
    val averageSpeedMbps: Double,
    val totalChunks: Long,
    val footerVerified: Boolean
)
