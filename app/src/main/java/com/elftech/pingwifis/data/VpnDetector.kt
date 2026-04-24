package com.elftech.pingwifis.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.elftech.pingwifis.data.model.VpnInfo
import java.net.NetworkInterface

object VpnDetector {
    fun detect(context: Context): VpnInfo {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)

        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
            return VpnInfo(true, "VPN")
        }

        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { iface ->
                val name = iface.name.lowercase()
                if (iface.isUp && (name.startsWith("tun") || name.startsWith("ppp") ||
                            name.startsWith("tap") || name.startsWith("ipsec"))
                ) {
                    return VpnInfo(true, iface.name)
                }
            }
        } catch (_: Exception) {}

        return VpnInfo(false)
    }
}
