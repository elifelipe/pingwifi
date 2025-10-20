package com.elftech.pingwifis.data

import com.elftech.pingwifis.data.model.ClientInfo
import com.elftech.pingwifis.data.model.SpeedTestServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class IpInfoService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun getClientInfo(): ClientInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://ip-api.com/json?fields=status,message,country,countryCode,city,query")
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                response.body?.string()?.let { body ->
                    val json = JSONObject(body)

                    if (json.getString("status") == "success") {
                        return@withContext ClientInfo(
                            ipAddress = json.optString("query", "Unknown"),
                            city = json.optString("city", "Unknown"),
                            country = json.optString("countryCode", "Unknown")
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getTestServers(): List<SpeedTestServer> {
        return listOf(
            SpeedTestServer(
                "Cloudflare CDN",
                "Global",
                "Worldwide",
                "https://speed.cloudflare.com/__down?bytes=100000000"
            ),
            SpeedTestServer(
                "Google CDN",
                "US",
                "Global",
                "https://dl.google.com/android/repository/android-ndk-r25c-linux.zip"
            )
        )
    }
}