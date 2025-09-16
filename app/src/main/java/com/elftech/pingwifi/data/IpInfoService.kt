package com.elftech.pingwifi.data

import com.elftech.pingwifi.data.model.ClientInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class IpInfoService {
    // Cliente HTTP configurado corretamente
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Busca informações do cliente (IP público, cidade, país) usando uma API externa.
     * Retorna null em caso de falha.
     */
    suspend fun getClientInfo(): ClientInfo? = withContext(Dispatchers.IO) {
        try {
            // Usando HTTP ao invés de HTTPS para evitar problemas de certificado
            // A API ip-api.com oferece serviço gratuito via HTTP
            val request = Request.Builder()
                .url("http://ip-api.com/json")
                .addHeader("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                response.body?.string()?.let { responseBody ->
                    try {
                        val json = JSONObject(responseBody)

                        // Verifica se a resposta teve sucesso
                        if (json.optString("status") == "success") {
                            return@withContext ClientInfo(
                                ipAddress = json.optString("query", "Unknown"),
                                city = json.optString("city", "Unknown"),
                                country = json.optString("countryCode", "Unknown")
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Alternativa: Usa ipinfo.io (requer token para muitas requisições)
     */
    suspend fun getClientInfoAlternative(): ClientInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://ipinfo.io/json")
                .addHeader("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                response.body?.string()?.let { responseBody ->
                    try {
                        val json = JSONObject(responseBody)

                        val locationParts = json.optString("loc", "").split(",")
                        val cityCountry = json.optString("city", "Unknown") +
                                ", " + json.optString("country", "Unknown")

                        return@withContext ClientInfo(
                            ipAddress = json.optString("ip", "Unknown"),
                            city = json.optString("city", "Unknown"),
                            country = json.optString("country", "Unknown")
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}