package com.example.crypto

import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Key derivation utility using PBKDF2-HMAC-SHA256.
 * Adheres to NIST recommendations and safely wipes sensitive memory buffers.
 */
object KeyDerivation {

    private val secureRandom = SecureRandom()

    /**
     * Generates a 32-byte cryptographically secure random salt.
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(CryptoConstants.SALT_SIZE_BYTES)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Generates a 12-byte cryptographically secure random IV for an individual AES-GCM chunk.
     */
    fun generateChunkIv(): ByteArray {
        val iv = ByteArray(CryptoConstants.GCM_IV_SIZE_BYTES)
        secureRandom.nextBytes(iv)
        return iv
    }

    /**
     * Derives an AES-256 SecretKey from a password and salt using PBKDF2-HMAC-SHA-256.
     */
    fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int = CryptoConstants.DEFAULT_PBKDF2_ITERATIONS
    ): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, CryptoConstants.KEY_SIZE_BYTES * 8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        val key = SecretKeySpec(secretBytes, "AES")
        Arrays.fill(secretBytes, 0.toByte())
        return key
    }

    /**
     * Safely zero out a byte array from memory.
     */
    fun wipe(bytes: ByteArray) {
        Arrays.fill(bytes, 0.toByte())
    }

    /**
     * Safely zero out a char array from memory.
     */
    fun wipe(chars: CharArray) {
        Arrays.fill(chars, '\u0000')
    }
}
