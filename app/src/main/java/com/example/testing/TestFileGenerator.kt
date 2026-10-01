package com.example.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.random.Random

object TestFileGenerator {

    /**
     * Generates a deterministic test file on disk of [sizeBytes].
     * Returns the computed SHA-256 hash string for verification.
     */
    suspend fun generateTestFile(
        targetFile: File,
        sizeBytes: Long,
        onProgress: (Float) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024) // 64 KB write buffer
        val random = Random(42) // Deterministic seed

        BufferedOutputStream(FileOutputStream(targetFile), 1024 * 1024).use { bos ->
            var written = 0L
            while (written < sizeBytes) {
                val toWrite = minOf(buffer.size.toLong(), sizeBytes - written).toInt()
                random.nextBytes(buffer, 0, toWrite)
                bos.write(buffer, 0, toWrite)
                digest.update(buffer, 0, toWrite)
                written += toWrite
                if (sizeBytes > 0 && written % (1024 * 1024) == 0L) {
                    onProgress(written.toFloat() / sizeBytes.toFloat())
                }
            }
            bos.flush()
        }
        onProgress(1f)
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
