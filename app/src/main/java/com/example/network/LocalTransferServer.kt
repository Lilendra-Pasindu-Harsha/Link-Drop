package com.example.network

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.crypto.CryptoEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class SharedFileItem(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val uri: Uri? = null,
    val localFile: File? = null
)

interface ServerTransferListener {
    fun onFileReceived(file: File, originalName: String, size: Long, mimeType: String, checksum: String)
    fun onTransferProgress(fileName: String, bytesTransferred: Long, totalBytes: Long, speedBps: Long)
    fun onClientConnected(clientAddress: String)
}

class LocalTransferServer(
    private val context: Context,
    private val port: Int = 8080,
    private val listener: ServerTransferListener? = null
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val sharedFiles = mutableListOf<SharedFileItem>()
    private var isRunning = false

    fun updateSharedFiles(files: List<SharedFileItem>) {
        synchronized(sharedFiles) {
            sharedFiles.clear()
            sharedFiles.addAll(files)
        }
    }

    fun start(scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port)
                Log.d("LinkDropServer", "Server listening on port $port")
                while (isActive && isRunning) {
                    val client = try {
                        serverSocket?.accept()
                    } catch (e: Exception) {
                        null
                    } ?: break

                    launch(Dispatchers.IO) {
                        handleClient(client)
                    }
                }
            } catch (e: Exception) {
                Log.e("LinkDropServer", "Server error", e)
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
    }

    private fun handleClient(socket: Socket) {
        val clientIp = socket.inetAddress.hostAddress ?: "Unknown"
        listener?.onClientConnected(clientIp)

        try {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val pathWithQuery = parts[1]
            val path = pathWithQuery.substringBefore("?")
            val query = if (pathWithQuery.contains("?")) pathWithQuery.substringAfter("?") else ""

            // Read headers
            val headers = mutableMapOf<String, String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrBlank()) break
                val colonIdx = line!!.indexOf(":")
                if (colonIdx > 0) {
                    val key = line!!.substring(0, colonIdx).trim().lowercase()
                    val value = line!!.substring(colonIdx + 1).trim()
                    headers[key] = value
                }
            }

            when {
                method == "GET" && (path == "/" || path == "/share") -> {
                    serveWebSharePortal(output)
                }
                method == "GET" && path == "/download" -> {
                    val fileId = extractQueryParam(query, "id")
                    serveFileDownload(fileId, output)
                }
                method == "GET" && path == "/api/status" -> {
                    val json = """{"status":"ready","app":"LinkDrop","port":$port,"filesCount":${sharedFiles.size}}"""
                    sendHttpResponse(output, 200, "OK", "application/json", json.toByteArray())
                }
                method == "POST" && (path == "/upload" || path == "/api/upload") -> {
                    handleUpload(headers, input, output)
                }
                else -> {
                    sendHttpResponse(output, 404, "Not Found", "text/plain", "Not Found".toByteArray())
                }
            }
        } catch (e: Exception) {
            Log.e("LinkDropServer", "Client handling error", e)
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    private fun extractQueryParam(query: String, key: String): String? {
        val pairs = query.split("&")
        for (pair in pairs) {
            val kv = pair.split("=")
            if (kv.size == 2 && kv[0] == key) {
                return URLDecoder.decode(kv[1], "UTF-8")
            }
        }
        return null
    }

    private fun serveFileDownload(fileId: String?, output: OutputStream) {
        val item = synchronized(sharedFiles) {
            sharedFiles.find { it.id == fileId }
        }

        if (item == null) {
            sendHttpResponse(output, 404, "Not Found", "text/plain", "File not found".toByteArray())
            return
        }

        val inputStream: InputStream? = when {
            item.localFile != null && item.localFile.exists() -> item.localFile.inputStream()
            item.uri != null -> context.contentResolver.openInputStream(item.uri)
            else -> null
        }

        if (inputStream == null) {
            sendHttpResponse(output, 500, "Error", "text/plain", "Unable to open file".toByteArray())
            return
        }

        try {
            val header = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: ${item.mimeType}\r\n")
                append("Content-Length: ${item.size}\r\n")
                append("Content-Disposition: attachment; filename=\"${item.name}\"\r\n")
                append("Connection: close\r\n\r\n")
            }
            output.write(header.toByteArray(StandardCharsets.UTF_8))

            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            var totalSent = 0L
            val startTime = System.currentTimeMillis()

            inputStream.use { inStream ->
                while (inStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalSent += bytesRead
                    val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                    val speed = (totalSent * 1000) / elapsed
                    listener?.onTransferProgress(item.name, totalSent, item.size, speed)
                }
            }
            output.flush()
        } catch (e: Exception) {
            Log.e("LinkDropServer", "Error streaming download", e)
        }
    }

    private fun handleUpload(headers: Map<String, String>, input: InputStream, output: OutputStream) {
        val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L
        val originalName = headers["x-file-name"]?.let { URLDecoder.decode(it, "UTF-8") } ?: "received_${System.currentTimeMillis()}.dat"
        val mimeType = headers["content-type"] ?: "application/octet-stream"

        val receivedDir = File(context.getExternalFilesDir(null), "Received").apply { mkdirs() }
        val targetFile = File(receivedDir, originalName)

        val startTime = System.currentTimeMillis()
        var bytesReadTotal = 0L

        try {
            val buffer = ByteArray(64 * 1024)
            FileOutputStream(targetFile).use { fileOut ->
                var remaining = if (contentLength > 0) contentLength else Long.MAX_VALUE
                while (remaining > 0) {
                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = input.read(buffer, 0, toRead)
                    if (read == -1) break
                    fileOut.write(buffer, 0, read)
                    bytesReadTotal += read
                    if (contentLength > 0) remaining -= read
                    val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                    val speed = (bytesReadTotal * 1000) / elapsed
                    listener?.onTransferProgress(originalName, bytesReadTotal, contentLength, speed)
                }
                fileOut.flush()
            }

            val checksum = try {
                targetFile.inputStream().use { CryptoEngine.computeSha256(it) }
            } catch (_: Exception) { "" }

            listener?.onFileReceived(targetFile, originalName, bytesReadTotal, mimeType, checksum)

            val responseBody = """{"status":"success","received":$bytesReadTotal,"checksum":"$checksum"}"""
            sendHttpResponse(output, 200, "OK", "application/json", responseBody.toByteArray())
        } catch (e: Exception) {
            Log.e("LinkDropServer", "Error saving upload", e)
            sendHttpResponse(output, 500, "Upload Failed", "text/plain", e.localizedMessage?.toByteArray() ?: ByteArray(0))
        }
    }

    private fun serveWebSharePortal(output: OutputStream) {
        val filesHtml = buildString {
            synchronized(sharedFiles) {
                if (sharedFiles.isEmpty()) {
                    append("""<div class="empty">No shared files currently queued. Select files on the phone to beam them!</div>""")
                } else {
                    for (file in sharedFiles) {
                        val formattedSize = NetworkUtils.formatBytes(file.size)
                        append("""
                            <div class="file-card">
                                <div class="file-info">
                                    <span class="file-icon">📄</span>
                                    <div>
                                        <div class="file-name">${file.name}</div>
                                        <div class="file-meta">$formattedSize &bull; ${file.mimeType}</div>
                                    </div>
                                </div>
                                <a href="/download?id=${file.id}" class="dl-btn">Download</a>
                            </div>
                        """.trimIndent())
                    }
                }
            }
        }

        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>LinkDrop Web Share</title>
                <style>
                    :root {
                        --bg: #F8FAFC;
                        --surface: #FFFFFF;
                        --border: #E2E8F0;
                        --primary: #2563EB;
                        --success: #16A34A;
                        --text: #0F172A;
                        --muted: #64748B;
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        background: var(--bg);
                        color: var(--text);
                        padding: 32px 16px;
                        display: flex;
                        justify-content: center;
                    }
                    .container { max-width: 600px; width: 100%; }
                    .header {
                        display: flex;
                        align-items: center;
                        gap: 14px;
                        margin-bottom: 24px;
                        padding-bottom: 16px;
                        border-bottom: 1px solid var(--border);
                    }
                    .logo {
                        width: 44px;
                        height: 44px;
                        background: #2563EB;
                        color: white;
                        border-radius: 12px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-size: 20px;
                        font-weight: bold;
                    }
                    h1 { font-size: 20px; font-weight: 700; color: var(--text); }
                    .sub { font-size: 13px; color: var(--muted); margin-top: 2px; }
                    .badge {
                        background: #EFF6FF;
                        border: 1px solid #BFDBFE;
                        color: #1D4ED8;
                        padding: 4px 10px;
                        border-radius: 999px;
                        font-size: 11px;
                        font-weight: 600;
                        margin-left: auto;
                    }
                    .section-title {
                        font-size: 13px;
                        font-weight: 700;
                        text-transform: uppercase;
                        letter-spacing: 0.05em;
                        color: var(--muted);
                        margin: 24px 0 12px;
                    }
                    .file-card {
                        background: var(--surface);
                        border: 1px solid var(--border);
                        border-radius: 12px;
                        padding: 16px;
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        margin-bottom: 12px;
                        box-shadow: 0 1px 3px rgba(0,0,0,0.04);
                    }
                    .file-info { display: flex; align-items: center; gap: 14px; overflow: hidden; }
                    .file-icon { font-size: 24px; }
                    .file-name { font-size: 15px; font-weight: 600; color: var(--text); word-break: break-all; }
                    .file-meta { font-size: 12px; color: var(--muted); margin-top: 2px; }
                    .dl-btn {
                        background: var(--primary);
                        color: white;
                        text-decoration: none;
                        font-size: 13px;
                        font-weight: 600;
                        padding: 9px 18px;
                        border-radius: 8px;
                        flex-shrink: 0;
                        transition: opacity 0.2s;
                    }
                    .dl-btn:hover { opacity: 0.9; }
                    .empty {
                        background: var(--surface);
                        border: 1px dashed var(--border);
                        border-radius: 12px;
                        padding: 36px 16px;
                        text-align: center;
                        color: var(--muted);
                        font-size: 14px;
                    }
                    .upload-card {
                        background: var(--surface);
                        border: 2px dashed #CBD5E1;
                        border-radius: 12px;
                        padding: 28px 16px;
                        text-align: center;
                        margin-top: 24px;
                    }
                    .upload-btn {
                        background: var(--success);
                        color: white;
                        border: none;
                        padding: 10px 22px;
                        border-radius: 8px;
                        font-size: 14px;
                        font-weight: 600;
                        cursor: pointer;
                        margin-top: 14px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <div class="logo">⚡</div>
                        <div>
                            <h1>LinkDrop Portal</h1>
                            <div class="sub">Offline Local Wi-Fi Direct Peer</div>
                        </div>
                        <div class="badge">100% OFFLINE</div>
                    </div>

                    <div class="section-title">Files Ready for Download</div>
                    $filesHtml

                    <div class="upload-card">
                        <div style="font-size: 28px; margin-bottom: 8px;">📤</div>
                        <div style="font-weight: 600; font-size: 15px;">Send Files to Phone</div>
                        <div style="font-size: 12px; color: var(--muted); margin-top: 4px;">Select any file to beam it directly back to this device</div>
                        <input type="file" id="filePicker" style="display: none;" onchange="uploadFile(this.files[0])">
                        <button class="upload-btn" onclick="document.getElementById('filePicker').click()">Choose File to Beam</button>
                        <div id="uploadStatus" style="font-size: 12px; color: var(--cyan); margin-top: 10px;"></div>
                    </div>
                </div>

                <script>
                    function uploadFile(file) {
                        if (!file) return;
                        const status = document.getElementById('uploadStatus');
                        status.textContent = 'Beaming ' + file.name + ' (' + (file.size / 1024 / 1024).toFixed(2) + ' MB)...';
                        
                        const xhr = new XMLHttpRequest();
                        xhr.open('POST', '/upload', true);
                        xhr.setRequestHeader('x-file-name', encodeURIComponent(file.name));
                        xhr.setRequestHeader('content-type', file.type || 'application/octet-stream');
                        
                        xhr.upload.onprogress = function(e) {
                            if (e.lengthComputable) {
                                const percent = Math.round((e.loaded / e.total) * 100);
                                status.textContent = 'Uploading: ' + percent + '%';
                            }
                        };
                        xhr.onload = function() {
                            if (xhr.status === 200) {
                                status.textContent = '✓ Transfer complete! Saved on device.';
                                setTimeout(() => { status.textContent = ''; }, 4000);
                            } else {
                                status.textContent = 'Upload failed: ' + xhr.statusText;
                            }
                        };
                        xhr.onerror = function() {
                            status.textContent = 'Network error during upload.';
                        };
                        xhr.send(file);
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        sendHttpResponse(output, 200, "OK", "text/html; charset=UTF-8", html.toByteArray(StandardCharsets.UTF_8))
    }

    private fun sendHttpResponse(output: OutputStream, statusCode: Int, statusText: String, contentType: String, body: ByteArray) {
        val header = buildString {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(body)
        output.flush()
    }
}
