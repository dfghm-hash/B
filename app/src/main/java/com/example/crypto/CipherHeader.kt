package com.example.crypto

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Metadata container representing the header of an encrypted file.
 * Contains KDF parameters, chunking parameters, and an integrity checksum.
 */
data class CipherHeader(
    val version: Short = CryptoConstants.CURRENT_VERSION,
    val algorithmId: Byte = CryptoConstants.ALGORITHM_AES_256_GCM,
    val kdfId: Byte = CryptoConstants.KDF_PBKDF2_HMAC_SHA256,
    val kdfIterations: Int = CryptoConstants.DEFAULT_PBKDF2_ITERATIONS,
    val salt: ByteArray,
    val chunkSize: Int = CryptoConstants.DEFAULT_CHUNK_SIZE,
    val originalFileSize: Long = -1L,
    val totalChunks: Long = -1L
) {
    init {
        require(salt.size == CryptoConstants.SALT_SIZE_BYTES) {
            "Salt must be ${CryptoConstants.SALT_SIZE_BYTES} bytes, got ${salt.size}"
        }
        require(chunkSize in CryptoConstants.MIN_CHUNK_SIZE..CryptoConstants.MAX_CHUNK_SIZE) {
            "Invalid chunk size: $chunkSize"
        }
    }

    /**
     * Serializes this header along with its SHA-256 integrity tag into [output].
     */
    fun serialize(output: OutputStream) {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        // Write header fields
        dos.write(CryptoConstants.MAGIC_BYTES)
        dos.writeShort(version.toInt())
        dos.writeByte(algorithmId.toInt())
        dos.writeByte(kdfId.toInt())
        dos.writeInt(kdfIterations)
        dos.writeByte(salt.size)
        dos.write(salt)
        dos.writeInt(chunkSize)
        dos.writeLong(originalFileSize)
        dos.writeLong(totalChunks)
        dos.flush()

        val headerData = baos.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(headerData)

        output.write(headerData)
        output.write(digest)
        output.flush()
    }

    companion object {
        /**
         * Reads and validates a CipherHeader from [input].
         * Throws [SecurityException] or [IllegalArgumentException] if corrupted or untrusted.
         */
        fun deserialize(input: InputStream): CipherHeader {
            val dis = DataInputStream(input)

            val magic = ByteArray(CryptoConstants.MAGIC_BYTES.size)
            dis.readFully(magic)
            if (!magic.contentEquals(CryptoConstants.MAGIC_BYTES)) {
                throw SecurityException("Invalid file header: magic mismatch. Not a HyperCipher encrypted file.")
            }

            val baos = ByteArrayOutputStream()
            baos.write(magic)

            val version = dis.readShort()
            val algoId = dis.readByte()
            val kdfId = dis.readByte()
            val iterations = dis.readInt()
            val saltLen = dis.readByte().toInt() and 0xFF

            if (saltLen != CryptoConstants.SALT_SIZE_BYTES) {
                throw SecurityException("Invalid salt length in header: $saltLen")
            }

            val salt = ByteArray(saltLen)
            dis.readFully(salt)

            val chunkSize = dis.readInt()
            val originalFileSize = dis.readLong()
            val totalChunks = dis.readLong()

            // Reconstruct payload to verify SHA-256 header checksum
            val dos = DataOutputStream(baos)
            dos.writeShort(version.toInt())
            dos.writeByte(algoId.toInt())
            dos.writeByte(kdfId.toInt())
            dos.writeInt(iterations)
            dos.writeByte(saltLen)
            dos.write(salt)
            dos.writeInt(chunkSize)
            dos.writeLong(originalFileSize)
            dos.writeLong(totalChunks)
            dos.flush()

            val expectedDigest = MessageDigest.getInstance("SHA-256").digest(baos.toByteArray())
            val actualDigest = ByteArray(CryptoConstants.SHA256_DIGEST_LENGTH)
            dis.readFully(actualDigest)

            if (!MessageDigest.isEqual(expectedDigest, actualDigest)) {
                throw SecurityException("Header integrity check failed! The encrypted file header has been tampered with or corrupted.")
            }

            return CipherHeader(
                version = version,
                algorithmId = algoId,
                kdfId = kdfId,
                kdfIterations = iterations,
                salt = salt,
                chunkSize = chunkSize,
                originalFileSize = originalFileSize,
                totalChunks = totalChunks
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CipherHeader
        return version == other.version &&
                algorithmId == other.algorithmId &&
                kdfId == other.kdfId &&
                kdfIterations == other.kdfIterations &&
                salt.contentEquals(other.salt) &&
                chunkSize == other.chunkSize &&
                originalFileSize == other.originalFileSize &&
                totalChunks == other.totalChunks
    }

    override fun hashCode(): Int {
        var result = version.toInt()
        result = 31 * result + algorithmId
        result = 31 * result + kdfId
        result = 31 * result + kdfIterations
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + chunkSize
        result = 31 * result + originalFileSize.hashCode()
        result = 31 * result + totalChunks.hashCode()
        return result
    }
}
