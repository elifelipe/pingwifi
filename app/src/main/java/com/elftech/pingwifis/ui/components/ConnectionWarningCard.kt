package com.elftech.pingwifis.ui.components

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elftech.pingwifis.data.ConnectionStatus
import com.elftech.pingwifis.data.ConnectionType
import kotlinx.coroutines.delay

/**
 * Card de aviso de conexão que aparece quando não há internet
 * ou quando a conexão está com problemas.
 */
@Composable
fun ConnectionWarningCard(
    connectionStatus: ConnectionStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    showRetryButton: Boolean = true
) {
    AnimatedVisibility(
        visible = !connectionStatus.isConnected,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFEF4444).copy(alpha = 0.15f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Ícone animado
                PulsingIcon()

                // Título
                Text(
                    text = "Sem Conexão à Internet",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEF4444),
                    textAlign = TextAlign.Center
                )

                // Mensagem
                Text(
                    text = getConnectionMessage(connectionStatus.connectionType),
                    fontSize = 14.sp,
                    color = Color(0xFFFECDD3),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                // Botão de retry
                if (showRetryButton) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Tentar Novamente",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Dicas
                ConnectionTips(connectionStatus.connectionType)
            }
        }
    }
}

@Composable
private fun PulsingIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier.size(64.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.WifiOff,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    alpha = alpha
                ),
            tint = Color(0xFFEF4444)
        )
    }
}

@Composable
private fun ConnectionTips(connectionType: ConnectionType) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "💡 Dicas:",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFFECDD3)
        )

        when (connectionType) {
            ConnectionType.WIFI -> {
                TipItem("Verifique se o Wi-Fi está ativado")
                TipItem("Confirme se está conectado à rede correta")
                TipItem("Tente desligar e ligar o Wi-Fi")
            }
            ConnectionType.CELLULAR -> {
                TipItem("Verifique se os dados móveis estão ativados")
                TipItem("Confirme se tem sinal da operadora")
                TipItem("Tente ativar e desativar o modo avião")
            }
            ConnectionType.ETHERNET -> {
                TipItem("Verifique o cabo de rede")
                TipItem("Confirme se o roteador está ligado")
            }
            ConnectionType.NONE -> {
                TipItem("Ative o Wi-Fi ou dados móveis")
                TipItem("Verifique se está no modo avião")
                TipItem("Aproxime-se do roteador Wi-Fi")
            }
        }
    }
}

@Composable
private fun TipItem(text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "•",
            fontSize = 12.sp,
            color = Color(0xFFFCA5A5),
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(
            text = text,
            fontSize = 12.sp,
            color = Color(0xFFFCA5A5),
            lineHeight = 16.sp
        )
    }
}

private fun getConnectionMessage(connectionType: ConnectionType): String {
    return when (connectionType) {
        ConnectionType.WIFI -> "Você está conectado ao Wi-Fi, mas não há acesso à internet. Verifique sua rede."
        ConnectionType.CELLULAR -> "Você está com dados móveis ativos, mas não há acesso à internet. Verifique sua operadora."
        ConnectionType.ETHERNET -> "Você está conectado via cabo, mas não há acesso à internet. Verifique a conexão."
        ConnectionType.NONE -> "Nenhuma rede detectada. Por favor, conecte-se ao Wi-Fi ou ative os dados móveis para usar o app."
    }
}

/**
 * Banner compacto de aviso (para usar no topo das telas)
 */
@Composable
fun ConnectionWarningBanner(
    connectionStatus: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !connectionStatus.isConnected,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFEF4444),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )

                Text(
                    text = "Sem conexão à internet",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )

                var dots by remember { mutableStateOf("") }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(500)
                        dots = when (dots) {
                            "" -> "."
                            "." -> ".."
                            ".." -> "..."
                            else -> ""
                        }
                    }
                }

                Text(
                    text = "Aguardando$dots",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Estado de tela vazia quando não há conexão
 * (para usar como estado vazio das telas)
 */
@Composable
fun NoConnectionState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    connectionType: ConnectionType = ConnectionType.NONE
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E293B)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // Ícone grande
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = Color(0xFF475569)
                )
            }

            // Título
            Text(
                text = "Sem Conexão",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            // Mensagem
            Text(
                text = getConnectionMessage(connectionType),
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            // Botão de retry
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .width(200.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3B82F6),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Tentar Novamente",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}