package com.elftech.pingwifis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elftech.pingwifis.data.model.DownPingState
import com.elftech.pingwifis.data.model.HttpCheckResult
import com.elftech.pingwifis.data.model.RunStatus

@Composable
fun DownPingDiagnosisScreen(
    state: DownPingState,
    onTargetChange: (String) -> Unit,
    onStart: (String, Int) -> Unit,
    onStop: () -> Unit
) {
    var pingCountText by remember { mutableStateOf("6") }
    val running = state.status == RunStatus.RUNNING

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Diagnóstico de Rede",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.target,
                onValueChange = onTargetChange,
                label = { Text("IP ou domínio") },
                singleLine = true,
                enabled = !running,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = pingCountText,
                onValueChange = { value ->
                    if (value.all(Char::isDigit)) pingCountText = value
                },
                label = { Text("Quantidade de ping (3-20)") },
                singleLine = true,
                enabled = !running,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (running) {
                        onStop()
                    } else {
                        onStart(state.target, pingCountText.toIntOrNull() ?: 6)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (running) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                if (running) {
                    androidx.compose.material3.Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Parar diagnóstico")
                } else {
                    androidx.compose.material3.Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Executar diagnóstico")
                }
            }
        }

        item {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (running) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                    }
                    Text(state.progressMessage)
                }
            }
        }

        state.error?.let { errorMsg ->
            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Erro: $errorMsg",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        state.report?.let { report ->
            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Destino: ${report.normalizedHost}", fontWeight = FontWeight.SemiBold)
                        Text("IPs: ${report.resolvedIps.joinToString()}")
                        Text("Pacotes perdidos: ${report.pingSummary.packetLossPercent}%")
                        Text("Latência média: ${report.pingSummary.avgLatencyMs} ms")
                    }
                }
            }

            item {
                Text("HTTP/HTTPS", style = MaterialTheme.typography.titleMedium)
            }
            items(report.httpChecks) { check ->
                HttpCheckRow(check)
            }

            item {
                Text("Portas TCP", style = MaterialTheme.typography.titleMedium)
            }
            items(report.tcpPortResults.entries.toList()) { entry ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Porta ${entry.key}: ${if (entry.value) "aberta" else "fechada/sem resposta"}",
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        if (state.pingResults.isNotEmpty()) {
            item {
                Text("Eventos do ping", style = MaterialTheme.typography.titleMedium)
            }
            items(state.pingResults.takeLast(30)) { result ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = result.message,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HttpCheckRow(check: HttpCheckResult) {
    val color = if (check.ok) Color(0xFF1B5E20) else MaterialTheme.colorScheme.error
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(check.url, fontWeight = FontWeight.SemiBold)
            Text("Status: ${check.statusCode?.toString() ?: "-"}", color = color)
            Text("Tempo: ${check.latencyMs} ms")
            if (check.message.isNotBlank()) {
                Text(check.message)
            }
        }
    }
}
