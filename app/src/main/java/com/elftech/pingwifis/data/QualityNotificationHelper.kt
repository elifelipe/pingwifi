package com.elftech.pingwifis.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat

class QualityNotificationHelper(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "wifi_quality_alerts"
        private const val NOTIF_ID = 2001
    }

    init { createChannel() }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alertas de Qualidade Wi-Fi",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Alerta quando a velocidade cai abaixo do limite configurado" }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    fun showLowSpeedAlert(downloadMbps: Double, thresholdMbps: Float) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Wi-Fi abaixo do esperado")
            .setContentText(
                "Download: ${"%.1f".format(downloadMbps)} Mbps " +
                        "(limite: ${"%.0f".format(thresholdMbps)} Mbps)"
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, notif)
    }
}
