package com.example.crypto

/**
 * Constants for the HyperCipher streaming container format.
 * Uses AES-256-GCM with PBKDF2-HMAC-SHA256 for authenticated encryption.
 */
object CryptoConstants {
    // 4-byte Magic: 'H', 'Y', 'P', 'C'
    val MAGIC_BYTES = byteArrayOf(0x48.toByte(), 0x59.toByte(), 0x50.toByte(), 0x43.toByte())
    const val CURRENT_VERSION: Short = 1

    // Algorithm & KDF identifiers
    const val ALGORITHM_AES_256_GCM: Byte = 0x01
    const val KDF_PBKDF2_HMAC_SHA256: Byte = 0x01

    // Key & Salt parameters
    const val KEY_SIZE_BYTES = 32 // 256 bits
    const val SALT_SIZE_BYTES = 32 // 256 bits
    const val DEFAULT_PBKDF2_ITERATIONS = 100_000

    // GCM parameters
    const val GCM_IV_SIZE_BYTES = 12 // 96 bits (NIST standard)
    const val GCM_TAG_SIZE_BITS = 128
    const val GCM_TAG_SIZE_BYTES = 16 // 128 bits / 8

    // Chunk size defaults and bounds
    const val DEFAULT_CHUNK_SIZE = 8 * 1024 * 1024 // 8 MB default
    const val MIN_CHUNK_SIZE = 1 * 1024 * 1024 // 1 MB
    const val MAX_CHUNK_SIZE = 32 * 1024 * 1024 // 32 MB

    // Footer Magic: 'E', 'N', 'D', 'F'
    val FOOTER_MAGIC_BYTES = byteArrayOf(0x45.toByte(), 0x4E.toByte(), 0x44.toByte(), 0x46.toByte())
    const val SHA256_DIGEST_LENGTH = 32
}
