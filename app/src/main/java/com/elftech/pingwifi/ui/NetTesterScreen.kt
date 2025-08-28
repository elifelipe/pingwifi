package com.elftech.pingwifi.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elftech.pingwifi.R
import com.elftech.pingwifi.model.RunStatus
import com.elftech.pingwifi.model.TracerouteState
import com.elftech.pingwifi.model.WifiInfoData
import com.elftech.pingwifi.viewmodel.ClientInfo
import com.elftech.pingwifi.viewmodel.NetworkViewModel
import com.elftech.pingwifi.viewmodel.SpeedTestServer
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetTesterScreen(vm: NetworkViewModel) {
    val speed by vm.speed.collectAsState()
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
    // MODIFICADO: Usa recursos de string para os títulos das abas
    val tabs = listOf(stringResource(R.string.tab_speed), stringResource(R.string.tab_diagnosis))

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(speed.error) {
        speed.error?.let {
            if (it.isNotBlank()) {
                snackbarHostState.showSnackbar(message = it, withDismissAction = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // MODIFICADO: Usa recurso de string para o título da app
                title = { Text(stringResource(R.string.app_name)) },
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
                                0 -> Icon(Icons.Filled.NetworkWifi, contentDescription = title)
                                1 -> Icon(Icons.Filled.Search, contentDescription = title)
                            }
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> SpeedTestTab(
                    status = speed.status,
                    mbps = speed.mbps,
                    progress = speed.progressPct,
                    error = speed.error,
                    wifiData = wifi,
                    serverDetails = serverDetails,
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
fun SpeedTestTab(
    status: RunStatus,
    mbps: Double,
    progress: Float,
    error: String?,
    wifiData: WifiInfoData,
    serverDetails: SpeedTestServer?,
    clientInfo: ClientInfo?,
    onStart: () -> Unit,
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!started) {
            started = true
            onStart()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // MODIFICADO: Usa recursos de string para o texto de status
        Text(
            text = when (status) {
                RunStatus.RUNNING -> stringResource(R.string.status_testing, animatedDots())
                RunStatus.DONE -> stringResource(R.string.status_done)
                RunStatus.ERROR -> stringResource(R.string.status_error)
                else -> stringResource(R.string.status_ready)
            },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 24.dp)
        )

        SpeedIndicator(
            mbps = mbps,
            progress = progress,
            status = status,
            modifier = Modifier.weight(1f)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(vertical = 24.dp)
        ) {
            Button(
                onClick = onStart,
                enabled = status != RunStatus.RUNNING,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                // MODIFICADO: Usa recurso de string para o texto do botão
                Text(stringResource(R.string.button_test_again), fontSize = 16.sp)
            }

            error?.let {
                Text(
                    // MODIFICADO: Usa recurso de string para a etiqueta de erro
                    text = stringResource(R.string.error_label, it),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            ConnectionDetailsCard(
                wifiData = wifiData,
                serverDetails = serverDetails,
                clientInfo = clientInfo
            )
        }
    }
}

@Composable
private fun SpeedIndicator(
    mbps: Double,
    progress: Float,
    status: RunStatus,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .padding(16.dp)
    ) {
        val animatedProgress by animateFloatAsState(
            targetValue = if (status == RunStatus.RUNNING) progress.coerceIn(0f, 100f) / 100f else 0f,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
            label = "SpeedProgressRing"
        )

        val indicatorProgress = when (status) {
            RunStatus.DONE -> 1f
            else -> animatedProgress
        }

        CircularProgressIndicator(
            progress = { indicatorProgress },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 16.dp,
            strokeCap = StrokeCap.Round,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedMbps(mbps = mbps, isEmphasized = status != RunStatus.ERROR)
            Text(
                text = "Mbps",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun AnimatedMbps(mbps: Double, isEmphasized: Boolean) {
    val formattedText = remember(mbps) { "%.1f".format(mbps.coerceAtLeast(0.0)) }
    AnimatedContent(targetState = formattedText, label = "AnimatedMbps") { value ->
        Text(
            text = value,
            fontSize = if (isEmphasized) 80.sp else 60.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ConnectionDetailsCard(
    wifiData: WifiInfoData,
    serverDetails: SpeedTestServer?,
    clientInfo: ClientInfo?
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(
                    // MODIFICADO: Usa recurso de string
                    stringResource(R.string.connection_details),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            val fetchingText = stringResource(R.string.fetching)
            DetailRow(stringResource(R.string.detail_client), clientInfo?.ipAddress ?: fetchingText)
            val clientLocation = clientInfo?.let { "${it.city}, ${it.country}" } ?: fetchingText
            DetailRow(stringResource(R.string.detail_location), clientLocation)

            val serverLocation = serverDetails?.let {
                if (it.city != "N/A") "${it.city}, ${it.country}" else it.country
            } ?: fetchingText
            DetailRow(stringResource(R.string.detail_server), serverLocation)

            DetailRow(stringResource(R.string.detail_ssid), wifiData.ssid ?: stringResource(R.string.not_applicable))
            DetailRow(stringResource(R.string.detail_link_speed), wifiData.linkSpeedMbps?.let { "$it Mbps" } ?: "-")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun animatedDots(): String {
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            tick = (tick + 1) % 4
        }
    }
    return when (tick) {
        1 -> "."
        2 -> ".."
        3 -> "..."
        else -> ""
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
            // MODIFICADO: Usa recurso de string
            Text(stringResource(R.string.traceroute_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = traceHost,
                onValueChange = { traceHost = it },
                // MODIFICADO: Usa recurso de string
                label = { Text(stringResource(R.string.traceroute_host_label)) },
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
                // MODIFICADO: Usa recurso de string
                Text(stringResource(R.string.traceroute_start_button))
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
                        modifier = Modifier
                            .padding(16.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        if (traceState.lines.isNotEmpty()) {
                            traceState.lines.forEach { line ->
                                Text(line, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 4.dp))
                            }
                        }
                        traceState.error?.let {
                            // MODIFICADO: Usa recurso de string
                            Text(stringResource(R.string.error_label, it), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
