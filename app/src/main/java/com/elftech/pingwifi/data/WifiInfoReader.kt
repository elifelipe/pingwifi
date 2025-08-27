package com.elftech.pingwifi.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.elftech.pingwifi.model.WifiInfoData

object WifiInfoReader {

    private fun isLocationEnabled(context: Context): Boolean {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                lm.isLocationEnabled
            } else {
                android.provider.Settings.Secure.getInt(
                    context.contentResolver,
                    android.provider.Settings.Secure.LOCATION_MODE,
                    android.provider.Settings.Secure.LOCATION_MODE_OFF
                ) != android.provider.Settings.Secure.LOCATION_MODE_OFF
            }
        } catch (_: Exception) { false }
    }

    fun read(context: Context): WifiInfoData {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val nc = cm.getNetworkCapabilities(cm.activeNetwork)
            val isWifi = nc?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            if (!isWifi) return WifiInfoData(false, "Não conectado ao Wi-Fi", null, null, null, null)

            // 1) Permissão
            val fineGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!fineGranted) {
                return WifiInfoData(true, "Permissão de localização necessária", null, null, null, null)
            }

            // 2) Localização do aparelho habilitada
            if (!isLocationEnabled(context)) {
                return WifiInfoData(true, "Ative a Localização do dispositivo para ler o SSID", null, null, null, null)
            }

            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo

            WifiInfoData(
                isWifi = true,
                ssid = info?.ssid?.removePrefix("\"")?.removeSuffix("\""),
                bssid = info?.bssid,
                linkSpeedMbps = info?.linkSpeed,
                rssiDbm = info?.rssi,
                frequencyMhz = if (Build.VERSION.SDK_INT >= 21) info?.frequency else null
            )
        } catch (e: SecurityException) {
            WifiInfoData(true, "Permissão negada", null, null, null, null)
        } catch (_: Exception) {
            WifiInfoData(false, "Erro ao ler dados", null, null, null, null)
        }
    }
}
