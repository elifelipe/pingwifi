package com.elftech.pingwifi.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elftech.pingwifi.model.RunStatus
import com.elftech.pingwifi.model.TracerouteState
import com.elftech.pingwifi.viewmodel.NetworkViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetTesterScreen(vm: NetworkViewModel) {
    val speed by vm.speed.collectAsState()
    val wifi by vm.wifi.collectAsState()
    val trace by vm.trace.collectAsState()
    val serverDetails by vm.serverDetails.collectAsState()
    // NOVO: Coleta o estado das informações do cliente
    val clientInfo by vm.clientInfo.collectAsState()


    // Permissões
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

    // Mostra mensagens de fallback/erro como snackbar
    LaunchedEffect(speed.error) {
        val msg = speed.error
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message = msg, withDismissAction = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ping Wifi") },
                actions = {
                    // Ação de "testar de novo" no AppBar
                    IconButton(onClick = { if (speed.status != RunStatus.RUNNING) vm.startSpeedTest() }) {
                        Icon(
                            if (speed.status == RunStatus.RUNNING) Icons.Default.Refresh else Icons.Default.PlayArrow,
                            contentDescription = "Testar novamente"
                        )
                    }
                },
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
                        text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }

            when (selectedTab) {
                0 -> FastHomeTab(
                    status = speed.status,
                    mbps = speed.mbps,
                    progress = speed.progressPct,
                    error = speed.error,
                    wifiData = wifi,
                    serverDetails = serverDetails,
                    // NOVO: Passa os dados do cliente para a UI
                    clientInfo = clientInfo,
                    onStart = { vm.startSpeedTest() }
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
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Traceroute", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = traceHost,
                onValueChange = { traceHost = it },
                label = { Text("Host ou IP") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onRunTraceroute(traceHost) },
                enabled = traceState.status != RunStatus.RUNNING,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Iniciar Traceroute")
            }
        }

        if (traceState.status == RunStatus.RUNNING && traceState.lines.isEmpty()) {
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

        if (traceState.lines.isNotEmpty()) {
            items(traceState.lines) { line ->
                Text(line, fontFamily = FontFamily.Monospace)
            }
        }

        traceState.error?.let {
            item {
                Text("Erro: $it", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}