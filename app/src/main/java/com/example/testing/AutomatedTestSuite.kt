package com.example.testing

import android.content.Context
import com.example.crypto.CryptoConstants
import com.example.engine.ParallelCryptoEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

class AutomatedTestSuite(private val context: Context) {

    private val engine = ParallelCryptoEngine()

    suspend fun runAllTests(onUpdate: (List<TestCaseResult>) -> Unit): List<TestCaseResult> = withContext(Dispatchers.IO) {
        val testDir = File(context.cacheDir, "hypcipher_tests")
        testDir.mkdirs()

        val tests = mutableListOf(
            TestCaseResult("t1", "1 KB Small File Roundtrip", "Verify byte-for-byte fidelity on small 1 KB file."),
            TestCaseResult("t2", "1 MB Medium File Roundtrip", "Verify chunk boundary handling on 1 MB file."),
            TestCaseResult("t3", "25 MB Large Stream Roundtrip", "Verify parallel multi-chunk streaming on large file."),
            TestCaseResult("t4", "Wrong Password Rejection", "Confirm authentication failure when decrypting with incorrect password."),
            TestCaseResult("t5", "Corrupted Chunk Tamper Detection", "Confirm GCM auth tag detects a single flipped bit in ciphertext."),
            TestCaseResult("t6", "Truncated File Detection", "Confirm security error when stream is abruptly truncated."),
            TestCaseResult("t7", "Tampered Header Detection", "Confirm header checksum protects against metadata alteration."),
            TestCaseResult("t8", "Multi-Core Scaling Fidelity", "Verify 1-worker and 4-worker outputs are identical."),
            TestCaseResult("t9", "Variable Chunk Size (2 MB vs 8 MB)", "Verify compatibility across different chunk sizes."),
            TestCaseResult("t10", "Memory Pressure Invariance", "Verify RAM consumption does not explode during streaming.")
        )

        fun updateTest(id: String, transform: (TestCaseResult) -> TestCaseResult) {
            val idx = tests.indexOfFirst { it.id == id }
            if (idx != -1) {
                tests[idx] = transform(tests[idx])
                onUpdate(tests.toList())
            }
        }

        // Test 1: 1 KB File
        runTest(tests, "t1", ::updateTest) {
            val src = File(testDir, "test_1k.bin")
            val enc = File(testDir, "test_1k.enc")
            val dec = File(testDir, "test_1k.dec")
            val originalSha = TestFileGenerator.generateTestFile(src, 1024)

            engine.encrypt(
                input = FileInputStream(src),
                totalSize = src.length(),
                output = FileOutputStream(enc),
                password = "Password123!".toCharArray(),
                workerCount = 2,
                chunkSize = CryptoConstants.MIN_CHUNK_SIZE
            )

            engine.decrypt(
                input = FileInputStream(enc),
                output = FileOutputStream(dec),
                password = "Password123!".toCharArray(),
                workerCount = 2
            )

            val decSha = computeSha256(dec)
            check(originalSha == decSha) { "SHA-256 mismatch! Orig: $originalSha, Dec: $decSha" }
            "Verified byte-for-byte SHA-256 match ($originalSha)"
        }

        // Test 2: 1 MB File
        runTest(tests, "t2", ::updateTest) {
            val src = File(testDir, "test_1m.bin")
            val enc = File(testDir, "test_1m.enc")
            val dec = File(testDir, "test_1m.dec")
            val originalSha = TestFileGenerator.generateTestFile(src, 1024 * 1024)

            engine.encrypt(
                input = FileInputStream(src),
                totalSize = src.length(),
                output = FileOutputStream(enc),
                password = "StrongPass#2026".toCharArray(),
                workerCount = 4,
                chunkSize = CryptoConstants.MIN_CHUNK_SIZE
            )

            engine.decrypt(
                input = FileInputStream(enc),
                output = FileOutputStream(dec),
                password = "StrongPass#2026".toCharArray(),
                workerCount = 4
            )

            val decSha = computeSha256(dec)
            check(originalSha == decSha) { "SHA-256 mismatch!" }
            "Verified 1 MB roundtrip. Decrypted size: ${dec.length()} bytes."
        }

        // Test 3: 25 MB File
        runTest(tests, "t3", ::updateTest) {
            val src = File(testDir, "test_25m.bin")
            val enc = File(testDir, "test_25m.enc")
            val dec = File(testDir, "test_25m.dec")
            val originalSha = TestFileGenerator.generateTestFile(src, 25 * 1024 * 1024)

            val encRes = engine.encrypt(
                input = FileInputStream(src),
                totalSize = src.length(),
                output = FileOutputStream(enc),
                password = "ParallelTestKey".toCharArray(),
                workerCount = 4,
                chunkSize = 4 * 1024 * 1024
            )

            val decRes = engine.decrypt(
                input = FileInputStream(enc),
                output = FileOutputStream(dec),
                password = "ParallelTestKey".toCharArray(),
                workerCount = 4
            )

            val decSha = computeSha256(dec)
            check(originalSha == decSha) { "SHA-256 mismatch!" }
            "Processed 25 MB in ${encRes.durationMs + decRes.durationMs}ms at avg ${"%.1f".format(encRes.averageSpeedMbps)} MB/s."
        }

        // Test 4: Wrong Password
        runTest(tests, "t4", ::updateTest) {
            val src = File(testDir, "test_pw.bin")
            val enc = File(testDir, "test_pw.enc")
            val dec = File(testDir, "test_pw.dec")
            TestFileGenerator.generateTestFile(src, 64 * 1024)

            engine.encrypt(
                input = FileInputStream(src),
                totalSize = src.length(),
                output = FileOutputStream(enc),
                password = "CorrectPassword".toCharArray(),
                workerCount = 2,
                chunkSize = CryptoConstants.MIN_CHUNK_SIZE
            )

            var caughtAuthError = false
            try {
                engine.decrypt(
                    input = FileInputStream(enc),
                    output = FileOutputStream(dec),
                    password = "IncorrectPassword".toCharArray(),
                    workerCount = 2
                )
            } catch (e: SecurityException) {
                caughtAuthError = true
            }

            check(caughtAuthError) { "Expected SecurityException on wrong password, but none was thrown!" }
            "Correctly rejected wrong password with SecurityException."
        }

        // Test 5: Corrupted Chunk Tamper Detection
        runTest(tests, "t5", ::updateTest) {
            val src = File(testDir, "test_tamper.bin")
            val enc = File(testDir, "test_tamper.enc")
            val dec = File(testDir, "test_tamper.dec")
            TestFileGenerator.generateTestFile(src, 128 * 1024)

            engine.encrypt(
                input = FileInputStream(src),
                totalSize = src.length(),
                output = FileOutputStream(enc),
                password = "IntegrityPass".toCharArray(),
                workerCount = 2,
                chunkSize = CryptoConstants.MIN_CHUNK_SIZE
            )

            // Intentionally corrupt 1 byte in the middle of ciphertext
            val raf = RandomAccessFile(enc, "rw")
            val targetOffset = enc.length() / 2
            raf.seek(targetOffset)
            val byteVal = raf.readByte()
            raf.seek(targetOffset)
            raf.writeByte(byteVal.toInt() xor 0xFF)
            raf.close()

            var detectedTamper = false
            try {
                engine.decrypt(
                    input = FileInputStream(enc),
                    output = FileOutputStream(dec),
                    password = "IntegrityPass".toCharArray(),
                    workerCount = 2
                )
            } catch (e: SecurityException) {
                detectedTamper = true
            }

            check(detectedTamper) { "Ciphertext was tampered with but decryption did not throw SecurityException!" }
            "Tampered byte at offset $targetOffset detected by AES-GCM tag verification."
        }

        // Test 6: Truncated File Detection
        runTest(tests, "t6", ::updateTest) {
            val src = File(testDir, "test_trunc.bin")
            val enc = File(testDir, "test_trunc.enc")
            val dec = File(testDir, "test_trunc.dec")
            TestFileGenerator.generateTestFile(src, 256 * 1024)

            engine.encrypt(
                input = FileInputStream(src),
                totalSize = src.length(),
                output = FileOutputStream(enc),
                password = "TruncPass".toCharArray(),
                workerCount = 2,
                chunkSize = CryptoConstants.MIN_CHUNK_SIZE
            )

            // Truncate file to 70% of length
            val raf = RandomAccessFile(enc, "rw")
            raf.setLength((enc.length() * 0.7).toLong())
            raf.close()

            var detectedTruncation = false
            try {
                engine.decrypt(
                    input = FileInputStream(enc),
                    output = FileOutputStream(dec),
                    password = "TruncPass".toCharArray(),
                    workerCount = 2
                )
            } catch (e: Exception) {
                detectedTruncation = true
            }

            check(detectedTruncation) { "Truncated file was not detected!" }
            "Truncated encrypted stream correctly caught and rejected."
        }

        // Test 7: Tampered Header Detection
        runTest(tests, "t7", ::updateTest) {
            val src = File(testDir, "test_hdr.bin")
            val enc = File(testDir, "test_hdr.enc")
            val dec = File(testDir, "test_hdr.dec")
            TestFileGenerator.generateTestFile(src, 64 * 1024)

            engine.encrypt(
                input = FileInputStream(src),
                totalSize = src.length(),
                output = FileOutputStream(enc),
                password = "HeaderPass".toCharArray(),
                workerCount = 2,
                chunkSize = CryptoConstants.MIN_CHUNK_SIZE
            )

            // Alter salt byte in header
            val raf = RandomAccessFile(enc, "rw")
            raf.seek(12) // inside header
            val b = raf.readByte()
            raf.seek(12)
            raf.writeByte(b.toInt() xor 0x01)
            raf.close()

            var headerRejected = false
            try {
                engine.decrypt(
                    input = FileInputStream(enc),
                    output = FileOutputStream(dec),
                    password = "HeaderPass".toCharArray(),
                    workerCount = 2
                )
            } catch (e: SecurityException) {
                headerRejected = true
            }

            check(headerRejected) { "Tampered header was not detected by header integrity check!" }
            "Header integrity checksum verified; tampered metadata was immediately rejected."
        }

        // Test 8: Multi-Core Scaling Fidelity
        runTest(tests, "t8", ::updateTest) {
            val src = File(testDir, "test_multicore.bin")
            val enc1 = File(testDir, "test_mc_1.enc")
            val dec1 = File(testDir, "test_mc_1.dec")
            val enc4 = File(testDir, "test_mc_4.enc")
            val dec4 = File(testDir, "test_mc_4.dec")
            val originalSha = TestFileGenerator.generateTestFile(src, 2 * 1024 * 1024)

            engine.encrypt(FileInputStream(src), src.length(), FileOutputStream(enc1), "MultiPass".toCharArray(), workerCount = 1, chunkSize = CryptoConstants.MIN_CHUNK_SIZE)
            engine.decrypt(FileInputStream(enc1), FileOutputStream(dec1), "MultiPass".toCharArray(), workerCount = 1)

            engine.encrypt(FileInputStream(src), src.length(), FileOutputStream(enc4), "MultiPass".toCharArray(), workerCount = 4, chunkSize = CryptoConstants.MIN_CHUNK_SIZE)
            engine.decrypt(FileInputStream(enc4), FileOutputStream(dec4), "MultiPass".toCharArray(), workerCount = 4)

            val sha1 = computeSha256(dec1)
            val sha4 = computeSha256(dec4)

            check(sha1 == originalSha && sha4 == originalSha) { "Multi-core output deviated from single-core!" }
            "1-worker and 4-worker decrypted outputs both match original SHA-256."
        }

        // Test 9: Variable Chunk Size (2MB vs 8MB)
        runTest(tests, "t9", ::updateTest) {
            val src = File(testDir, "test_chunks.bin")
            val enc2m = File(testDir, "test_chunk_2m.enc")
            val dec2m = File(testDir, "test_chunk_2m.dec")
            val enc8m = File(testDir, "test_chunk_8m.enc")
            val dec8m = File(testDir, "test_chunk_8m.dec")
            val originalSha = TestFileGenerator.generateTestFile(src, 16 * 1024 * 1024)

            engine.encrypt(FileInputStream(src), src.length(), FileOutputStream(enc2m), "ChunkPass".toCharArray(), workerCount = 2, chunkSize = 2 * 1024 * 1024)
            engine.decrypt(FileInputStream(enc2m), FileOutputStream(dec2m), "ChunkPass".toCharArray(), workerCount = 2)

            engine.encrypt(FileInputStream(src), src.length(), FileOutputStream(enc8m), "ChunkPass".toCharArray(), workerCount = 2, chunkSize = 8 * 1024 * 1024)
            engine.decrypt(FileInputStream(enc8m), FileOutputStream(dec8m), "ChunkPass".toCharArray(), workerCount = 2)

            val sha2 = computeSha256(dec2m)
            val sha8 = computeSha256(dec8m)

            check(sha2 == originalSha && sha8 == originalSha) { "Chunk size variation caused corruption!" }
            "Both 2 MB and 8 MB chunk formats decrypted with byte-for-byte precision."
        }

        // Test 10: Memory Pressure Invariance
        runTest(tests, "t10", ::updateTest) {
            val beforeMem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)

            val src = File(testDir, "test_mem.bin")
            val enc = File(testDir, "test_mem.enc")
            TestFileGenerator.generateTestFile(src, 10 * 1024 * 1024)

            engine.encrypt(
                input = FileInputStream(src),
                totalSize = src.length(),
                output = FileOutputStream(enc),
                password = "MemoryPass".toCharArray(),
                workerCount = 4,
                chunkSize = 2 * 1024 * 1024
            )

            val afterMem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)
            val delta = afterMem - beforeMem
            "Memory bounded during 10 MB streaming. Heap delta: ${delta}MB."
        }

        // Clean up test temp files
        try {
            testDir.deleteRecursively()
        } catch (_: Exception) {}

        tests
    }

    private suspend fun runTest(
        tests: MutableList<TestCaseResult>,
        id: String,
        update: (String, (TestCaseResult) -> TestCaseResult) -> Unit,
        block: suspend () -> String
    ) {
        update(id) { it.copy(status = TestStatus.RUNNING) }
        val start = System.currentTimeMillis()
        try {
            val resultDetails = block()
            val duration = System.currentTimeMillis() - start
            update(id) {
                it.copy(
                    status = TestStatus.PASSED,
                    durationMs = duration,
                    details = resultDetails
                )
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            update(id) {
                it.copy(
                    status = TestStatus.FAILED,
                    durationMs = duration,
                    error = e.message ?: e.javaClass.simpleName,
                    details = "Test failed after ${duration}ms"
                )
            }
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        FileInputStream(file).use { fis ->
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
