package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "crypto_jobs")
data class CryptoJobEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val fileName: String,
    val fileSizeBytes: Long,
    val operation: String, // "ENCRYPT" or "DECRYPT"
    val durationMs: Long,
    val averageSpeedMbps: Double,
    val peakSpeedMbps: Double = 0.0,
    val workerCount: Int,
    val chunkSizeMb: Int,
    val status: String, // "COMPLETED", "FAILED", "CANCELLED"
    val sha256Checksum: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
