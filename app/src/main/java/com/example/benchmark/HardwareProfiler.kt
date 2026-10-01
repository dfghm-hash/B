package com.example.benchmark

import com.example.crypto.ChunkCipher
import com.example.crypto.CryptoConstants
import kotlin.math.max
import kotlin.math.min

data class HardwareSpecs(
    val cpuCores: Int,
    val maxHeapMemoryMb: Long,
    val freeMemoryMb: Long,
    val hasHardwareAes: Boolean,
    val osArch: String,
    val recommendedWorkers: Int,
    val recommendedChunkSizeMb: Int
)

object HardwareProfiler {

    fun profile(): HardwareSpecs {
        val runtime = Runtime.getRuntime()
        val cores = runtime.availableProcessors()
        val maxMemoryMb = runtime.maxMemory() / (1024 * 1024)
        val freeMemoryMb = runtime.freeMemory() / (1024 * 1024)
        val hasHwAes = ChunkCipher.checkHardwareAcceleration()
        val osArch = System.getProperty("os.arch") ?: "unknown"

        // Tune optimal workers based on CPU core count and memory budget
        // Never exceed 16 workers to avoid excessive thread context switching
        val optimalWorkers = when {
            cores <= 2 -> cores
            cores <= 4 -> cores
            cores <= 8 -> cores
            else -> min(cores, 12)
        }

        // Recommend chunk size: 4MB for small memory devices (< 256MB heap), 8MB for normal, 16MB for 512MB+ heap
        val optimalChunkMb = when {
            maxMemoryMb < 256 -> 4
            maxMemoryMb < 512 -> 8
            else -> 16
        }

        return HardwareSpecs(
            cpuCores = cores,
            maxHeapMemoryMb = maxMemoryMb,
            freeMemoryMb = freeMemoryMb,
            hasHardwareAes = hasHwAes,
            osArch = osArch,
            recommendedWorkers = max(1, optimalWorkers),
            recommendedChunkSizeMb = optimalChunkMb
        )
    }
}
