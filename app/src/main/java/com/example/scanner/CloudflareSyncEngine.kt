package com.example.scanner

import android.util.Log
import com.example.data.CloudflareSyncRule
import com.example.data.ScannedIp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object CloudflareSyncEngine {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun syncRule(rule: CloudflareSyncRule, ipsToSync: List<ScannedIp>): Boolean = withContext(Dispatchers.IO) {
        if (ipsToSync.isEmpty()) return@withContext false

        val headersBuilder = okhttp3.Headers.Builder()
        if (rule.email.isNotBlank()) {
            headersBuilder.add("X-Auth-Email", rule.email)
            headersBuilder.add("X-Auth-Key", rule.apiKey)
        } else {
            headersBuilder.add("Authorization", "Bearer ${rule.apiKey}")
        }
        headersBuilder.add("Content-Type", "application/json")
        val headers = headersBuilder.build()

        val url = "https://api.cloudflare.com/client/v4/zones/${rule.zoneId}/dns_records"

        try {
            // Only sync IPv4 (type A) for now based on ScannedIp
            val type = "A"
            val getUrl = "$url?type=$type&name=${rule.targetDomain}"
            val request = Request.Builder().url(getUrl).headers(headers).get().build()
            val response = client.newCall(request).execute()
            val respStr = response.body?.string() ?: ""
            Log.d("CF_SYNC", "GET records: $respStr")

            val json = JSONObject(respStr)
            if (!json.getBoolean("success")) {
                return@withContext false
            }

            val existingRecords = json.getJSONArray("result")
            val existingMap = mutableMapOf<String, String>()
            for (i in 0 until existingRecords.length()) {
                val r = existingRecords.getJSONObject(i)
                existingMap[r.getString("content")] = r.getString("id")
            }

            val desiredIps = ipsToSync.map { it.ip }.take(rule.syncCount)

            // Delete existing that are not in desired
            for ((ipVal, rId) in existingMap) {
                if (ipVal !in desiredIps) {
                    val delUrl = "$url/$rId"
                    val delReq = Request.Builder().url(delUrl).headers(headers).delete().build()
                    client.newCall(delReq).execute()
                }
            }

            // Post new ones
            for (ipVal in desiredIps) {
                if (ipVal !in existingMap) {
                    val bodyJson = JSONObject().apply {
                        put("type", type)
                        put("name", rule.targetDomain)
                        put("content", ipVal)
                        put("ttl", 1)
                        put("proxied", false)
                    }
                    val reqBody = bodyJson.toString().toRequestBody("application/json".toMediaTypeOrNull())
                    val postReq = Request.Builder().url(url).headers(headers).post(reqBody).build()
                    client.newCall(postReq).execute()
                }
            }
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
}
