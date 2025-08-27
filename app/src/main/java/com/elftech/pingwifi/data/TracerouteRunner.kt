package com.elftech.pingwifi.data

import kotlinx.coroutines.*

/**
 * Usa lib nativa com.wandroid.traceroute, com fallback por shell `ping` (TTL crescente).
 */
class TracerouteRunner(private val scope: CoroutineScope) {

    fun run(
        host: String,
        maxHops: Int = 30,
        onLine: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            val usedNative = tryNative(host, onLine)
            if (usedNative) {
                withContext(Dispatchers.Main) { onDone() }
                return@launch
            }

            val ok = tryShellPing(host, maxHops, onLine)
            withContext(Dispatchers.Main) {
                if (ok) onDone() else onError("Traceroute falhou (lib nativa ausente e fallback indisponível).")
            }
        }
    }

    private fun tryNative(host: String, onLine: (String) -> Unit): Boolean {
        return try {
            val clazz = Class.forName("com.wandroid.traceroute.TraceRoute")
            val method = clazz.getMethod("traceRoute", String::class.java)
            val result = method.invoke(null, host) as? String
            if (!result.isNullOrBlank()) {
                result.lines().forEach { onLine(it) }
                true
            } else false
        } catch (_: Throwable) {
            false
        }
    }

    private fun tryShellPing(host: String, maxHops: Int, onLine: (String) -> Unit): Boolean {
        return try {
            for (ttl in 1..maxHops) {
                val cmd = arrayOf("/system/bin/ping", "-c", "1", "-W", "1", "-t", ttl.toString(), host)
                val proc = ProcessBuilder(*cmd).redirectErrorStream(true).start()
                val out = proc.inputStream.bufferedReader().use { it.readText() }
                proc.waitFor()

                val hopLine = out.lineSequence()
                    .firstOrNull { it.contains("from") || it.contains("icmp_seq") || it.contains("time=") }
                    ?: " $ttl  * * *"
                onLine("${ttl.toString().padStart(2)}  $hopLine")

                if (out.contains(host) && (out.contains("ttl=") || out.contains("bytes from"))) break
            }
            true
        } catch (_: Throwable) {
            false
        }
    }
}
