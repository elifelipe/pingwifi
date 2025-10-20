package com.elftech.pingwifis.data

import com.elftech.pingwifis.data.model.ClientInfo
import com.elftech.pingwifis.data.model.SpeedTestServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class EnhancedIpInfoService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            val originalRequest = chain.request()
            val requestWithUserAgent = originalRequest.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36")
                .build()
            chain.proceed(requestWithUserAgent)
        }
        .build()

    suspend fun getClientInfo(): ClientInfo? = withContext(Dispatchers.IO) {
        // Esta função continua sendo um bom fallback, mas a fonte primária agora é a config da Ookla.
        try {
            val request = Request.Builder()
                .url("http://ip-api.com/json?fields=status,message,countryCode,city,lat,lon,isp,query")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()?.let { body ->
                    val json = JSONObject(body)
                    if (json.getString("status") == "success") {
                        return@withContext ClientInfo(
                            ipAddress = json.optString("query", "Unknown"),
                            city = json.optString("city", "Unknown"),
                            country = json.optString("countryCode", "Unknown"),
                            isp = json.optString("isp", "Unknown"),
                            lat = json.optDouble("lat", 0.0),
                            lon = json.optDouble("lon", 0.0)
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
        // Etapa 1: Tentar a estratégia profissional primeiro.
        val ooklaRecommendedServers = fetchOoklaConfigAndServers()

        // Etapa 2: Buscar a lista de CDNs curados em paralelo.
        val curatedCdnServersDeferred = async { getCuratedCdnServers() }
        val reachableCdnServers = try { curatedCdnServersDeferred.await() } catch (e: Exception) { emptyList() }

        // Etapa 3: Combinar os resultados.
        val finalServers = (ooklaRecommendedServers + reachableCdnServers).distinctBy { it.downloadUrl }

        // Etapa 4: Lógica de Fallback Definitiva.
        // Se a estratégia profissional falhar e não retornar servidores, usamos nosso fallback robusto.
        if (ooklaRecommendedServers.isEmpty()) {
            val curatedLocalIspFallback = getCuratedLocalIspServers("BR")
            val fallbackCandidates = curatedLocalIspFallback.filter { isServerReachable(it) }
            val top3Fallback = fallbackCandidates
                .sortedBy { server -> haversineDistance(clientInfo.lat, clientInfo.lon, server.lat, server.lon) }
                .take(3)
            return@withContext (top3Fallback + reachableCdnServers).distinctBy { it.downloadUrl }
        }

        return@withContext finalServers
    }

    // MÉTODO PROFISSIONAL: Emula a lógica do Speedtest.net
    private suspend fun fetchOoklaConfigAndServers(): List<SpeedTestServer> {
        return try {
            // 1. Obter a configuração do cliente, que informa o IP e o ISP.
            val configRequest = Request.Builder().url("https://www.speedtest.net/speedtest-config.php").build()
            val configResponse = client.newCall(configRequest).execute()
            if (!configResponse.isSuccessful) return emptyList()

            // 2. Com a configuração, solicitar a lista de servidores RECOMENDADOS.
            val serversRequest = Request.Builder().url("https://www.speedtest.net/speedtest-servers-php.php?threads=4").build()
            val serversResponse = client.newCall(serversRequest).execute()
            if (!serversResponse.isSuccessful) return emptyList()

            val xmlBody = serversResponse.body?.string() ?: return emptyList()
            parseSpeedtestServersXml(xmlBody).take(5) // Pega os 5 melhores servidores recomendados.
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseSpeedtestServersXml(xml: String): List<SpeedTestServer> {
        val servers = mutableListOf<SpeedTestServer>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "server") {
                val url = parser.getAttributeValue(null, "url")
                servers.add(
                    SpeedTestServer(
                        name = parser.getAttributeValue(null, "sponsor"),
                        country = parser.getAttributeValue(null, "country"),
                        city = parser.getAttributeValue(null, "name"),
                        downloadUrl = url.replace("upload.php", "random4000x4000.jpg"),
                        uploadUrl = url,
                        lat = parser.getAttributeValue(null, "lat").toDoubleOrNull() ?: 0.0,
                        lon = parser.getAttributeValue(null, "lon").toDoubleOrNull() ?: 0.0
                    )
                )
            }
            eventType = parser.next()
        }
        return servers
    }

    private fun getCuratedLocalIspServers(countryCode: String): List<SpeedTestServer> {
        if (countryCode != "BR") return emptyList()
        return listOf(
            SpeedTestServer("Vivo (SP)", "BR", "Sao Paulo", "http://speedtest.vivo.com.br.prod.hosts.ooklaserver.net:8080/download?size=25000000", "http://speedtest.vivo.com.br.prod.hosts.ooklaserver.net:8080/upload.php", -23.55, -46.63),
            SpeedTestServer("Claro (SP)", "BR", "Sao Paulo", "http://spd-tst-sao.claro.com.br.prod.hosts.ooklaserver.net:8080/download?size=25000000", "http://spd-tst-sao.claro.com.br.prod.hosts.ooklaserver.net:8080/upload.php", -23.55, -46.63),
            SpeedTestServer("TIM (SP)", "BR", "Sao Paulo", "http://speedtest.tim.com.br.prod.hosts.ooklaserver.net:8080/download?size=25000000", "http://speedtest.tim.com.br.prod.hosts.ooklaserver.net:8080/upload.php", -23.55, -46.63),
            SpeedTestServer("Unifique (SC)", "BR", "Timbó", "http://speedtest.unifique.com.br.prod.hosts.ooklaserver.net:8080/download?size=25000000", "http://speedtest.unifique.com.br.prod.hosts.ooklaserver.net:8080/upload.php", -26.82, -49.27),
            SpeedTestServer("Claro (SC)", "BR", "Florianopolis", "http://sc.speedtest.claro.com.br.prod.hosts.ooklaserver.net:8080/download?size=25000000", "http://sc.speedtest.claro.com.br.prod.hosts.ooklaserver.net:8080/upload.php", -27.59, -48.54),
            SpeedTestServer("Oi Fibra (SC)", "BR", "Florianopolis", "http://speedtest.oi.com.br.prod.hosts.ooklaserver.net:8080/download?size=25000000", "http://speedtest.oi.com.br.prod.hosts.ooklaserver.net:8080/upload.php", -27.59, -48.54)
        )
    }

    private suspend fun getCuratedCdnServers(): List<SpeedTestServer> = withContext(Dispatchers.IO) {
        val fastComServer = fetchFastComServer()
        val genericUploadUrl = "http://speedtest.vivo.com.br.prod.hosts.ooklaserver.net:8080/upload.php"

        val cdnList = mutableListOf(
            SpeedTestServer("Cloudflare CDN", "Global", "Worldwide", "https://speed.cloudflare.com/__down?bytes=100000000", genericUploadUrl),
            SpeedTestServer("Google CDN", "Global", "Worldwide", "https://storage.googleapis.com/gweb-cloud-storage-geo-testing/1GB.bin", genericUploadUrl)
        )

        fastComServer?.let { cdnList.add(it) }
        return@withContext cdnList.filter { isServerReachable(it) }
    }

    private suspend fun fetchFastComServer(): SpeedTestServer? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.fast.com/netflix/speedtest/v2?https=true&token=YXNkZmFzZGxmai1odG1sNS1wYWdl&urlCount=5")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val json = JSONObject(response.body!!.string())
            val targets = json.getJSONArray("targets")
            if (targets.length() > 0) {
                val url = targets.getJSONObject(0).getString("url")
                return@withContext SpeedTestServer("Netflix (Fast.com)", "Global", "Worldwide", url, url)
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun isServerReachable(server: SpeedTestServer): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(server.downloadUrl).get().build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    suspend fun pingServer(server: SpeedTestServer): Int = withContext(Dispatchers.IO) {
        try {
            val urlForPing = if ("cloudflare" in server.downloadUrl) {
                server.downloadUrl.replaceAfter("bytes=", "0")
            } else {
                server.downloadUrl
            }

            val request = Request.Builder().url(urlForPing).get().build()
            val pings = mutableListOf<Long>()
            repeat(3) {
                val start = System.currentTimeMillis()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) pings.add(System.currentTimeMillis() - start)
                }
            }
            if (pings.isEmpty()) 9999 else pings.average().toInt()
        } catch (e: Exception) { 9999 }
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}

