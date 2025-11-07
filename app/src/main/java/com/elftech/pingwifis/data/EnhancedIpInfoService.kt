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
        // Tenta obter via Ookla primeiro, que é mais preciso para o ISP
        var ooklaClientInfo: ClientInfo? = null
        try {
            val configRequest = Request.Builder().url("https://www.speedtest.net/speedtest-config.php").build()
            val configResponse = client.newCall(configRequest).execute()
            if (configResponse.isSuccessful) {
                val xmlBody = configResponse.body?.string()
                if (xmlBody != null) {
                    val (clientInfo, _) = parseClientInfoFromConfig(xmlBody)
                    ooklaClientInfo = clientInfo // Salva a info da Ookla
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Ookla falhou, mas continuamos para o fallback
        }

        // AGORA, SEMPRE TENTA ip-api.com para obter a cidade
        try {
            val request = Request.Builder()
                .url("http://ip-api.com/json?fields=status,message,countryCode,city,lat,lon,isp,query")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()?.let { body ->
                    val json = JSONObject(body)
                    if (json.getString("status") == "success") {
                        val ipApiCity = json.optString("city", "Unknown")
                        val ipApiCountry = json.optString("countryCode", "Unknown")

                        if (ooklaClientInfo != null) {
                            // SUCESSO: Temos Ookla. Vamos "remendar" a cidade.
                            // Se a cidade do ip-api for válida, use-a.
                            if (ipApiCity != "Unknown") {
                                return@withContext ooklaClientInfo.copy(city = ipApiCity)
                            } else {
                                // ip-api também não sabe a cidade,
                                // mas a Ookla pode ter um país melhor.
                                // Se o país da Ookla for "Unknown", use o do ip-api.
                                val finalCountry = if (ooklaClientInfo.country == "Unknown") ipApiCountry else ooklaClientInfo.country
                                return@withContext ooklaClientInfo.copy(city = "Unknown", country = finalCountry)
                            }
                        } else {
                            // FALLBACK: Ookla falhou. Use os dados do ip-api.
                            return@withContext ClientInfo(
                                ipAddress = json.optString("query", "Unknown"),
                                city = ipApiCity,
                                country = ipApiCountry,
                                isp = json.optString("isp", "Unknown"),
                                lat = json.optDouble("lat", 0.0),
                                lon = json.optDouble("lon", 0.0)
                            )
                        }
                    }
                }
            }

            // Se chegamos aqui, o ip-api falhou.
            // Retorna o que tivermos da Ookla (pode ser nulo ou ter "Unknown" city)
            return@withContext ooklaClientInfo

        } catch (e: Exception) {
            e.printStackTrace()
            // Se o ip-api falhar com exceção, retorna o que tivermos da Ookla
            return@withContext ooklaClientInfo
        }
    }

    /**
     * LÓGICA DE SELEÇÃO DE SERVIDOR PROFISSIONAL (Estilo WiFiman)
     * 1. Busca o CATÁLOGO COMPLETO de servidores Ookla (milhares).
     * 2. Filtra os 20 servidores GEOGRAFICAMENTE mais próximos do usuário.
     * 3. Adiciona os CDNs globais (Cloudflare, Google, etc) como curingas.
     * 4. Testa a latência (ping) de TODOS os candidatos em paralelo.
     * 5. Retorna a lista ordenada pelo menor ping.
     */
    suspend fun getNearbyServers(clientInfo: ClientInfo): List<SpeedTestServer> = withContext(Dispatchers.IO) {
        // Etapa 1: Obter candidatos de todas as fontes em paralelo
        // BUSCA A LISTA PROFISSIONAL: Os 20 servidores geograficamente mais próximos
        val ooklaServersDeferred = async { fetchOoklaFullServerList(clientInfo) }
        val curatedCdnServersDeferred = async { getCuratedCdnServers() }

        val ooklaClosestServers = try { ooklaServersDeferred.await() } catch (e: Exception) { emptyList() }
        val reachableCdnServers = try { curatedCdnServersDeferred.await() } catch (e: Exception) { emptyList() }

        // Etapa 2: Combinar listas
        val combinedList = (ooklaClosestServers + reachableCdnServers).toMutableList()
        val uniqueCandidates = combinedList.distinctBy { it.downloadUrl }

        // Etapa 3: Testar a latência de TODOS os candidatos em paralelo
        val serversWithLatency = uniqueCandidates.map { server ->
            async {
                val latency = pingServer(server) // AGORA CHAMA A FUNÇÃO CORRETA
                Pair(server, latency)
            }
        }.map { it.await() } // Espera todos os pings terminarem

        // Etapa 4: Filtrar servidores inacessíveis e ordenar pelo menor ping
        val sortedServers = serversWithLatency
            .filter { it.second < 9999 } // Filtra falhas de ping
            .sortedBy { it.second } // Ordena pelo menor ping
            .map { it.first } // Pega apenas o objeto SpeedTestServer

        // Se, depois de tudo, a lista estiver vazia (improvável),
        // retorne a lista original ordenada por geografia como último recurso.
        if (sortedServers.isEmpty() && uniqueCandidates.isNotEmpty()) {
            return@withContext uniqueCandidates
                .sortedBy { haversineDistance(clientInfo.lat, clientInfo.lon, it.lat, it.lon) }
        }

        return@withContext sortedServers
    }

    /**
     * MÉTODO PROFISSIONAL: Busca o catálogo COMPLETO da Ookla,
     * ordena por distância e retorna os 20 mais próximos.
     */
    private suspend fun fetchOoklaFullServerList(clientInfo: ClientInfo): List<SpeedTestServer> {
        return try {
            // 1. Obter o catálogo estático completo.
            val serversRequest = Request.Builder()
                .url("https://www.speedtest.net/speedtest-servers-static.php")
                .build()
            val serversResponse = client.newCall(serversRequest).execute()
            if (!serversResponse.isSuccessful) return emptyList()

            val xmlBody = serversResponse.body?.string() ?: return emptyList()

            // 2. Parsear a lista inteira
            val allServers = parseSpeedtestServersXml(xmlBody)

            // 3. Ordenar por distância e pegar os 20 mais próximos
            return allServers
                .sortedBy { haversineDistance(clientInfo.lat, clientInfo.lon, it.lat, it.lon) }
                .take(20)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // Helper para extrair ClientInfo do XML de configuração da Ookla
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
                        city = "Unknown", // O config não fornece a cidade, mas o ISP sim
                        country = parser.getAttributeValue(null, "country") ?: "Unknown",
                        isp = parser.getAttributeValue(null, "isp") ?: "Unknown",
                        lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0,
                        lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                    )
                } else if (eventType == XmlPullParser.START_TAG && parser.name == "server-config") {
                    // Exemplo de como pegar outros dados, se necessário
                    configMap["threads"] = parser.getAttributeValue(null, "threads") ?: "4"
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
                    val url = parser.getAttributeValue(null, "url")
                    // --- CORREÇÃO DE NULABILIDADE ---
                    // Só adiciona o servidor se a URL não for nula
                    if (url != null) {
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
                    // --- FIM DA CORREÇÃO ---
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return servers
    }

    private suspend fun getCuratedCdnServers(): List<SpeedTestServer> = withContext(Dispatchers.IO) {
        val fastComServer = fetchFastComServer()
        // URL genérica caso os CDNs não tenham um endpoint de upload dedicado
        val genericUploadUrl = "http://speedtest.vivo.com.br.prod.hosts.ooklaserver.net:8080/upload.php"

        val cdnList = mutableListOf(
            SpeedTestServer("Cloudflare CDN", "Global", "Worldwide", "https://speed.cloudflare.com/__down?bytes=100000000", "https://speed.cloudflare.com/__up", 0.0, 0.0),
            SpeedTestServer("Google CDN", "Global", "Worldwide", "https://storage.googleapis.com/gweb-cloud-storage-geo-testing/1GB.bin", genericUploadUrl, 0.0, 0.0),
            // ADICIONADO: Servidor de teste da Akamai
            SpeedTestServer("Akamai CDN", "Global", "Worldwide", "http://speedtest.akamaized.net/static/images/background.jpg", genericUploadUrl, 0.0, 0.0)

        )

        fastComServer?.let { cdnList.add(it) }

        return@withContext cdnList
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
                return@withContext SpeedTestServer("Netflix (Fast.com)", "Global", "Worldwide", url, url, 0.0, 0.0)
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * FUNÇÃO DE PING PROFISSIONAL (CORREÇÃO CRÍTICA)
     * O "ping" (latência) NUNCA deve ser medido contra um arquivo de download grande.
     * Isso mistura latência (ping) com velocidade (throughput).
     * O ping real deve ser a requisição mais leve possível.
     *
     * Estratégia:
     * 1. Servidores Ookla: Usamos um 'HEAD' request contra a URL BASE (uploadUrl).
     * 2. Servidores CDN: Usamos um 'HEAD' request contra a 'downloadUrl' (ou 'bytes=0' para Cloudflare).
     */
    suspend fun pingServer(server: SpeedTestServer): Int = withContext(Dispatchers.IO) {
        try {
            val urlForPing: String

            // --- Determina a URL correta para o PING (com checagem de nulabilidade) ---

            // Caso 1: Cloudflare CDN
            if (server.downloadUrl?.contains("cloudflare") == true) {
                // Cloudflare tem um endpoint de download que aceita 'bytes=0' para teste de latência
                urlForPing = server.downloadUrl!!.replaceAfter("bytes=", "0")

                // Caso 2: Servidores Ookla Padrão
            } else if (server.uploadUrl?.contains("upload.php") == true) {
                // Pingar a URL base (upload.php) é o método padrão de latência da Ookla.
                urlForPing = server.uploadUrl!!

                // Caso 3: Outros CDNs (Google, Akamai, Fast.com)
            } else if (server.downloadUrl != null) {
                // Para CDNs, um 'HEAD' request no arquivo de download é a melhor
                // aproximação de latência, pois eles não têm 'upload.php'.
                urlForPing = server.downloadUrl!!
            } else {
                // Se ambas as URLs forem nulas, não podemos pingar.
                return@withContext 9999
            }
            // --- FIM DAS CORREÇÕES ---

            // --- Executa o PING ---

            val request = Request.Builder()
                .url(urlForPing)
                .head() // USA HEAD - Esta é a mudança-chave! Não baixa o corpo.
                .build()

            val pings = mutableListOf<Long>()

            // 3 repetições para uma média de ping mais estável
            repeat(3) {
                val start = System.currentTimeMillis()
                client.newCall(request).execute().use { response ->
                    // O 'code' não precisa ser 200. Um 404, 403 etc.,
                    // ainda significa que o servidor respondeu e a latência foi medida.
                    if (response.code != 0) {
                        pings.add(System.currentTimeMillis() - start)
                    } else {
                        // A requisição falhou totalmente (ex: DNS, timeout)
                        // Não adicione à lista.
                    }
                }
            }
            if (pings.isEmpty()) 9999 else pings.average().toInt()
        } catch (e: Exception) {
            // Captura timeouts, DNS failures, etc.
            9999
        }
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        if (lat1 == 0.0 && lon1 == 0.0) return Double.MAX_VALUE // Põe CDNs no fim da lista geográfica
        val r = 6371
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}