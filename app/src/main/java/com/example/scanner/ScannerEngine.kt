package com.example.scanner

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object ScannerEngine {
    
    // Some common CF IPv4 ranges
    private val cfCidrs = listOf(
        "104.16.0.0/13",
        "104.22.0.0/16",
        "104.23.0.0/16",
        "162.152.0.0/13",
        "162.158.0.0/16",
        "162.159.0.0/16",
        "172.64.0.0/13",
        "172.68.0.0/16",
        "172.69.0.0/16",
        "172.70.0.0/16",
        "172.71.0.0/16"
    )

    fun generateIpsAroundSeeds(seeds: List<String>, count: Int): List<String> {
        val ips = mutableListOf<String>()
        val random = Random.Default
        if (seeds.isEmpty()) return generateRandomIps(count)
        
        for (i in 0 until count) {
            // 90% chance to expand around a known seed, 10% chance to explore totally random
            if (random.nextInt(100) < 90) {
                val seed = seeds[random.nextInt(seeds.size)]
                val parts = seed.split(".")
                if (parts.size == 4) {
                    val newIp = if (random.nextBoolean()) {
                        // /24 expansion
                        "${parts[0]}.${parts[1]}.${parts[2]}.${random.nextInt(256)}"
                    } else {
                        // /16 expansion
                        "${parts[0]}.${parts[1]}.${random.nextInt(256)}.${random.nextInt(256)}"
                    }
                    ips.add(newIp)
                } else {
                    ips.addAll(generateRandomIps(1))
                }
            } else {
                ips.addAll(generateRandomIps(1))
            }
        }
        return ips
    }

    fun generateRandomIps(count: Int): List<String> {
        val ips = mutableListOf<String>()
        val random = Random.Default
        for (i in 0 until count) {
            val cidr = cfCidrs[random.nextInt(cfCidrs.size)]
            val parts = cidr.split("/")
            val baseIp = parts[0]
            val prefix = parts[1].toInt()
            
            val ipParts = baseIp.split(".").map { it.toLong() }
            var ipLong = (ipParts[0] shl 24) or (ipParts[1] shl 16) or (ipParts[2] shl 8) or ipParts[3]
            
            val hostBits = 32 - prefix
            val mask = (1L shl hostBits) - 1
            val randomHost = (random.nextLong() and mask)
            
            val finalIpLong = (ipLong and mask.inv()) or randomHost
            
            val p1 = (finalIpLong shr 24) and 255
            val p2 = (finalIpLong shr 16) and 255
            val p3 = (finalIpLong shr 8) and 255
            val p4 = finalIpLong and 255
            
            ips.add("$p1.$p2.$p3.$p4")
        }
        return ips
    }

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // 1A. 利用 Cloudflare Worker API 验证 CF Proxy IP (方案 A: 云端检测)
    suspend fun testIpViaApi(apiUrl: String, ip: String, port: Int = 443): ScanResult? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val url = if (apiUrl.endsWith("/")) "${apiUrl}check?proxyip=$ip:$port" else "$apiUrl/check?proxyip=$ip:$port"
            val request = Request.Builder()
                .url(url)
                .build()
            
            baseClient.newCall(request).execute().use { response ->
                val connectTime = System.currentTimeMillis() - startTime
                val body = response.body?.string() ?: "{}"
                
                try {
                    val json = JSONObject(body)
                    val isSuccess = json.optBoolean("success", false)
                    
                    if (isSuccess) {
                        val colo = if (json.has("colo") && !json.isNull("colo")) {
                            json.getString("colo")
                        } else if (json.has("country") && !json.isNull("country")) {
                            json.getString("country")
                        } else {
                            "UNK"
                        }
                        
                        if (colo.uppercase() == "UNK") return@withContext null
                        
                        val apiLatency = if (json.has("latencyMs") && !json.isNull("latencyMs")) {
                            json.getLong("latencyMs")
                        } else {
                            connectTime
                        }
                        
                        return@withContext ScanResult(ip, colo, apiLatency)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    // 1B. 建立 TCP 连接，模拟 HTTP 请求，验证 CF Proxy IP (方案 B: 本地直连)
    suspend fun testIp(ip: String, port: Int = 443): ScanResult? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(ip, port), 2000)
            val connectTime = System.currentTimeMillis() - startTime

            val httpRequest = "GET /cdn-cgi/trace HTTP/1.1\r\n" +
                    "Host: speed.cloudflare.com\r\n" +
                    "User-Agent: CheckProxyIP/cmliu\r\n" +
                    "Connection: close\r\n\r\n"

            val outStream: OutputStream = socket.getOutputStream()
            outStream.write(httpRequest.toByteArray())
            outStream.flush()

            val inStream: InputStream = socket.getInputStream()
            val buffer = ByteArray(4096)
            var responseText = ""
            
            val bytesRead = inStream.read(buffer)
            if (bytesRead > 0) {
                responseText = String(buffer, 0, bytesRead)
            }

            val statusMatch = Regex("^HTTP/\\d\\.\\d\\s+(\\d+)").find(responseText)
            val statusCode = statusMatch?.groupValues?.get(1)?.toIntOrNull()
            
            val looksLikeCloudflare = responseText.contains("cloudflare", ignoreCase = true)
            val isExpectedError = responseText.contains("plain HTTP request") || responseText.contains("400 Bad Request")
            val hasBody = responseText.length > 100

            if (statusCode != null && looksLikeCloudflare && isExpectedError && hasBody) {
                val cfRayMatch = Regex("CF-RAY:\\s*[a-zA-Z0-9]+-([A-Z]{3})").find(responseText)
                val colo = cfRayMatch?.groupValues?.get(1) ?: "UNK"
                return@withContext ScanResult(ip, colo, connectTime)
            }
        } catch (e: Exception) {
            // Failed connection or timeout
        } finally {
            try {
                socket?.close()
            } catch (e: Exception) {}
        }
        return@withContext null
    }

    // 2. 利用 1.1.1.1 的 DoH 解析域名
    suspend fun resolveDomain(domain: String): List<String> = withContext(Dispatchers.IO) {
        val ips = mutableListOf<String>()
        try {
            val request = Request.Builder()
                .url("https://1.1.1.1/dns-query?name=$domain&type=A")
                .header("Accept", "application/dns-json")
                .build()
            
            baseClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: "{}"
                    val json = JSONObject(body)
                    if (json.has("Answer")) {
                        val answers = json.getJSONArray("Answer")
                        for (i in 0 until answers.length()) {
                            val record = answers.getJSONObject(i)
                            if (record.getInt("type") == 1) { // A record (IPv4)
                                ips.add(record.getString("data"))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext ips
    }

    // 3. 利用 ip-api.com 获取 IP 的地理和 ASN 信息
    suspend fun getIpInfo(ip: String): IpInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("http://ip-api.com/json/$ip?lang=zh-CN")
                .build()
            
            baseClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: "{}"
                    val json = JSONObject(body)
                    if (json.optString("status") == "success") {
                        return@withContext IpInfo(
                            country = json.optString("country", "未知"),
                            asn = json.optString("as", "未知")
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }
}

data class ScanResult(val ip: String, val colo: String, val latency: Long)
data class IpInfo(val country: String, val asn: String)
