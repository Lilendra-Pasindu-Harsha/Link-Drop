package com.example.mesh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MeshNode(
    val nodeId: String,
    val deviceName: String,
    val ipAddress: String,
    val rssiDbm: Int, // e.g. -45 dBm (strong) to -85 dBm (weak)
    val batteryPercent: Int,
    val isDirectNeighbor: Boolean,
    val hopCount: Int,
    val nextHopNodeId: String? = null,
    val isRelayActive: Boolean = true
)

data class MeshTransferRoute(
    val routeId: String,
    val originName: String,
    val destinationName: String,
    val hops: List<String>,
    val totalHops: Int,
    val estimatedThroughputMbps: Double,
    val status: String
)

class OfflineMeshManager {
    private val _isMeshEnabled = MutableStateFlow(true)
    val isMeshEnabled: StateFlow<Boolean> = _isMeshEnabled.asStateFlow()

    private val _nearbyNodes = MutableStateFlow<List<MeshNode>>(emptyList())
    val nearbyNodes: StateFlow<List<MeshNode>> = _nearbyNodes.asStateFlow()

    private val _activeRoutes = MutableStateFlow<List<MeshTransferRoute>>(emptyList())
    val activeRoutes: StateFlow<List<MeshTransferRoute>> = _activeRoutes.asStateFlow()

    init {
        populateInitialMesh()
    }

    private fun populateInitialMesh() {
        val sampleNodes = listOf(
            MeshNode(
                nodeId = "node_relay_1",
                deviceName = "Galaxy S24 (Relay)",
                ipAddress = "192.168.43.15",
                rssiDbm = -52,
                batteryPercent = 91,
                isDirectNeighbor = true,
                hopCount = 1,
                nextHopNodeId = null
            ),
            MeshNode(
                nodeId = "node_dest_2",
                deviceName = "Field Tablet B (Remote)",
                ipAddress = "192.168.43.88",
                rssiDbm = -78,
                batteryPercent = 74,
                isDirectNeighbor = false,
                hopCount = 2,
                nextHopNodeId = "node_relay_1"
            ),
            MeshNode(
                nodeId = "node_relay_3",
                deviceName = "Rescue Van AP (Repeater)",
                ipAddress = "192.168.43.201",
                rssiDbm = -60,
                batteryPercent = 100,
                isDirectNeighbor = true,
                hopCount = 1,
                nextHopNodeId = null
            )
        )
        _nearbyNodes.value = sampleNodes

        val sampleRoute = MeshTransferRoute(
            routeId = "route_alpha",
            originName = "This Device (Local Host)",
            destinationName = "Field Tablet B (Remote)",
            hops = listOf("This Device", "Galaxy S24 (Relay)", "Field Tablet B"),
            totalHops = 2,
            estimatedThroughputMbps = 42.5,
            status = "Route Active (AODV Routing)"
        )
        _activeRoutes.value = listOf(sampleRoute)
    }

    fun toggleMesh(enabled: Boolean) {
        _isMeshEnabled.value = enabled
    }

    fun discoverNodes() {
        // Refresh signal strengths slightly
        _nearbyNodes.value = _nearbyNodes.value.map { node ->
            val delta = (-3..3).random()
            val newRssi = (node.rssiDbm + delta).coerceIn(-90, -40)
            node.copy(rssiDbm = newRssi)
        }
    }
}
