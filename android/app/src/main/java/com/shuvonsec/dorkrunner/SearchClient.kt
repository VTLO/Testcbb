package com.shuvonsec.dorkrunner

import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Auto-checks whether a dork returns results, using the official Google
 * Programmable Search (Custom Search JSON API). This is the only reliable and
 * ToS-compliant way to query programmatically — raw scraping of google.com is
 * blocked/CAPTCHA'd from app clients and is deliberately NOT implemented.
 *
 * Requires the user's own API key + Programmable Search Engine id (cx), entered
 * in Settings and stored locally. The free tier is 100 queries/day, which also
 * keeps the tool naturally rate-limited. Use only against in-scope targets.
 */
class SearchClient(private val apiKey: String, private val cx: String) {

    data class Result(val count: Long, val topLink: String, val error: String? = null)

    fun isConfigured(): Boolean = apiKey.isNotBlank() && cx.isNotBlank()

    fun check(dork: String): Result {
        val url = "https://www.googleapis.com/customsearch/v1" +
            "?key=${Uri.encode(apiKey)}&cx=${Uri.encode(cx)}&num=1&q=${Uri.encode(dork)}"
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.requestMethod = "GET"
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            if (code !in 200..299) {
                val msg = runCatching {
                    JSONObject(body).getJSONObject("error").getString("message")
                }.getOrDefault("HTTP $code")
                return Result(0, "", "API error: $msg")
            }
            val json = JSONObject(body)
            val count = json.optJSONObject("searchInformation")
                ?.optString("totalResults", "0")?.toLongOrNull() ?: 0L
            val items = json.optJSONArray("items")
            val top = if (items != null && items.length() > 0)
                items.getJSONObject(0).optString("link", "") else ""
            Result(count, top)
        } catch (e: Exception) {
            Result(0, "", "network error: ${e.message}")
        }
    }
}
