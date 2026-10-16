package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TransferDirection {
    SENT,
    RECEIVED
}

enum class TransferStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class TransferMode {
    WIFI_HOTSPOT,
    WIFI_LAN,
    WEB_SHARE,
    MULTI_PATH
}

@Entity(tableName = "transfer_records")
data class TransferRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val filePath: String = "",
    val fileSize: Long,
    val mimeType: String,
    val direction: TransferDirection,
    val status: TransferStatus = TransferStatus.COMPLETED,
    val speedBps: Long = 0,
    val durationMs: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val peerName: String = "Nearby Peer",
    val peerIp: String = "",
    val isEncrypted: Boolean = true,
    val checksumSha256: String = "",
    val transferMode: TransferMode = TransferMode.WIFI_HOTSPOT,
    val isWebShare: Boolean = false
)
