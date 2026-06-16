package com.bigotp.app.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

fun interface PatternFetcher {
    suspend fun fetch(): PatternConfig?
}

private val fetcherJson = Json { ignoreUnknownKeys = true }

class ConfigFetcher : PatternFetcher {

    override suspend fun fetch(): PatternConfig? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(CDN_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout    = 10_000
                requestMethod  = "GET"
            }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val body = conn.inputStream.bufferedReader().readText()
                fetcherJson.decodeFromString<PatternConfig>(body)
            } else null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        private const val CDN_URL = "https://bigotp.in/patterns/v1.json"
    }
}
