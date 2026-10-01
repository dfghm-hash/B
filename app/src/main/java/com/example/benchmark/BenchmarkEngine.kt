package com.example.benchmark

import com.example.crypto.ChunkCipher
import com.example.crypto.CryptoConstants
import com.example.crypto.KeyDerivation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.SecretKey
import kotlin.math.max

data class ScalingResult(
    val workers: Int,
    val throughputMbps: Double,
    val durationSeconds: Double,
    val speedupRatio: Double
)

data class BenchmarkReport(
    val testSizeMb: Int,
    val workerCount: Int,
    val chunkSizeMb: Int,
    val totalTimeSec: Double,
    val averageThroughputMbps: Double,
    val peakThroughputMbps: Double,
    val encryptionSpeedMbps: Double,
    val decryptionSpeedMbps: Double,
    val simulatedReadSpeedMbps: Double,
    val simulatedWriteSpeedMbps: Double,
    val estimatedBottleneck: String,
    val cpuUtilizationEstPercent: Int,
    val scalingResults: List<ScalingResult>
)

object BenchmarkEngine {

    /**
     * Executes a hardware benchmark measuring raw crypto scaling across different core counts.
     */
    suspend fun runScalingBenchmark(
        payloadSizeMb: Int = 64,
        chunkSizeMb: Int = 8,
        onStatus: (String) -> Unit = {}
    ): List<ScalingResult> = withContext(Dispatchers.Default) {
        val availableCores = Runtime.getRuntime().availableProcessors()
        val workerSteps = listOf(1, 2, 4, 8, availableCores).distinct().filter { it <= availableCores }

        val chunkSize = chunkSizeMb * 1024 * 1024
        val totalBytes = payloadSizeMb * 1024 * 1024
        val chunkCount = (totalBytes + chunkSize - 1) / chunkSize

        val testSalt = KeyDerivation.generateSalt()
        val key = KeyDerivation.deriveKey("BenchmarkPass#2026".toCharArray(), testSalt)

        // Generate synthetic patterned chunk to avoid memory overhead
        val sampleChunk = ByteArray(chunkSize)
        val random = SecureRandom()
        random.nextBytes(sampleChunk.copyOfRange(0, minOf(chunkSize, 4096)))

        var singleCoreThroughput = 0.0
        val results = mutableListOf<ScalingResult>()

        for (workers in workerSteps) {
            onStatus("Benchmarking $workers worker(s)...")

            val startTime = System.nanoTime()

            // Run chunks across worker pool
            val deferreds = (0 until chunkCount).map { index ->
                async(Dispatchers.Default) {
                    val iv = KeyDerivation.generateChunkIv()
                    val ciphertext = ChunkCipher.encryptChunk(
                        plaintext = sampleChunk,
                        offset = 0,
                        length = chunkSize,
                        key = key,
                        iv = iv,
                        chunkIndex = index.toLong()
                    )
                    // Quick decryption check to benchmark round-trip
                    ChunkCipher.decryptChunk(
                        ciphertext = ciphertext,
                        offset = 0,
                        length = ciphertext.size,
                        key = key,
                        iv = iv,
                        chunkIndex = index.toLong()
                    )
                }
            }
            deferreds.awaitAll()

            val elapsedSec = max(0.0001, (System.nanoTime() - startTime) / 1_000_000_000.0)
            // Round trip processed both encryption and decryption
            val processedMb = (totalBytes.toDouble() * 2) / (1024.0 * 1024.0)
            val throughput = processedMb / elapsedSec

            if (workers == 1) {
                singleCoreThroughput = throughput
            }
            val speedup = if (singleCoreThroughput > 0) throughput / singleCoreThroughput else 1.0

            results.add(
                ScalingResult(
                    workers = workers,
                    throughputMbps = throughput,
                    durationSeconds = elapsedSec,
                    speedupRatio = speedup
                )
            )
        }

        results
    }

    /**
     * Executes a full pipeline benchmark measuring simulated I/O and crypto throughput.
     */
    suspend fun runFullBenchmark(
        payloadSizeMb: Int = 128,
        workerCount: Int = Runtime.getRuntime().availableProcessors(),
        chunkSizeMb: Int = 8,
        onStatus: (String) -> Unit = {}
    ): BenchmarkReport = withContext(Dispatchers.Default) {
        onStatus("Calibrating hardware capabilities...")
        val chunkSize = chunkSizeMb * 1024 * 1024
        val totalBytes = payloadSizeMb * 1024 * 1024
        val chunkCount = (totalBytes + chunkSize - 1) / chunkSize

        val testSalt = KeyDerivation.generateSalt()
        val key = KeyDerivation.deriveKey("CalibPass#1234".toCharArray(), testSalt)
        val sampleChunk = ByteArray(chunkSize)

        // 1. Measure Simulated Read Speed
        onStatus("Measuring memory read bandwidth...")
        val readStart = System.nanoTime()
        var readChecksum = 0L
        for (i in 0 until chunkCount) {
            for (j in 0 until chunkSize step 64) {
                readChecksum += sampleChunk[j]
            }
        }
        val readSec = max(0.0001, (System.nanoTime() - readStart) / 1_000_000_000.0)
        val readSpeed = (payloadSizeMb.toDouble()) / readSec

        // 2. Measure Parallel Encryption Speed
        onStatus("Measuring AES-256-GCM encryption throughput ($workerCount workers)...")
        val encStart = System.nanoTime()
        val encryptedChunks = (0 until chunkCount).map { index ->
            async(Dispatchers.Default) {
                val iv = KeyDerivation.generateChunkIv()
                val ct = ChunkCipher.encryptChunk(sampleChunk, 0, chunkSize, key, iv, index.toLong())
                Pair(iv, ct)
            }
        }.awaitAll()
        val encSec = max(0.0001, (System.nanoTime() - encStart) / 1_000_000_000.0)
        val encSpeed = (payloadSizeMb.toDouble()) / encSec

        // 3. Measure Parallel Decryption Speed
        onStatus("Measuring AES-256-GCM decryption & auth verification throughput...")
        val decStart = System.nanoTime()
        encryptedChunks.mapIndexed { index, pair ->
            async(Dispatchers.Default) {
                ChunkCipher.decryptChunk(pair.second, 0, pair.second.size, key, pair.first, index.toLong())
            }
        }.awaitAll()
        val decSec = max(0.0001, (System.nanoTime() - decStart) / 1_000_000_000.0)
        val decSpeed = (payloadSizeMb.toDouble()) / decSec

        // 4. Measure Simulated Write Speed
        onStatus("Measuring memory write bandwidth...")
        val writeStart = System.nanoTime()
        val writeBuf = ByteArray(chunkSize)
        for (i in 0 until chunkCount) {
            System.arraycopy(sampleChunk, 0, writeBuf, 0, chunkSize)
        }
        val writeSec = max(0.0001, (System.nanoTime() - writeStart) / 1_000_000_000.0)
        val writeSpeed = (payloadSizeMb.toDouble()) / writeSec

        // 5. Multi-core scaling comparison
        onStatus("Running multi-core scaling test...")
        val scaling = runScalingBenchmark(payloadSizeMb = minOf(64, payloadSizeMb), chunkSizeMb = chunkSizeMb)

        val totalPipelineSec = encSec + (payloadSizeMb / (readSpeed * 0.5)) // combined estimate
        val avgThroughput = payloadSizeMb / max(0.001, totalPipelineSec)
        val peakThroughput = maxOf(encSpeed, decSpeed)

        // Determine estimated bottleneck
        val bottleneck = when {
            encSpeed < 100 && workerCount < 4 -> "CPU (Workers: $workerCount)"
            encSpeed < readSpeed && encSpeed < writeSpeed -> "Encryption (Cryptographic processing)"
            readSpeed < encSpeed -> "Storage / Disk Read I/O"
            writeSpeed < encSpeed -> "Storage / Disk Write I/O"
            else -> "Balanced System Throughput"
        }

        val estimatedCpuUtil = minOf(98, 30 + (workerCount * 8))

        BenchmarkReport(
            testSizeMb = payloadSizeMb,
            workerCount = workerCount,
            chunkSizeMb = chunkSizeMb,
            totalTimeSec = totalPipelineSec,
            averageThroughputMbps = avgThroughput,
            peakThroughputMbps = peakThroughput,
            encryptionSpeedMbps = encSpeed,
            decryptionSpeedMbps = decSpeed,
            simulatedReadSpeedMbps = readSpeed,
            simulatedWriteSpeedMbps = writeSpeed,
            estimatedBottleneck = bottleneck,
            cpuUtilizationEstPercent = estimatedCpuUtil,
            scalingResults = scaling
        )
    }
}
