package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.CipherHeader
import com.example.crypto.ChunkCipher
import com.example.crypto.CryptoConstants
import com.example.crypto.KeyDerivation
import com.example.engine.BufferPool
import com.example.engine.ParallelCryptoEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Random
import javax.crypto.AEADBadTagException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `appName string matches HyperCipher`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("HyperCipher", appName)
    }

    @Test
    fun `header serialization and deserialization roundtrip`() {
        val salt = KeyDerivation.generateSalt()
        val original = CipherHeader(
            salt = salt,
            chunkSize = 4 * 1024 * 1024,
            originalFileSize = 5000000000L,
            totalChunks = 1250L
        )

        val baos = ByteArrayOutputStream()
        original.serialize(baos)

        val bais = ByteArrayInputStream(baos.toByteArray())
        val restored = CipherHeader.deserialize(bais)

        assertEquals(original.version, restored.version)
        assertEquals(original.algorithmId, restored.algorithmId)
        assertEquals(original.kdfId, restored.kdfId)
        assertEquals(original.chunkSize, restored.chunkSize)
        assertEquals(original.originalFileSize, restored.originalFileSize)
        assertEquals(original.totalChunks, restored.totalChunks)
        assertTrue(original.salt.contentEquals(restored.salt))
    }

    @Test
    fun `header tampering triggers checksum failure`() {
        val salt = KeyDerivation.generateSalt()
        val original = CipherHeader(
            salt = salt,
            chunkSize = 4 * 1024 * 1024,
            originalFileSize = 1000L,
            totalChunks = 1L
        )

        val baos = ByteArrayOutputStream()
        original.serialize(baos)
        val headerBytes = baos.toByteArray()

        // Corrupt salt byte in header
        headerBytes[15] = (headerBytes[15].toInt() xor 0xFF).toByte()

        val bais = ByteArrayInputStream(headerBytes)
        assertThrows(SecurityException::class.java) {
            CipherHeader.deserialize(bais)
        }
    }

    @Test
    fun `single chunk AES-256-GCM encryption and decryption roundtrip`() {
        val salt = KeyDerivation.generateSalt()
        val key = KeyDerivation.deriveKey("Secr3tPassword!".toCharArray(), salt)
        val iv = KeyDerivation.generateChunkIv()

        val plaintext = "High performance parallel large-file streaming encryption test".toByteArray()
        val chunkIndex = 0L

        val ciphertext = ChunkCipher.encryptChunk(
            plaintext = plaintext,
            offset = 0,
            length = plaintext.size,
            key = key,
            iv = iv,
            chunkIndex = chunkIndex
        )

        val decrypted = ChunkCipher.decryptChunk(
            ciphertext = ciphertext,
            offset = 0,
            length = ciphertext.size,
            key = key,
            iv = iv,
            chunkIndex = chunkIndex
        )

        assertTrue(plaintext.contentEquals(decrypted))
    }

    @Test
    fun `tampered ciphertext throws AEADBadTagException`() {
        val salt = KeyDerivation.generateSalt()
        val key = KeyDerivation.deriveKey("TamperKey".toCharArray(), salt)
        val iv = KeyDerivation.generateChunkIv()

        val plaintext = "Data to protect against tampering".toByteArray()
        val ciphertext = ChunkCipher.encryptChunk(plaintext, 0, plaintext.size, key, iv, 0L)

        // Flip 1 bit in ciphertext
        ciphertext[5] = (ciphertext[5].toInt() xor 0x01).toByte()

        assertThrows(AEADBadTagException::class.java) {
            ChunkCipher.decryptChunk(ciphertext, 0, ciphertext.size, key, iv, 0L)
        }
    }

    @Test
    fun `chunk reordering detection via AAD`() {
        val salt = KeyDerivation.generateSalt()
        val key = KeyDerivation.deriveKey("AadOrderKey".toCharArray(), salt)
        val iv = KeyDerivation.generateChunkIv()

        val plaintext = "Chunk index binding prevents reordering".toByteArray()
        // Encrypt with chunkIndex = 0
        val ciphertext = ChunkCipher.encryptChunk(plaintext, 0, plaintext.size, key, iv, 0L)

        // Attempt to decrypt claiming chunkIndex = 1
        assertThrows(AEADBadTagException::class.java) {
            ChunkCipher.decryptChunk(ciphertext, 0, ciphertext.size, key, iv, 1L)
        }
    }

    @Test
    fun `buffer pool reuses byte arrays correctly`() {
        val pool = BufferPool(bufferSize = 1024, maxPooledBuffers = 4)
        val buf1 = pool.acquire()
        assertEquals(1024, buf1.size)
        pool.release(buf1)
        assertEquals(1, pool.pooledCount)

        val buf2 = pool.acquire()
        // Should be the exact same instance
        assertTrue(buf1 === buf2)
        assertEquals(0, pool.pooledCount)
    }

    @Test
    fun `engine streams multi-chunk roundtrip correctly`() = runBlocking {
        val engine = ParallelCryptoEngine()
        val random = Random(1234)
        val testData = ByteArray(256 * 1024) // 256 KB
        random.nextBytes(testData)

        val input = ByteArrayInputStream(testData)
        val encOut = ByteArrayOutputStream()

        val encResult = engine.encrypt(
            input = input,
            totalSize = testData.size.toLong(),
            output = encOut,
            password = "StreamingEnginePass".toCharArray(),
            workerCount = 2,
            chunkSize = CryptoConstants.MIN_CHUNK_SIZE
        )

        assertNotNull(encResult)
        assertEquals(testData.size.toLong(), encResult.totalBytes)

        val encBytes = encOut.toByteArray()
        val decOut = ByteArrayOutputStream()

        val decResult = engine.decrypt(
            input = ByteArrayInputStream(encBytes),
            output = decOut,
            password = "StreamingEnginePass".toCharArray(),
            workerCount = 2
        )

        assertTrue(decResult.footerVerified)
        assertTrue(testData.contentEquals(decOut.toByteArray()))
    }
}
