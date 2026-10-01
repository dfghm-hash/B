package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CryptoHistoryDao {

    @Query("SELECT * FROM crypto_jobs ORDER BY timestamp DESC")
    fun getAllJobs(): Flow<List<CryptoJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: CryptoJobEntity): Long

    @Query("DELETE FROM crypto_jobs WHERE id = :id")
    suspend fun deleteJob(id: Long)

    @Query("DELETE FROM crypto_jobs")
    suspend fun clearHistory()
}
