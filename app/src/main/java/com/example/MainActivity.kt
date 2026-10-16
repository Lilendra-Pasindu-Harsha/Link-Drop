package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ActiveTransferUiState
import com.example.ui.AppTab
import com.example.ui.TransferViewModel
import com.example.ui.components.ActiveTransferDialog
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.MeshScreen
import com.example.ui.screens.ReceiveScreen
import com.example.ui.screens.SendScreen
import com.example.ui.screens.WebShareScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.OceanBlue
import com.example.ui.theme.OceanBlueDark
import com.example.ui.theme.SageGreen
import com.example.ui.theme.SageGreenDark

class MainActivity : ComponentActivity() {

    private val viewModel: TransferViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: TransferViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()
    val activeTransfer by viewModel.activeTransfer.collectAsState()

    // Handle back button to return to Dashboard from secondary tabs
    if (currentTab != AppTab.DASHBOARD) {
        BackHandler {
            viewModel.selectTab(AppTab.DASHBOARD)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(OceanBlueDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapCalls,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "LinkDrop",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(SageGreenDark)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Direct Offline Transfer",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.selectTab(AppTab.ANALYTICS) },
                        modifier = Modifier.testTag("topbar_analytics_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "Analytics",
                            tint = if (currentTab == AppTab.ANALYTICS) OceanBlueDark else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 6.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == AppTab.DASHBOARD,
                    onClick = { viewModel.selectTab(AppTab.DASHBOARD) },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Hub") },
                    label = { Text("Hub", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = OceanBlueDark,
                        selectedTextColor = OceanBlueDark,
                        indicatorColor = OceanBlue.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.testTag("nav_dashboard")
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.SEND,
                    onClick = { viewModel.selectTab(AppTab.SEND) },
                    icon = { Icon(Icons.Default.ArrowUpward, contentDescription = "Send") },
                    label = { Text("Send", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = OceanBlueDark,
                        selectedTextColor = OceanBlueDark,
                        indicatorColor = OceanBlue.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.testTag("nav_send")
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.RECEIVE,
                    onClick = { viewModel.selectTab(AppTab.RECEIVE) },
                    icon = { Icon(Icons.Default.ArrowDownward, contentDescription = "Receive") },
                    label = { Text("Receive", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SageGreenDark,
                        selectedTextColor = SageGreenDark,
                        indicatorColor = SageGreen.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.testTag("nav_receive")
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.WEB_SHARE,
                    onClick = { viewModel.selectTab(AppTab.WEB_SHARE) },
                    icon = { Icon(Icons.Default.Language, contentDescription = "Web") },
                    label = { Text("Web", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = OceanBlueDark,
                        selectedTextColor = OceanBlueDark,
                        indicatorColor = OceanBlue.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.testTag("nav_webshare")
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.MESH,
                    onClick = { viewModel.selectTab(AppTab.MESH) },
                    icon = { Icon(Icons.Default.Hub, contentDescription = "Mesh") },
                    label = { Text("Mesh", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = OceanBlueDark,
                        selectedTextColor = OceanBlueDark,
                        indicatorColor = OceanBlue.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.testTag("nav_mesh")
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.HISTORY,
                    onClick = { viewModel.selectTab(AppTab.HISTORY) },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = OceanBlueDark,
                        selectedTextColor = OceanBlueDark,
                        indicatorColor = OceanBlue.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.testTag("nav_history")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppTab.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                AppTab.SEND -> SendScreen(viewModel = viewModel)
                AppTab.RECEIVE -> ReceiveScreen(viewModel = viewModel)
                AppTab.WEB_SHARE -> WebShareScreen(viewModel = viewModel)
                AppTab.MESH -> MeshScreen(viewModel = viewModel)
                AppTab.ANALYTICS -> AnalyticsScreen(viewModel = viewModel)
                AppTab.HISTORY -> HistoryScreen(viewModel = viewModel)
            }

            // Real-time Transfer Modal
            ActiveTransferDialog(
                state = activeTransfer,
                onDismiss = { viewModel.dismissTransferDialog() }
            )
        }
    }
}
