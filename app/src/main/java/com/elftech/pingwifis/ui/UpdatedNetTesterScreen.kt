package com.elftech.pingwifis.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elftech.pingwifis.data.model.*
import com.elftech.pingwifis.ui.components.AppBackground
import com.elftech.pingwifis.viewmodel.EnhancedNetworkViewModel
import kotlinx.coroutines.delay

import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.surfaceColorAtElevation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatedNetTesterScreen(vm: EnhancedNetworkViewModel) {
    val extendedSpeed by vm.extendedSpeed.collectAsState()
    val wifi by vm.wifi.collectAsState()
    val ping by vm.ping.collectAsState()
    val downPing by vm.downPing.collectAsState()
    val serverDetails by vm.serverDetails.collectAsState()
    val clientInfo by vm.clientInfo.collectAsState()
    val availableServers by vm.availableServers.collectAsState()
    val isLoadingServers by vm.isLoadingServers.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.refreshWifi() }

    LaunchedEffect(Unit) {
        delay(300)
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }.toTypedArray()
        permissionLauncher.launch(perms)
    }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Velocidade", "Ping", "Rede", "Diagnóstico")

    AppBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { AppTopBar(wifiData = wifi) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                AppTabRow(
                    tabs = tabs,
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )

                Crossfade(targetState = selectedTab, label = "main_tabs") { index ->
                    when (index) {
                        0 -> ModernSpeedTestScreen(
                            status = extendedSpeed.status,
                            testPhase = extendedSpeed.currentPhase,
                            downloadMbps = extendedSpeed.downloadMbps,
                            uploadMbps = extendedSpeed.uploadMbps,
                            progress = extendedSpeed.progressPct,
                            error = extendedSpeed.error,
                            wifiData = wifi,
                            serverDetails = serverDetails,
                            clientInfo = clientInfo,
                            pingMs = extendedSpeed.pingMs,
                            jitterMs = extendedSpeed.jitterMs,
                            isLoadingServers = isLoadingServers,
                            availableServers = availableServers,
                            downloadSpeedSamples = extendedSpeed.downloadSpeedSamples,
                            uploadSpeedSamples = extendedSpeed.uploadSpeedSamples,
                            onStartTest = { vm.startSpeedTest() },
                            onSelectServer = { vm.changeServer(it) }
                        )
                        1 -> ModernPingTestScreen(
                            pingHost = ping.host,
                            onHostChange = { vm.updatePingHost(it) },
                            pingResults = ping.results,
                            pingSummary = ping.summary,
                            status = ping.status,
                            error = ping.error,
                            onStartPing = { host, count -> vm.startPingTest(host, count) },
                            onStopPing = { vm.stopPingTest() }
                        )
                        2 -> NetworkScannerScreen(
                            scanState = vm.networkScan.collectAsState().value,
                            onStartScan = { vm.startNetworkScan() },
                            onStopScan = { vm.stopNetworkScan() },
                            onDeviceClick = { device ->
                                // Atualiza o ping host e vai para aba de ping
                                vm.updatePingHost(device.ipAddress)
                                selectedTab = 1
                            }
                        )
                        3 -> DownPingDiagnosisScreen(
                            state = downPing,
                            onTargetChange = { vm.updateDownPingTarget(it) },
                            onStart = { target, pingCount -> vm.startDownPing(target, pingCount) },
                            onStop = { vm.stopDownPing() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(wifiData: WifiInfoData) {
    val colors = MaterialTheme.colorScheme
    val wifiName = wifiData.ssid?.takeIf { wifiData.isWifi && it.isNotBlank() }

    CenterAlignedTopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = colors.primary
                )
                wifiName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface
                    )
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = colors.surface.copy(alpha = 0.92f),
            scrolledContainerColor = colors.surface
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTabRow(
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(999.dp),
        tonalElevation = 2.dp,
        color = colors.surfaceColorAtElevation(2.dp)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            divider = {},
            indicator = { tabPositions ->
                val currentTab = tabPositions[selectedTab]
                Box(
                    modifier = Modifier
                        .tabIndicatorOffset(currentTab)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .fillMaxHeight()
                        .background(
                            color = colors.primary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(999.dp)
                        )
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                val selected = selectedTab == index
                Tab(
                    selected = selected,
                    onClick = { onTabSelected(index) },
                    selectedContentColor = colors.primary,
                    unselectedContentColor = colors.onSurfaceVariant,
                    text = {
                        Text(
                            title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    icon = {
                        when (index) {
                            0 -> Icon(Icons.Filled.Speed, contentDescription = title)
                            1 -> Icon(Icons.Filled.NetworkPing, contentDescription = title)
                            2 -> Icon(Icons.Filled.Devices, contentDescription = title)
                            3 -> Icon(Icons.Filled.Search, contentDescription = title)
                        }
                    }
                )
            }
        }
    }
}
