package com.elftech.pingwifis.data

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import com.elftech.pingwifis.data.model.RunStatus
import com.elftech.pingwifis.data.model.WifiChannelData
import com.elftech.pingwifis.data.model.WifiChannelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WifiChannelAnalyzer(
    private val context: Context,
    private val scope: CoroutineScope
) {
    fun analyze(
        currentFrequencyMhz: Int?,
        onComplete: (WifiChannelState) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val wifiManager = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as WifiManager

                @Suppress("DEPRECATION")
                val scanResults = wifiManager.scanResults

                if (scanResults.isNullOrEmpty()) {
                    onComplete(WifiChannelState(status = RunStatus.DONE, channels = emptyList()))
                    return@launch
                }

                val currentChannel = currentFrequencyMhz?.let { freqToChannel(it) } ?: 0

                val channels = scanResults
                    .groupBy { freqToChannel(it.frequency) }
                    .filter { it.key > 0 }
                    .map { (ch, networks) ->
                        WifiChannelData(
                            channel = ch,
                            frequencyMhz = networks.first().frequency,
                            band = if (networks.first().frequency < 3000) "2.4 GHz" else "5 GHz",
                            networkSsids = networks.map { ap ->
                                @Suppress("DEPRECATION")
                                val ssid = if (Build.VERSION.SDK_INT >= 33)
                                    ap.wifiSsid?.toString()?.removeSurrounding("\"") ?: "Oculta"
                                else
                                    ap.SSID.ifBlank { "Oculta" }
                                ssid
                            }.distinct(),
                            currentChannel = ch == currentChannel
                        )
                    }
                    .sortedWith(compareBy({ it.band }, { it.channel }))

                onComplete(WifiChannelState(status = RunStatus.DONE, channels = channels))
            } catch (e: Exception) {
                onError("Erro ao analisar canais: ${e.message}")
            }
        }
    }

    fun freqToChannel(freqMhz: Int): Int = when {
        freqMhz <= 0 -> 0
        freqMhz == 2484 -> 14
        freqMhz in 2412..2483 -> (freqMhz - 2407) / 5
        freqMhz in 5160..5885 -> (freqMhz - 5000) / 5
        freqMhz in 5925..7125 -> (freqMhz - 5950) / 5 + 1
        else -> 0
    }
}
