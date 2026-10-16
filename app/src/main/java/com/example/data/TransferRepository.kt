package com.example.data

import com.example.model.TransferDirection
import com.example.model.TransferRecord
import com.example.model.TransferStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class TransferAnalytics(
    val totalFilesTransferred: Int = 0,
    val totalBytesTransferred: Long = 0L,
    val sentBytes: Long = 0L,
    val receivedBytes: Long = 0L,
    val averageSpeedBps: Long = 0L,
    val successfulTransfers: Int = 0,
    val failedTransfers: Int = 0,
    val estimatedCellularDataSavedMb: Double = 0.0
)

class TransferRepository(private val transferDao: TransferDao) {
    val allRecords: Flow<List<TransferRecord>> = transferDao.getAllRecords()
    val sentRecords: Flow<List<TransferRecord>> = transferDao.getSentRecords()
    val receivedRecords: Flow<List<TransferRecord>> = transferDao.getReceivedRecords()

    val analytics: Flow<TransferAnalytics> = allRecords.map { list ->
        val completed = list.filter { it.status == TransferStatus.COMPLETED }
        val failed = list.count { it.status == TransferStatus.FAILED }
        val totalBytes = completed.sumOf { it.fileSize }
        val sent = completed.filter { it.direction == TransferDirection.SENT }.sumOf { it.fileSize }
        val received = completed.filter { it.direction == TransferDirection.RECEIVED }.sumOf { it.fileSize }
        val speeds = completed.map { it.speedBps }.filter { it > 0 }
        val avgSpeed = if (speeds.isNotEmpty()) speeds.average().toLong() else 0L

        TransferAnalytics(
            totalFilesTransferred = completed.size,
            totalBytesTransferred = totalBytes,
            sentBytes = sent,
            receivedBytes = received,
            averageSpeedBps = avgSpeed,
            successfulTransfers = completed.size,
            failedTransfers = failed,
            estimatedCellularDataSavedMb = totalBytes / (1024.0 * 1024.0)
        )
    }

    suspend fun logTransfer(record: TransferRecord): Long {
        return transferDao.insertRecord(record)
    }

    suspend fun deleteRecord(record: TransferRecord) {
        transferDao.deleteRecord(record)
    }

    suspend fun clearHistory() {
        transferDao.clearAll()
    }
}
