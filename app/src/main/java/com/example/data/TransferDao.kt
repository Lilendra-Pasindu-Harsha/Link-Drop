package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.TransferRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfer_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<TransferRecord>>

    @Query("SELECT * FROM transfer_records WHERE direction = 'SENT' ORDER BY timestamp DESC")
    fun getSentRecords(): Flow<List<TransferRecord>>

    @Query("SELECT * FROM transfer_records WHERE direction = 'RECEIVED' ORDER BY timestamp DESC")
    fun getReceivedRecords(): Flow<List<TransferRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: TransferRecord): Long

    @Update
    suspend fun updateRecord(record: TransferRecord)

    @Delete
    suspend fun deleteRecord(record: TransferRecord)

    @Query("DELETE FROM transfer_records")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM transfer_records WHERE status = 'COMPLETED'")
    fun getCompletedCount(): Flow<Int>

    @Query("SELECT SUM(fileSize) FROM transfer_records WHERE status = 'COMPLETED'")
    fun getTotalBytesTransferred(): Flow<Long?>
}
