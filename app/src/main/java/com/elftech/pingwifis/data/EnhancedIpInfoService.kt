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
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class EnhancedIpInfoService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            val requestWithUserAgent = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36")
                .build()
            chain.proceed(requestWithUserAgent)
        }
        .build()

    private val probeClient = client.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    private data class GeoProviderInfo(
        val ip: String,
        val city: String,
        val country: String,
        val isp: String,
        val lat: Double,
        val lon: Double
    )

    companion object {
        private const val UNREACHABLE_LATENCY = 9_999
        private const val OOKLA_NEARBY_LIMIT = 120

        private val CDN_KEYWORDS = listOf(
            "cdn", "cloudflare", "akamai", "fast.com", "netflix", "google", "gstatic", "edgesuite"
        )
    }

    suspend fun getClientInfo(): ClientInfo? = withContext(Dispatchers.IO) {
        val ooklaInfo = fetchOoklaClientInfo()
        val geoInfo = fetchGeoProviderInfo()

        when {
            ooklaInfo != null && geoInfo != null -> {
                val finalCity = geoInfo.city.takeUnless { it.equals("Unknown", ignoreCase = true) } ?: ooklaInfo.city
                val finalCountry = if (ooklaInfo.country.equals("Unknown", ignoreCase = true)) geoInfo.country else ooklaInfo.country
                ClientInfo(
                    ipAddress = ooklaInfo.ipAddress.takeUnless { it.equals("Unknown", ignoreCase = true) } ?: geoInfo.ip,
                    city = finalCity,
                    country = finalCountry,
                    isp = ooklaInfo.isp.takeUnless { it.equals("Unknown", ignoreCase = true) } ?: geoInfo.isp,
                    lat = if (ooklaInfo.lat != 0.0) ooklaInfo.lat else geoInfo.lat,
                    lon = if (ooklaInfo.lon != 0.0) ooklaInfo.lon else geoInfo.lon
                )
            }

            ooklaInfo != null -> ooklaInfo

            geoInfo != null -> ClientInfo(
                ipAddress = geoInfo.ip,
                city = geoInfo.city,
                country = geoInfo.country,
                isp = geoInfo.isp,
                lat = geoInfo.lat,
                lon = geoInfo.lon
            )

            else -> null
        }
    }

    suspend fun getNearbyServers(
        clientInfo: ClientInfo,
        includeNearby: Boolean = true,
        includeCdn: Boolean = true
    ): List<SpeedTestServer> = withContext(Dispatchers.IO) {
        val nearby = if (includeNearby) {
            try {
                fetchOoklaFullServerList(clientInfo)
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        val uniqueNearby = nearby
            .filter { it.downloadUrl.isNotBlank() }
            .distinctBy { it.uploadUrl ?: it.downloadUrl }
            .sortedBy { haversineDistance(clientInfo.lat, clientInfo.lon, it.lat, it.lon) }

        if (uniqueNearby.isNotEmpty()) {
            val probeTargets = uniqueNearby.take(15)
            val latencyByKey = probeTargets.map { server ->
                async { (server.uploadUrl ?: server.downloadUrl) to pingServer(server) }
            }.map { it.await() }.toMap()

            return@withContext uniqueNearby.sortedWith(
                compareBy<SpeedTestServer> { latencyByKey[it.uploadUrl ?: it.downloadUrl] ?: Int.MAX_VALUE }
                    .thenBy { haversineDistance(clientInfo.lat, clientInfo.lon, it.lat, it.lon) }
            )
        }

        if (!includeCdn) return@withContext emptyList()

        val cdn = try {
            getCuratedCdnServers()
        } catch (_: Exception) {
            emptyList()
        }

        val cdnWithLatency = cdn.map { server ->
            async { server to pingServer(server) }
        }.map { it.await() }

        cdnWithLatency
            .sortedBy { it.second }
            .map { it.first }
    }

    fun isCdnServer(server: SpeedTestServer): Boolean {
        val marker = "${server.name} ${server.downloadUrl} ${server.uploadUrl.orEmpty()} ${server.country}".lowercase()
        val hasCdnKeyword = CDN_KEYWORDS.any { marker.contains(it) }
        val hasOoklaUploadPattern = server.uploadUrl?.contains("upload.php", ignoreCase = true) == true
        val hasGlobalMarker = server.country.equals("global", ignoreCase = true) ||
            server.city.equals("worldwide", ignoreCase = true)
        return hasCdnKeyword || (hasGlobalMarker && !hasOoklaUploadPattern)
    }

    private suspend fun fetchOoklaClientInfo(): ClientInfo? = withContext(Dispatchers.IO) {
        try {
            val configRequest = Request.Builder().url("https://www.speedtest.net/speedtest-config.php").build()
            val configResponse = client.newCall(configRequest).execute()
            if (!configResponse.isSuccessful) return@withContext null

            val xmlBody = configResponse.body?.string() ?: return@withContext null
            parseClientInfoFromConfig(xmlBody).first
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchGeoProviderInfo(): GeoProviderInfo? = withContext(Dispatchers.IO) {
        fetchFromIpWhoIs() ?: fetchFromIpApiCo()
    }

    private suspend fun fetchFromIpWhoIs(): GeoProviderInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("https://ipwho.is/").build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) return@withContext null

            val connection = json.optJSONObject("connection")
            GeoProviderInfo(
                ip = json.optString("ip", "Unknown"),
                city = json.optString("city", "Unknown"),
                country = json.optString("country_code", json.optString("country", "Unknown")),
                isp = connection?.optString("isp", "Unknown") ?: "Unknown",
                lat = json.optDouble("latitude", 0.0),
                lon = json.optDouble("longitude", 0.0)
            )
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchFromIpApiCo(): GeoProviderInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("https://ipapi.co/json/").build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            if (json.has("error") && json.optBoolean("error", false)) return@withContext null

            GeoProviderInfo(
                ip = json.optString("ip", "Unknown"),
                city = json.optString("city", "Unknown"),
                country = json.optString("country_code", "Unknown"),
                isp = json.optString("org", "Unknown"),
                lat = json.optDouble("latitude", 0.0),
                lon = json.optDouble("longitude", 0.0)
            )
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchOoklaFullServerList(clientInfo: ClientInfo): List<SpeedTestServer> {
        val candidateUrls = listOf(
            "https://www.speedtest.net/speedtest-servers-static.php",
            "https://www.speedtest.net/speedtest-servers.php",
            "https://www.speedtest.net/speedtest-servers.php?lat=${clientInfo.lat}&lon=${clientInfo.lon}",
            "https://www.speedtest.net/api/js/servers?engine=js&https_functional=true&limit=1000",
            "https://www.speedtest.net/api/js/servers?engine=js&search=&https_functional=true&limit=1000"
        )

        val collected = mutableListOf<SpeedTestServer>()

        for (url in candidateUrls) {
            try {
                val response = client.newCall(
                    Request.Builder().url(url).build()
                ).execute()
                if (!response.isSuccessful) continue

                val body = response.body?.string().orEmpty()
                if (body.isBlank()) continue

                val parsed = when {
                    body.trimStart().startsWith("<") -> parseSpeedtestServersXml(body)
                    body.trimStart().startsWith("[") -> parseSpeedtestServersJson(body)
                    body.trimStart().startsWith("{") -> parseSpeedtestServersJson(body)
                    else -> emptyList()
                }

                if (parsed.isNotEmpty()) {
                    collected.addAll(parsed)
                }
            } catch (_: Exception) {
                // tenta próxima fonte
            }
        }

        return collected
            .distinctBy { it.uploadUrl ?: it.downloadUrl }
            .sortedBy { haversineDistance(clientInfo.lat, clientInfo.lon, it.lat, it.lon) }
            .take(OOKLA_NEARBY_LIMIT)
    }

    private fun parseClientInfoFromConfig(xml: String): Pair<ClientInfo?, Map<String, String>> {
        var clientInfo: ClientInfo? = null
        val configMap = mutableMapOf<String, String>()

        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "client") {
                    clientInfo = ClientInfo(
                        ipAddress = parser.getAttributeValue(null, "ip") ?: "Unknown",
                        city = "Unknown",
                        country = parser.getAttributeValue(null, "country") ?: "Unknown",
                        isp = parser.getAttributeValue(null, "isp") ?: "Unknown",
                        lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0,
                        lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                    )
                } else if (eventType == XmlPullParser.START_TAG && parser.name == "server-config") {
                    configMap["threads"] = parser.getAttributeValue(null, "threads") ?: "4"
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            // fallback silencioso
        }

        return Pair(clientInfo, configMap)
    }

    private fun parseSpeedtestServersXml(xml: String): List<SpeedTestServer> {
        val servers = mutableListOf<SpeedTestServer>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "server") {
                    val uploadUrl = parser.getAttributeValue(null, "url") ?: ""
                    if (uploadUrl.isNotBlank()) {
                        servers.add(
                            SpeedTestServer(
                                name = parser.getAttributeValue(null, "sponsor") ?: "Ookla Server",
                                country = parser.getAttributeValue(null, "country") ?: "Unknown",
                                city = parser.getAttributeValue(null, "name") ?: "Unknown",
                                downloadUrl = uploadUrl.replace("upload.php", "random4000x4000.jpg"),
                                uploadUrl = uploadUrl,
                                lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0,
                                lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                            )
                        )
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            // fallback silencioso
        }
        return servers
    }

    private fun parseSpeedtestServersJson(rawJson: String): List<SpeedTestServer> {
        return try {
            val array = when {
                rawJson.trimStart().startsWith("[") -> org.json.JSONArray(rawJson)
                rawJson.trimStart().startsWith("{") -> {
                    val obj = JSONObject(rawJson)
                    obj.optJSONArray("servers") ?: obj.optJSONArray("data") ?: org.json.JSONArray()
                }
                else -> org.json.JSONArray()
            }
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val explicitUrl = item.optString("url", "").trim()
                    val host = item.optString("host", "").trim()
                    if (explicitUrl.isBlank() && host.isBlank()) continue

                    val uploadUrl = when {
                        explicitUrl.isNotBlank() -> explicitUrl
                        host.startsWith("http") -> if (host.endsWith("/")) "${host}upload.php" else "${host}/upload.php"
                        else -> "https://${if (host.endsWith("/")) "${host}upload.php" else "${host}/upload.php"}"
                    }
                    val downloadUrl = uploadUrl.replace("upload.php", "random4000x4000.jpg")
                    val lat = item.optDouble("lat", item.optDouble("latitude", Double.NaN))
                    val lon = item.optDouble("lon", item.optDouble("longitude", Double.NaN))

                    add(
                        SpeedTestServer(
                            name = item.optString("sponsor", "Ookla Server"),
                            country = item.optString("country", "Unknown"),
                            city = item.optString("name", "Unknown"),
                            downloadUrl = downloadUrl,
                            uploadUrl = uploadUrl,
                            lat = if (lat.isNaN()) 0.0 else lat,
                            lon = if (lon.isNaN()) 0.0 else lon
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun getCuratedCdnServers(): List<SpeedTestServer> = withContext(Dispatchers.IO) {
        val genericUploadUrl = "https://speed.cloudflare.com/__up"
        val cdnList = mutableListOf(
            SpeedTestServer(
                "Cloudflare CDN",
                "Global",
                "Worldwide",
                "https://speed.cloudflare.com/__down?bytes=100000000",
                "https://speed.cloudflare.com/__up",
                0.0,
                0.0
            ),
            SpeedTestServer(
                "Google CDN",
                "Global",
                "Worldwide",
                "https://storage.googleapis.com/gweb-cloud-storage-geo-testing/1GB.bin",
                genericUploadUrl,
                0.0,
                0.0
            ),
            SpeedTestServer(
                "Akamai CDN",
                "Global",
                "Worldwide",
                "https://speedtest.akamaized.net/static/images/background.jpg",
                genericUploadUrl,
                0.0,
                0.0
            )
        )

        fetchFastComServer()?.let { cdnList.add(it) }
        cdnList
    }

    private suspend fun fetchFastComServer(): SpeedTestServer? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.fast.com/netflix/speedtest/v2?https=true&token=YXNkZmFzZGxmai1odG1sNS1wYWdl&urlCount=5")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val json = JSONObject(response.body?.string().orEmpty())
            val targets = json.optJSONArray("targets") ?: return@withContext null
            if (targets.length() <= 0) return@withContext null

            val url = targets.getJSONObject(0).optString("url", "")
            if (url.isBlank()) return@withContext null

            SpeedTestServer(
                name = "Netflix (Fast.com)",
                country = "Global",
                city = "Worldwide",
                downloadUrl = url,
                uploadUrl = "https://speed.cloudflare.com/__up",
                lat = 0.0,
                lon = 0.0
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun pingServer(server: SpeedTestServer): Int = withContext(Dispatchers.IO) {
        val endpoint = resolvePingEndpoint(server)
        val latencies = mutableListOf<Int>()

        repeat(4) {
            val headAttempt = timedProbe(endpoint, useHead = true)
            if (headAttempt != null) {
                latencies.add(headAttempt)
            } else {
                val getAttempt = timedProbe(endpoint, useHead = false)
                if (getAttempt != null) {
                    latencies.add(getAttempt)
                }
            }
        }

        if (latencies.isEmpty()) UNREACHABLE_LATENCY else median(latencies)
    }

    private fun resolvePingEndpoint(server: SpeedTestServer): String {
        if (server.downloadUrl.contains("cloudflare", ignoreCase = true) && server.downloadUrl.contains("bytes=")) {
            return server.downloadUrl.replaceAfter("bytes=", "0")
        }

        if (server.uploadUrl?.contains("upload.php", ignoreCase = true) == true) {
            return server.uploadUrl
        }

        return server.downloadUrl
    }

    private fun timedProbe(url: String, useHead: Boolean): Int? {
        return try {
            val builder = Request.Builder()
                .url(url)
                .header("Cache-Control", "no-cache")

            val request = if (useHead) {
                builder.head().build()
            } else {
                builder.header("Range", "bytes=0-0").get().build()
            }

            val startNs = System.nanoTime()
            probeClient.newCall(request).execute().use { response ->
                if (response.code in 100..599) {
                    ((System.nanoTime() - startNs) / 1_000_000.0).roundToInt().coerceAtLeast(1)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun median(values: List<Int>): Int {
        if (values.isEmpty()) return UNREACHABLE_LATENCY
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            ((sorted[middle - 1] + sorted[middle]) / 2.0).roundToInt()
        } else {
            sorted[middle]
        }
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        if (lat1 == 0.0 && lon1 == 0.0) return Double.MAX_VALUE
        val r = 6371
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
