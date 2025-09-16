package com.elftech.pingwifi.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elftech.pingwifi.data.model.RunStatus
import com.elftech.pingwifi.model.TracerouteState
import com.elftech.pingwifi.viewmodel.NetworkViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetTesterScreen(vm: NetworkViewModel) {
    val extendedSpeed by vm.extendedSpeed.collectAsState()
    val wifi by vm.wifi.collectAsState()
    val trace by vm.trace.collectAsState()
    val serverDetails by vm.serverDetails.collectAsState()
    val clientInfo by vm.clientInfo.collectAsState()

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
    val tabs = listOf("Velocidade", "Diagnóstico")
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(extendedSpeed.error) {
        extendedSpeed.error?.let {
            if (it.isNotBlank()) {
                snackbarHostState.showSnackbar(message = it, withDismissAction = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PingWifi") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        icon = {
                            when (index) {
                                0 -> Icon(Icons.Filled.Speed, contentDescription = title)
                                1 -> Icon(Icons.Filled.Search, contentDescription = title)
                            }
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> ImprovedSpeedTestScreen(
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
                    onStartTest = { vm.startSpeedTest() }
                )
                1 -> DiagnosisTab(
                    traceState = trace,
                    onRunTraceroute = { host -> vm.runTraceroute(host) }
                )
            }
        }
    }
}

@Composable
fun DiagnosisTab(
    traceState: TracerouteState,
    onRunTraceroute: (String) -> Unit
) {
    var traceHost by remember { mutableStateOf("google.com") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Traceroute", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = traceHost,
                onValueChange = { traceHost = it },
                label = { Text("Host de destino") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onRunTraceroute(traceHost) },
                enabled = traceState.status != com.elftech.pingwifi.model.RunStatus.RUNNING,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Iniciar Traceroute")
            }
        }

        if (traceState.status == com.elftech.pingwifi.model.RunStatus.RUNNING && traceState.lines.isEmpty()) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        if (traceState.lines.isNotEmpty() || traceState.error != null) {
            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        if (traceState.lines.isNotEmpty()) {
                            traceState.lines.forEach { line ->
                                Text(
                                    line,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
                        traceState.error?.let {
                            Text("Erro: $it", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

