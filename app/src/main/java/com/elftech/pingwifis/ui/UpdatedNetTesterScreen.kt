package com.elftech.pingwifis.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
// CORRECT IMPORTS
import com.elftech.pingwifis.data.model.RunStatus
import com.elftech.pingwifis.data.model.SpeedTestServer
import com.elftech.pingwifis.data.model.TracerouteState
import com.elftech.pingwifis.viewmodel.EnhancedNetworkViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatedNetTesterScreen(vm: EnhancedNetworkViewModel) {
    val extendedSpeed by vm.extendedSpeed.collectAsState()
    val wifi by vm.wifi.collectAsState()
    val trace by vm.trace.collectAsState()
    val serverDetails by vm.serverDetails.collectAsState()
    val clientInfo by vm.clientInfo.collectAsState()
    val isLoadingServers by vm.isLoadingServers.collectAsState()
    val availableServers by vm.availableServers.collectAsState()

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
    var showServerDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PingWiFi Pro") },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(onClick = { showServerDialog = true }) {
                            Icon(Icons.Default.Storage, contentDescription = "Servidores")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
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
                    onStartTest = { vm.startSpeedTest() },
                    onChangeServer = { showServerDialog = true }
                )
                1 -> EnhancedDiagnosisTab(
                    traceState = trace,
                    onRunTraceroute = { host -> vm.runTraceroute(host) }
                )
            }
        }

        if (showServerDialog) {
            ServerSelectionDialog(
                servers = availableServers,
                currentServer = serverDetails,
                onDismiss = { showServerDialog = false },
                onSelectServer = { server ->
                    vm.changeServer(server)
                    showServerDialog = false
                }
            )
        }
    }
}

@Composable
fun EnhancedDiagnosisTab(
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
                enabled = traceState.status != RunStatus.RUNNING,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
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

        if (traceState.lines.isNotEmpty() || traceState.error != null) {
            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSelectionDialog(
    servers: List<SpeedTestServer>,
    currentServer: SpeedTestServer?,
    onDismiss: () -> Unit,
    onSelectServer: (SpeedTestServer) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .fillMaxHeight(0.7f)
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Selecionar Servidor",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(16.dp))

                if (servers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Carregando servidores...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Text(
                        "Escolha o servidor mais próximo:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = servers.take(10)) { server ->
                            val isSelected = server == currentServer

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                ),
                                onClick = { onSelectServer(server) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = server.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "${server.city}, ${server.country}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (isSelected) {
                                        Spacer(Modifier.width(12.dp))
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Selecionado",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Fechar")
                }
            }
        }
    }
}