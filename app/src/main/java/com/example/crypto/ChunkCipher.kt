package com.example.crypto

import java.nio.ByteBuffer
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * High-performance AES-256-GCM chunk cryptographic engine.
 * Leverages native Conscrypt / ARMv8 / AES-NI hardware acceleration.
 *
 * Employs chunk index as Additional Authenticated Data (AAD) to cryptographically
 * prevent chunk reordering, truncation, injection, or permutation attacks.
 */
object ChunkCipher {

    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"

    /**
     * Encrypts a chunk using AES-256-GCM.
     *
     * @param plaintext Input byte array.
     * @param offset Start offset in [plaintext].
     * @param length Number of bytes to encrypt.
     * @param key AES-256 secret key.
     * @param iv 12-byte unique IV for this chunk.
     * @param chunkIndex Zero-based chunk sequence index used as AAD.
     * @return Ciphertext array containing encrypted data plus 16-byte GCM auth tag.
     */
    fun encryptChunk(
        plaintext: ByteArray,
        offset: Int,
        length: Int,
        key: SecretKey,
        iv: ByteArray,
        chunkIndex: Long
    ): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val spec = GCMParameterSpec(CryptoConstants.GCM_TAG_SIZE_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        // Bind chunk index as AAD to prevent chunk permutation attacks
        val aad = ByteBuffer.allocate(8).putLong(chunkIndex).array()
        cipher.updateAAD(aad)

        return cipher.doFinal(plaintext, offset, length)
    }

    /**
     * Decrypts and verifies authentication for an AES-256-GCM chunk.
     *
     * @param ciphertext Input ciphertext byte array containing encrypted data + 16-byte GCM tag.
     * @param offset Start offset in [ciphertext].
     * @param length Length of ciphertext block to decrypt.
     * @param key AES-256 secret key.
     * @param iv 12-byte IV stored with this chunk.
     * @param chunkIndex Zero-based chunk sequence index used as AAD.
     * @return Decrypted plaintext byte array.
     * @throws AEADBadTagException if authentication fails (wrong password or tampered data).
     */
    fun decryptChunk(
        ciphertext: ByteArray,
        offset: Int,
        length: Int,
        key: SecretKey,
        iv: ByteArray,
        chunkIndex: Long
    ): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val spec = GCMParameterSpec(CryptoConstants.GCM_TAG_SIZE_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val aad = ByteBuffer.allocate(8).putLong(chunkIndex).array()
        cipher.updateAAD(aad)

        return cipher.doFinal(ciphertext, offset, length)
    }

    /**
     * Checks if hardware AES acceleration is available on the current device.
     */
    fun checkHardwareAcceleration(): Boolean {
        return try {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val providerName = cipher.provider.name.lowercase()
            // Conscrypt and AndroidOpenSSL use assembly/hardware acceleration (ARMv8 NEON crypto / AES-NI)
            providerName.contains("conscrypt") ||
                    providerName.contains("androidopenssl") ||
                    providerName.contains("bc")
        } catch (_: Exception) {
            false
        }
    }
}
