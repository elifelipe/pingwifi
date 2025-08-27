package com.elftech.pingwifi.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

// Data class para guardar as informações do cliente
data class ClientInfo(
    val ipAddress: String,
    val city: String,
    val country: String
)

class IpInfoService {
    private val client = OkHttpClient()

    /**
     * Busca informações do cliente (IP público, cidade, país) usando uma API externa.
     * Retorna null em caso de falha.
     */
    suspend fun getClientInfo(): ClientInfo? {
        val request = Request.Builder()
            .url("http://ip-api.com/json") // API simples e gratuita
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val json = JSONObject(responseBody)
                ClientInfo(
                    ipAddress = json.getString("query"),
                    city = json.getString("city"),
                    country = json.getString("countryCode") // ex: "BR"
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}