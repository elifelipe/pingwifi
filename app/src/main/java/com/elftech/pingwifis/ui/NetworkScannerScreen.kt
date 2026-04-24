package com.elftech.pingwifis.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elftech.pingwifis.data.DeviceType
import com.elftech.pingwifis.data.NetworkDevice
import com.elftech.pingwifis.data.model.NetworkScanState
import com.elftech.pingwifis.data.model.RunStatus
import com.elftech.pingwifis.data.model.WifiChannelData
import com.elftech.pingwifis.data.model.WifiChannelState
import androidx.compose.material3.surfaceColorAtElevation

@Composable
fun NetworkScannerScreen(
    scanState: NetworkScanState,
    channelState: WifiChannelState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onScanChannels: () -> Unit,
    onDeviceClick: (NetworkDevice) -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        NetworkScanHeader()

        // WiFi Channel Analysis
        WifiChannelCard(state = channelState, onScan = onScanChannels)

        // Control Card
        NetworkScanControlCard(
            status = scanState.status,
            progress = scanState.progress,
            devicesFound = scanState.devices.size,
            onStartScan = onStartScan,
            onStopScan = onStopScan
        )

        // Error Message
        scanState.error?.let { error ->
            ErrorCard(error)
        }

        // Devices List
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = colors.surfaceColorAtElevation(2.dp)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Dispositivos Encontrados",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface
                    )

                    if (scanState.status == RunStatus.RUNNING) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = colors.primary
                            )
                            Text(
                                "${scanState.progress}%",
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when {
                    scanState.devices.isEmpty() && scanState.status == RunStatus.IDLE -> {
                        EmptyDevicesState()
                    }
                    scanState.devices.isEmpty() && scanState.status == RunStatus.RUNNING -> {
                        ScanningState()
                    }
                    else -> {
                        DevicesList(
                            devices = scanState.devices,
                            onClick = onDeviceClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WifiChannelCard(state: WifiChannelState, onScan: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceColorAtElevation(2.dp)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    Text("Análise de Canais Wi-Fi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = colors.onSurfaceVariant)
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onScan,
                        enabled = state.status != RunStatus.RUNNING,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (state.status == RunStatus.RUNNING) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Analisando…")
                        } else {
                            Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Escanear Canais")
                        }
                    }

                    state.error?.let {
                        Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall)
                    }

                    if (state.channels.isNotEmpty()) {
                        val bands = state.channels.map { it.band }.distinct().sorted()
                        bands.forEach { band ->
                            val channelsInBand = state.channels.filter { it.band == band }
                            ChannelBandSection(band = band, channels = channelsInBand)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelBandSection(band: String, channels: List<WifiChannelData>) {
    val colors = MaterialTheme.colorScheme
    val maxNetworks = channels.maxOf { it.networkSsids.size }.coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(shape = RoundedCornerShape(4.dp), color = colors.primaryContainer) {
                Text(
                    band,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onPrimaryContainer
                )
            }
            Text(
                "${channels.size} canais em uso",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }

        channels.forEach { ch ->
            val fraction = ch.networkSsids.size.toFloat() / maxNetworks
            val congestionColor = when {
                fraction <= 0.33f -> Color(0xFF4CAF50)
                fraction <= 0.66f -> Color(0xFFFF9800)
                else -> Color(0xFFF44336)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Ch ${ch.channel}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (ch.currentChannel) FontWeight.Bold else FontWeight.Normal,
                    color = if (ch.currentChannel) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.width(44.dp)
                )

                val animFraction by animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = tween(600, easing = FastOutSlowInEasing),
                    label = "ch_bar_${ch.channel}"
                )

                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                ) {
                    drawRoundRect(
                        color = congestionColor.copy(alpha = 0.15f),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(5f)
                    )
                    drawRoundRect(
                        color = congestionColor,
                        size = Size(size.width * animFraction, size.height),
                        cornerRadius = CornerRadius(5f)
                    )
                    if (ch.currentChannel) {
                        drawLine(
                            color = colors.primary,
                            start = androidx.compose.ui.geometry.Offset(0f, size.height / 2),
                            end = androidx.compose.ui.geometry.Offset(size.width * animFraction, size.height / 2),
                            strokeWidth = 2f
                        )
                    }
                }

                Text(
                    "${ch.networkSsids.size} rede${if (ch.networkSsids.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = congestionColor,
                    modifier = Modifier.width(52.dp),
                    textAlign = TextAlign.End
                )

                if (ch.currentChannel) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "Seu canal",
                        tint = colors.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkScanHeader() {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            Icons.Default.Devices,
            contentDescription = null,
            tint = colors.tertiary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Scanner de Rede",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onBackground
        )
    }
}

@Composable
private fun NetworkScanControlCard(
    status: RunStatus,
    progress: Int,
    devicesFound: Int,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceColorAtElevation(2.dp)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stats Row
            if (status == RunStatus.RUNNING || status == RunStatus.DONE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        label = "Progresso",
                        value = "$progress%",
                        icon = Icons.Default.Speed,
                        color = colors.primary,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Dispositivos",
                        value = "$devicesFound",
                        icon = Icons.Default.Devices,
                        color = colors.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Progress Bar
            if (status == RunStatus.RUNNING) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = colors.primary,
                        trackColor = colors.outlineVariant,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                    Text(
                        "Escaneando rede local...",
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Action Button
            Button(
                onClick = {
                    if (status == RunStatus.RUNNING) {
                        onStopScan()
                    } else {
                        onStartScan()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (status == RunStatus.RUNNING)
                        colors.error else colors.tertiary,
                    contentColor = colors.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                if (status == RunStatus.RUNNING) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Parar Scan", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Iniciar Scan", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = modifier.height(90.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
        }
    }
}

@Composable
private fun ErrorCard(error: String) {
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colors.errorContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = colors.error
            )
            Text(
                text = error,
                color = colors.onErrorContainer,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun EmptyDevicesState() {
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.DevicesOther,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = colors.outline
            )
            Text(
                "Nenhum dispositivo encontrado",
                fontSize = 14.sp,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                "Clique em \"Iniciar Scan\" para buscar\ndispositivos na rede local",
                fontSize = 12.sp,
                color = colors.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ScanningState() {
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = colors.primary
            )
            Text(
                "Procurando dispositivos...",
                fontSize = 14.sp,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DevicesList(
    devices: List<NetworkDevice>,
    onClick: (NetworkDevice) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(devices) { device ->
            DeviceCard(device = device, onClick = { onClick(device) })
        }
    }
}

@Composable
private fun DeviceCard(
    device: NetworkDevice,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    val deviceColor = when (device.deviceType) {
        DeviceType.ROUTER -> Color(0xFF3B82F6)
        DeviceType.COMPUTER -> Color(0xFF8B5CF6)
        DeviceType.MOBILE -> Color(0xFF10B981)
        DeviceType.PRINTER -> Color(0xFFF59E0B)
        DeviceType.TV -> Color(0xFFEC4899)
        DeviceType.CAMERA -> Color(0xFF06B6D4)
        else -> Color(0xFF64748B)
    }

    val deviceIcon = when (device.deviceType) {
        DeviceType.ROUTER -> Icons.Default.Router
        DeviceType.COMPUTER -> Icons.Default.Computer
        DeviceType.MOBILE -> Icons.Default.PhoneAndroid
        DeviceType.PRINTER -> Icons.Default.Print
        DeviceType.TV -> Icons.Default.Tv
        DeviceType.CAMERA -> Icons.Default.Videocam
        else -> Icons.Default.DeviceUnknown
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = deviceColor.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Device Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(deviceColor.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = deviceIcon,
                    contentDescription = null,
                    tint = deviceColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Device Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Hostname or IP
                Text(
                    text = if (device.hostname != "Unknown") device.hostname else device.ipAddress,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // IP Address (se hostname existe)
                if (device.hostname != "Unknown") {
                    Text(
                        text = device.ipAddress,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.onSurfaceVariant
                    )
                }

                // Vendor
                if (device.vendor != "Unknown") {
                    Text(
                        text = device.vendor,
                        fontSize = 11.sp,
                        color = colors.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                // Open Ports
                if (device.openPorts.isNotEmpty()) {
                    Text(
                        text = "Portas: ${device.openPorts.joinToString(", ")}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            // Response Time
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${device.responseTime}ms",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        device.responseTime < 50 -> colors.secondary
                        device.responseTime < 100 -> colors.tertiary
                        else -> colors.error
                    }
                )

                // Online indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(colors.secondary, CircleShape)
                    )
                    Text(
                        text = "Online",
                        fontSize = 10.sp,
                        color = colors.secondary
                    )
                }
            }
        }
    }
}
