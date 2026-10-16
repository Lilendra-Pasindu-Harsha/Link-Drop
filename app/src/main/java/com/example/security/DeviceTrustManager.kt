package com.example.security

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TrustedDevice(
    val id: String,
    val name: String,
    val fingerprint: String,
    val lastConnectedTime: Long = System.currentTimeMillis(),
    val isBlocked: Boolean = false,
    val autoAcceptTransfers: Boolean = true
)

class DeviceTrustManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("linkdrop_trust_prefs", Context.MODE_PRIVATE)

    private val _trustedDevices = MutableStateFlow<List<TrustedDevice>>(emptyList())
    val trustedDevices: StateFlow<List<TrustedDevice>> = _trustedDevices.asStateFlow()

    init {
        loadDevices()
    }

    private fun loadDevices() {
        // Pre-populate with typical trusted peers if empty
        val list = mutableListOf(
            TrustedDevice(
                id = "peer_macbook",
                name = "MacBook Pro (Living Room)",
                fingerprint = "4F9A:88B1:E32C",
                lastConnectedTime = System.currentTimeMillis() - 3600000,
                isBlocked = false,
                autoAcceptTransfers = true
            ),
            TrustedDevice(
                id = "peer_pixel",
                name = "Pixel 8 Pro",
                fingerprint = "A91C:2B77:99F0",
                lastConnectedTime = System.currentTimeMillis() - 7200000,
                isBlocked = false,
                autoAcceptTransfers = true
            )
        )
        _trustedDevices.value = list
    }

    fun trustDevice(name: String, fingerprint: String, autoAccept: Boolean = true) {
        val current = _trustedDevices.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.fingerprint == fingerprint }
        val device = TrustedDevice(
            id = "dev_${System.currentTimeMillis()}",
            name = name,
            fingerprint = fingerprint,
            lastConnectedTime = System.currentTimeMillis(),
            isBlocked = false,
            autoAcceptTransfers = autoAccept
        )
        if (existingIndex >= 0) {
            current[existingIndex] = device
        } else {
            current.add(0, device)
        }
        _trustedDevices.value = current
    }

    fun toggleBlock(fingerprint: String) {
        val current = _trustedDevices.value.map {
            if (it.fingerprint == fingerprint) it.copy(isBlocked = !it.isBlocked) else it
        }
        _trustedDevices.value = current
    }

    fun removeDevice(fingerprint: String) {
        _trustedDevices.value = _trustedDevices.value.filterNot { it.fingerprint == fingerprint }
    }

    fun isDeviceTrusted(fingerprint: String): Boolean {
        return _trustedDevices.value.any { it.fingerprint == fingerprint && !it.isBlocked }
    }

    fun isDeviceBlocked(fingerprint: String): Boolean {
        return _trustedDevices.value.any { it.fingerprint == fingerprint && it.isBlocked }
    }
}
