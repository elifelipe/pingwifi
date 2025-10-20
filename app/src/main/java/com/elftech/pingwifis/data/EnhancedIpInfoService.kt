package com.elftech.pingwifis.data

import com.elftech.pingwifis.data.model.ClientInfo
import com.elftech.pingwifis.data.model.SpeedTestServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class EnhancedIpInfoService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
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

    suspend fun getNearbyServers(clientInfo: ClientInfo): List<SpeedTestServer> = withContext(Dispatchers.IO) {
        try {
            val allServers = when (clientInfo.country) {
                "BR" -> getBrazilianServers()
                else -> getGlobalServers()
            }

            // Valida cada servidor antes de retornar
            val validServers = allServers.filter { server ->
                isServerReachable(server)
            }

            if (validServers.isEmpty()) {
                // Fallback para servidores globais confiáveis
                return@withContext getFallbackServers()
            }

            validServers
        } catch (e: Exception) {
            e.printStackTrace()
            getFallbackServers()
        }
    }

    private fun getBrazilianServers(): List<SpeedTestServer> {
        return listOf(
            // Servidores públicos de CDN que funcionam no Brasil
            SpeedTestServer(
                name = "Cloudflare Brasil",
                country = "BR",
                city = "São Paulo",
                downloadUrl = "https://speed.cloudflare.com/__down?bytes=50000000"
            ),
            SpeedTestServer(
                name = "Google Brasil",
                country = "BR",
                city = "São Paulo",
                downloadUrl = "https://dl.google.com/dl/android/studio/ide-zips/2022.1.1.21/android-studio-2022.1.1.21-linux.tar.gz"
            ),
            // Arquivo público da Microsoft
            SpeedTestServer(
                name = "Microsoft CDN Brasil",
                country = "BR",
                city = "São Paulo",
                downloadUrl = "https://download.visualstudio.microsoft.com/download/pr/8b3f5d6c-9b9b-4e1b-8c3b-1a3d3e3e3e3e/vs_community.exe"
            ),
            // Ubuntu Brasil (espelho confiável)
            SpeedTestServer(
                name = "Ubuntu BR",
                country = "BR",
                city = "São Paulo",
                downloadUrl = "http://br.archive.ubuntu.com/ubuntu/dists/jammy/main/installer-amd64/current/legacy-images/netboot/mini.iso"
            ),
            // Arquivo de teste da RNP (Rede Nacional de Ensino e Pesquisa)
            SpeedTestServer(
                name = "RNP Brasil",
                country = "BR",
                city = "Rio de Janeiro",
                downloadUrl = "http://ftp.rnp.br/debian-cd/current/amd64/iso-cd/debian-12.0.0-amd64-netinst.iso"
            )
        )
    }

    private fun getGlobalServers(): List<SpeedTestServer> {
        return listOf(
            SpeedTestServer(
                name = "Cloudflare Global",
                country = "Global",
                city = "Worldwide",
                downloadUrl = "https://speed.cloudflare.com/__down?bytes=50000000"
            ),
            SpeedTestServer(
                name = "Google CDN",
                country = "US",
                city = "Global",
                downloadUrl = "https://dl.google.com/android/repository/android-ndk-r25c-linux.zip"
            ),
            SpeedTestServer(
                name = "Microsoft Azure",
                country = "US",
                city = "Global",
                downloadUrl = "https://az792536.vo.msecnd.net/vms/VMBuild_20190311/VirtualBox/MSEdge/MSEdge.Win10.VirtualBox.zip"
            )
        )
    }

    private fun getFallbackServers(): List<SpeedTestServer> {
        return listOf(
            SpeedTestServer(
                name = "Cloudflare CDN",
                country = "Global",
                city = "Worldwide",
                downloadUrl = "https://speed.cloudflare.com/__down?bytes=50000000"
            ),
            SpeedTestServer(
                name = "Fast.com Netflix",
                country = "Global",
                city = "Worldwide",
                downloadUrl = "https://api.fast.com/netflix/speedtest/v2"
            )
        )
    }

    /**
     * Valida se um servidor está acessível
     */
    private suspend fun isServerReachable(server: SpeedTestServer): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(server.downloadUrl)
                .head() // Usa HEAD para não baixar conteúdo
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 302 || response.code == 301
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Testa a latência de um servidor específico usando ICMP
     */
    suspend fun pingServer(server: SpeedTestServer): Int = withContext(Dispatchers.IO) {
        try {
            val host = server.downloadUrl
                .substringAfter("://")
                .substringBefore("/")

            val startTime = System.currentTimeMillis()
            val address = InetAddress.getByName(host)

            // Tenta 3 vezes e pega a média
            val pings = mutableListOf<Long>()
            repeat(3) {
                val pingStart = System.currentTimeMillis()
                if (address.isReachable(3000)) {
                    pings.add(System.currentTimeMillis() - pingStart)
                }
            }

            if (pings.isEmpty()) return@withContext 9999

            pings.average().toInt()
        } catch (e: Exception) {
            e.printStackTrace()
            9999
        }
    }
}