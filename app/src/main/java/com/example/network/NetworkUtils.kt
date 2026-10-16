package com.example.network

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.provider.Settings
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

data class DeviceNetworkInfo(
    val ipAddress: String,
    val isWifiConnected: Boolean,
    val isHotspotOrAp: Boolean,
    val interfaceName: String,
    val ssid: String = "LinkDrop-Local"
)

object NetworkUtils {

    fun getDeviceNetworkInfo(context: Context): DeviceNetworkInfo {
        var foundIp = "127.0.0.1"
        var interfaceName = "none"
        var isHotspot = false

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                val name = iface.name.lowercase(Locale.ROOT)
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val hostAddress = addr.hostAddress ?: continue
                        // Priority to wlan0, ap0, softap, rndis
                        if (name.contains("ap") || name.contains("softap") || hostAddress.startsWith("192.168.43.")) {
                            isHotspot = true
                            foundIp = hostAddress
                            interfaceName = iface.name
                            break
                        } else if (foundIp == "127.0.0.1" || name.contains("wlan")) {
                            foundIp = hostAddress
                            interfaceName = iface.name
                        }
                    }
                }
                if (isHotspot) break
            }
        } catch (_: Exception) {
            // fallback
        }

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNetwork)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        var ssid = "Offline Network"
        if (isWifi) {
            try {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val info = wm?.connectionInfo
                val rawSsid = info?.ssid?.replace("\"", "")
                if (!rawSsid.isNullOrBlank() && rawSsid != "<unknown ssid>") {
                    ssid = rawSsid
                }
            } catch (_: Exception) {}
        } else if (isHotspot) {
            ssid = "Personal Hotspot"
        }

        return DeviceNetworkInfo(
            ipAddress = foundIp,
            isWifiConnected = isWifi,
            isHotspotOrAp = isHotspot || foundIp.startsWith("192.168.43."),
            interfaceName = interfaceName,
            ssid = ssid
        )
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec < 1024) return "$bytesPerSec B/s"
        val kb = bytesPerSec / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB/s", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.1f MB/s", mb)
    }

    fun openHotspotSettings(context: Context) {
        val tetherSettings = Intent().apply {
            action = "android.settings.TETHER_SETTINGS"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(tetherSettings)
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } catch (_: Exception) {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
        }
    }

    fun openWifiSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }
}
