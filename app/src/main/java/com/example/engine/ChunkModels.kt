package com.example.engine

/**
 * Representation of a raw unencrypted chunk read from the input stream.
 */
data class RawChunk(
    val index: Long,
    val buffer: ByteArray,
    val length: Int,
    val isLast: Boolean
)

/**
 * Representation of an encrypted chunk ready to be written to output.
 */
data class EncryptedChunk(
    val index: Long,
    val iv: ByteArray,
    val ciphertext: ByteArray,
    val ciphertextLength: Int,
    val originalPlaintextBuffer: ByteArray?,
    val isLast: Boolean
)

/**
 * Representation of a decrypted chunk ready to be written to output.
 */
data class DecryptedChunk(
    val index: Long,
    val plaintext: ByteArray,
    val plaintextLength: Int,
    val originalCiphertextBuffer: ByteArray?,
    val isLast: Boolean
)

/**
 * Telemetry snapshot emitted during streaming execution.
 */
data class ProcessingProgress(
    val operation: String,
    val processedBytes: Long,
    val totalBytes: Long,
    val percent: Float,
    val currentSpeedMbps: Double,
    val averageSpeedMbps: Double,
    val etaSeconds: Long,
    val activeWorkers: Int,
    val currentChunkIndex: Long,
    val totalChunks: Long,
    val memoryUsageMb: Long,
    val statusText: String
)
