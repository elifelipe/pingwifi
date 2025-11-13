package com.elftech.pingwifis.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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

@Composable
fun NetworkScannerScreen(
    scanState: NetworkScanState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onDeviceClick: (NetworkDevice) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E293B),
                        Color(0xFF0F172A)
                    )
                )
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        NetworkScanHeader()

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
                .weight(1f)
                .shadow(8.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)
            )
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
                        color = Color.White
                    )

                    if (scanState.status == RunStatus.RUNNING) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF3B82F6)
                            )
                            Text(
                                "${scanState.progress}%",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
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
private fun NetworkScanHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            Icons.Default.Devices,
            contentDescription = null,
            tint = Color(0xFF8B5CF6),
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Scanner de Rede",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)
        )
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
                        color = Color(0xFF3B82F6),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Dispositivos",
                        value = "$devicesFound",
                        icon = Icons.Default.Devices,
                        color = Color(0xFF10B981),
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
                        color = Color(0xFF3B82F6),
                        trackColor = Color(0xFF334155),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                    Text(
                        "Escaneando rede local...",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
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
                    .height(56.dp)
                    .shadow(12.dp, CircleShape),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (status == RunStatus.RUNNING)
                        Color(0xFFEF4444) else Color(0xFF8B5CF6),
                    contentColor = Color.White
                )
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
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
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
                color = Color.White
            )
        }
    }
}

@Composable
private fun ErrorCard(error: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFEF4444).copy(alpha = 0.2f)
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
                tint = Color(0xFFEF4444)
            )
            Text(
                text = error,
                color = Color(0xFFFECDD3),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun EmptyDevicesState() {
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
                tint = Color(0xFF475569)
            )
            Text(
                "Nenhum dispositivo encontrado",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )
            Text(
                "Clique em \"Iniciar Scan\" para buscar\ndispositivos na rede local",
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ScanningState() {
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
                color = Color(0xFF3B82F6)
            )
            Text(
                "Procurando dispositivos...",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
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
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // IP Address (se hostname existe)
                if (device.hostname != "Unknown") {
                    Text(
                        text = device.ipAddress,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF94A3B8)
                    )
                }

                // Vendor
                if (device.vendor != "Unknown") {
                    Text(
                        text = device.vendor,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }

                // Open Ports
                if (device.openPorts.isNotEmpty()) {
                    Text(
                        text = "Portas: ${device.openPorts.joinToString(", ")}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF475569)
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
                        device.responseTime < 50 -> Color(0xFF10B981)
                        device.responseTime < 100 -> Color(0xFFF59E0B)
                        else -> Color(0xFFEF4444)
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
                            .background(Color(0xFF10B981), CircleShape)
                    )
                    Text(
                        text = "Online",
                        fontSize = 10.sp,
                        color = Color(0xFF10B981)
                    )
                }
            }
        }
    }
}