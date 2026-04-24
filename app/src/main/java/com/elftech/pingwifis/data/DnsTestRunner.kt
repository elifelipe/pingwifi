package com.elftech.pingwifis.data

import com.elftech.pingwifis.data.model.DnsResult
import com.elftech.pingwifis.data.model.DnsServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class DnsTestRunner(private val scope: CoroutineScope) {

    val servers = listOf(
        DnsServer("ISP (Padrão)", "system"),
        DnsServer("Google DNS", "8.8.8.8"),
        DnsServer("Cloudflare", "1.1.1.1"),
        DnsServer("Quad9", "9.9.9.9")
    )

    fun runTest(
        hostname: String,
        onResult: (DnsResult) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                servers.forEach { server ->
                    val result = if (server.address == "system") {
                        measureSystemDns(hostname, server)
                    } else {
                        measureCustomDns(hostname, server)
                    }
                    onResult(result)
                }
                onComplete()
            } catch (e: Exception) {
                onError(e.message ?: "Erro desconhecido")
            }
        }
    }

    private fun measureSystemDns(hostname: String, server: DnsServer): DnsResult {
        return try {
            val start = System.currentTimeMillis()
            val addresses = InetAddress.getAllByName(hostname)
            val elapsed = System.currentTimeMillis() - start
            DnsResult(server, elapsed, true, addresses.firstOrNull()?.hostAddress)
        } catch (e: Exception) {
            DnsResult(server, -1L, false, null, "Falha na resolução")
        }
    }

    private fun measureCustomDns(hostname: String, server: DnsServer): DnsResult {
        return try {
            val queryBytes = buildDnsQuery(hostname)
            val socket = DatagramSocket()
            socket.soTimeout = 3000
            val dnsAddress = InetAddress.getByName(server.address)
            val sendPacket = DatagramPacket(queryBytes, queryBytes.size, dnsAddress, 53)

            val start = System.currentTimeMillis()
            socket.send(sendPacket)

            val responseBuffer = ByteArray(512)
            val receivePacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(receivePacket)
            val elapsed = System.currentTimeMillis() - start
            socket.close()

            val resolvedIp = parseFirstARecord(receivePacket.data, receivePacket.length)
            DnsResult(server, elapsed, resolvedIp != null, resolvedIp)
        } catch (e: Exception) {
            DnsResult(server, -1L, false, null, "Timeout ou falha")
        }
    }

    private fun buildDnsQuery(hostname: String): ByteArray {
        val buf = mutableListOf<Byte>()
        buf.add(0x00); buf.add(0x01) // Transaction ID
        buf.add(0x01); buf.add(0x00) // Flags: standard query, recursion desired
        buf.add(0x00); buf.add(0x01) // Questions: 1
        repeat(6) { buf.add(0x00) }  // Answer/Auth/Additional RRs: 0

        hostname.split(".").forEach { label ->
            buf.add(label.length.toByte())
            label.forEach { c -> buf.add(c.code.toByte()) }
        }
        buf.add(0x00) // End of hostname
        buf.add(0x00); buf.add(0x01) // Type A
        buf.add(0x00); buf.add(0x01) // Class IN

        return buf.toByteArray()
    }

    private fun parseFirstARecord(data: ByteArray, length: Int): String? {
        return try {
            if (length < 12) return null
            val anCount = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
            if (anCount == 0) return null

            var pos = 12
            while (pos < length) {
                val labelLen = data[pos].toInt() and 0xFF
                if (labelLen == 0) { pos++; break }
                pos += labelLen + 1
            }
            pos += 4 // QTYPE (2) + QCLASS (2)

            repeat(anCount) {
                if (pos >= length) return null
                if (pos < length && (data[pos].toInt() and 0xFF) == 0xC0) {
                    pos += 2
                } else {
                    while (pos < length && data[pos] != 0.toByte()) pos++
                    pos++
                }
                if (pos + 10 >= length) return null
                val rType = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
                pos += 8
                val rdLength = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
                pos += 2
                if (rType == 1 && rdLength == 4) {
                    return "${data[pos].toInt() and 0xFF}.${data[pos+1].toInt() and 0xFF}" +
                           ".${data[pos+2].toInt() and 0xFF}.${data[pos+3].toInt() and 0xFF}"
                }
                pos += rdLength
            }
            null
        } catch (e: Exception) { null }
    }
}
