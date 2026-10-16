package com.example.optimizer

import com.example.model.TransferMode

data class OptimizationRecommendation(
    val selectedMode: TransferMode,
    val recommendedChunkSizeKb: Int,
    val enableCompression: Boolean,
    val compressionSavingsEstimatePercent: Int,
    val parallelStreams: Int,
    val reasoning: String,
    val batteryImpact: String,
    val estimatedTransferSpeedMbps: Int
)

object SmartTransferOptimizer {

    private val COMPRESSIBLE_EXTENSIONS = setOf(
        "txt", "csv", "json", "xml", "html", "css", "js",
        "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "pdf", "sql", "log", "md", "apk"
    )

    private val ALREADY_COMPRESSED_EXTENSIONS = setOf(
        "zip", "rar", "7z", "tar", "gz",
        "jpg", "jpeg", "png", "webp",
        "mp4", "mkv", "mov", "avi",
        "mp3", "aac", "flac", "ogg"
    )

    fun analyzeBatch(
        fileNames: List<String>,
        totalSizeBytes: Long,
        batteryPercent: Int = 85,
        isWifiConnected: Boolean = true,
        isHotspotActive: Boolean = false
    ): OptimizationRecommendation {
        var compressibleCount = 0
        fileNames.forEach { name ->
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext in COMPRESSIBLE_EXTENSIONS) {
                compressibleCount++
            }
        }

        val compressionBeneficial = (compressibleCount.toDouble() / fileNames.size.coerceAtLeast(1)) >= 0.4
        val estimatedSavings = if (compressionBeneficial) 45 else 0

        val isLargeBatch = totalSizeBytes > 25 * 1024 * 1024 // 25 MB+

        // Mode Decision Matrix
        val (mode, streams, speed) = when {
            isHotspotActive || isWifiConnected -> {
                if (isLargeBatch) {
                    Triple(TransferMode.MULTI_PATH, 4, 320)
                } else {
                    Triple(TransferMode.WIFI_HOTSPOT, 2, 220)
                }
            }
            batteryPercent < 20 -> {
                Triple(TransferMode.WIFI_LAN, 1, 80)
            }
            else -> {
                Triple(TransferMode.WIFI_HOTSPOT, 2, 150)
            }
        }

        val chunkSize = when {
            totalSizeBytes > 100 * 1024 * 1024 -> 1024 // 1 MB chunks
            totalSizeBytes > 10 * 1024 * 1024 -> 512   // 512 KB chunks
            else -> 128                                // 128 KB chunks
        }

        val reason = buildString {
            append(when (mode) {
                TransferMode.MULTI_PATH -> "Using Multi-Channel Streaming for maximum speed"
                TransferMode.WIFI_HOTSPOT -> "Using Direct Wi-Fi Hotspot connection"
                TransferMode.WIFI_LAN -> "Using Local Wi-Fi Network"
                TransferMode.WEB_SHARE -> "Using Web Share browser portal"
            })
            append(". ")
            if (compressionBeneficial) {
                append("Automatic compression enabled (~${estimatedSavings}% smaller transfer). ")
            } else {
                append("Direct streaming enabled for already compressed media files. ")
            }
            if (isLargeBatch) {
                append("Transfer split into 4 parallel chunks for reliability.")
            }
        }

        val battery = when {
            batteryPercent < 20 -> "Battery Saver: Throttled to preserve battery (<20%)"
            mode == TransferMode.MULTI_PATH -> "High Speed: Maximum Wi-Fi transfer rate"
            else -> "Balanced: Efficient transfer with minimal battery usage"
        }

        return OptimizationRecommendation(
            selectedMode = mode,
            recommendedChunkSizeKb = chunkSize,
            enableCompression = compressionBeneficial,
            compressionSavingsEstimatePercent = estimatedSavings,
            parallelStreams = streams,
            reasoning = reason,
            batteryImpact = battery,
            estimatedTransferSpeedMbps = speed
        )
    }
}
