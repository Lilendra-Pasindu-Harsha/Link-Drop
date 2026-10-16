package com.example.network

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.crypto.CryptoEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ClientTransferProgress(
    val fileName: String,
    val bytesSent: Long,
    val totalBytes: Long,
    val speedBps: Long,
    val percent: Int
)

data class ClientTransferResult(
    val success: Boolean,
    val bytesSent: Long,
    val durationMs: Long,
    val averageSpeedBps: Long,
    val checksum: String,
    val errorMessage: String? = null
)

class TransferClient(private val context: Context) {

    suspend fun sendFile(
        peerIp: String,
        peerPort: Int,
        fileItem: SharedFileItem,
        onProgress: (ClientTransferProgress) -> Unit
    ): ClientTransferResult = withContext(Dispatchers.IO) {
        val targetUrl = URL("http://$peerIp:$peerPort/upload")
        var connection: HttpURLConnection? = null
        val startTime = System.currentTimeMillis()
        var totalBytesSent = 0L

        try {
            connection = (targetUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 30000
                setRequestProperty("Content-Type", fileItem.mimeType.ifBlank { "application/octet-stream" })
                setRequestProperty("X-File-Name", URLEncoder.encode(fileItem.name, "UTF-8"))
                setRequestProperty("Connection", "close")
                if (fileItem.size > 0) {
                    setFixedLengthStreamingMode(fileItem.size)
                } else {
                    setChunkedStreamingMode(64 * 1024)
                }
            }

            val inputStream: InputStream? = when {
                fileItem.localFile != null && fileItem.localFile.exists() -> fileItem.localFile.inputStream()
                fileItem.uri != null -> context.contentResolver.openInputStream(fileItem.uri)
                else -> null
            }

            if (inputStream == null) {
                return@withContext ClientTransferResult(
                    success = false,
                    bytesSent = 0,
                    durationMs = 0,
                    averageSpeedBps = 0,
                    checksum = "",
                    errorMessage = "Cannot open source file stream"
                )
            }

            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            val outputStream: OutputStream = connection.outputStream

            inputStream.use { inStream ->
                while (inStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesSent += bytesRead
                    val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                    val speed = (totalBytesSent * 1000) / elapsed
                    val percent = if (fileItem.size > 0) ((totalBytesSent * 100) / fileItem.size).toInt().coerceIn(0, 100) else 0

                    onProgress(
                        ClientTransferProgress(
                            fileName = fileItem.name,
                            bytesSent = totalBytesSent,
                            totalBytes = fileItem.size,
                            speedBps = speed,
                            percent = percent
                        )
                    )
                }
                outputStream.flush()
            }

            val responseCode = connection.responseCode
            val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
            val avgSpeed = (totalBytesSent * 1000) / elapsed

            val checksum = try {
                if (fileItem.localFile != null && fileItem.localFile.exists()) {
                    fileItem.localFile.inputStream().use { CryptoEngine.computeSha256(it) }
                } else if (fileItem.uri != null) {
                    context.contentResolver.openInputStream(fileItem.uri)?.use { CryptoEngine.computeSha256(it) } ?: ""
                } else ""
            } catch (_: Exception) { "" }

            if (responseCode in 200..299) {
                ClientTransferResult(
                    success = true,
                    bytesSent = totalBytesSent,
                    durationMs = elapsed,
                    averageSpeedBps = avgSpeed,
                    checksum = checksum
                )
            } else {
                ClientTransferResult(
                    success = false,
                    bytesSent = totalBytesSent,
                    durationMs = elapsed,
                    averageSpeedBps = avgSpeed,
                    checksum = checksum,
                    errorMessage = "Receiver responded with code $responseCode"
                )
            }
        } catch (e: Exception) {
            Log.e("LinkDropClient", "Send file error", e)
            val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
            val avgSpeed = if (totalBytesSent > 0) (totalBytesSent * 1000) / elapsed else 0
            ClientTransferResult(
                success = false,
                bytesSent = totalBytesSent,
                durationMs = elapsed,
                averageSpeedBps = avgSpeed,
                checksum = "",
                errorMessage = e.localizedMessage ?: "Connection failed"
            )
        } finally {
            connection?.disconnect()
        }
    }
}
