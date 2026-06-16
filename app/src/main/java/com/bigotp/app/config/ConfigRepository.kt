package com.bigotp.app.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bigotp.app.parser.OtpPattern
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.configDataStore: DataStore<Preferences> by preferencesDataStore("config")

// ── Interfaces (internal — test fakes implement these) ───────────────────────

interface PatternCache {
    suspend fun load(): PatternConfig?
    suspend fun save(config: PatternConfig)
    suspend fun getLastFetchTime(): Long
    suspend fun setLastFetchTime(epochMs: Long)
}

fun interface FallbackSource {
    fun loadFallback(): PatternConfig
}

// ── Production implementations ───────────────────────────────────────────────

private val repoJson = Json { ignoreUnknownKeys = true }

internal class DataStorePatternCache(private val context: Context) : PatternCache {

    private object Keys {
        val VERSION       = intPreferencesKey("cfg_version")
        val PATTERNS_JSON = stringPreferencesKey("cfg_patterns_json")
        val LAST_FETCH    = longPreferencesKey("cfg_last_fetch")
    }

    override suspend fun load(): PatternConfig? {
        val prefs = context.configDataStore.data.first()
        prefs[Keys.VERSION]       ?: return null
        val json = prefs[Keys.PATTERNS_JSON] ?: return null
        return runCatching { repoJson.decodeFromString<PatternConfig>(json) }.getOrNull()
    }

    override suspend fun save(config: PatternConfig) {
        val json = repoJson.encodeToString(config)
        context.configDataStore.edit { prefs ->
            prefs[Keys.VERSION]       = config.version
            prefs[Keys.PATTERNS_JSON] = json
        }
    }

    override suspend fun getLastFetchTime(): Long =
        context.configDataStore.data.first()[Keys.LAST_FETCH] ?: 0L

    override suspend fun setLastFetchTime(epochMs: Long) {
        context.configDataStore.edit { prefs -> prefs[Keys.LAST_FETCH] = epochMs }
    }
}

internal class AssetFallbackSource(private val context: Context) : FallbackSource {
    override fun loadFallback(): PatternConfig {
        val json = context.assets.open("patterns_fallback.json").bufferedReader().readText()
        return repoJson.decodeFromString(json)
    }
}

// ── Repository ────────────────────────────────────────────────────────────────

class ConfigRepository(
    private val fetcher: PatternFetcher,
    private val cache: PatternCache,
    private val fallback: FallbackSource
) {
    private val mutex = Mutex()
    @Volatile private var inMemoryPatterns: List<OtpPattern>? = null

    constructor(context: Context) : this(
        ConfigFetcher(),
        DataStorePatternCache(context),
        AssetFallbackSource(context)
    )

    suspend fun getPatterns(): List<OtpPattern> = mutex.withLock {
        inMemoryPatterns ?: run {
            val patterns = cache.load()?.patterns ?: fallback.loadFallback().patterns
            inMemoryPatterns = patterns
            patterns
        }
    }

    suspend fun getLastFetchTime(): Long = cache.getLastFetchTime()

    // Called by ConfigRefreshWorker. Updates DataStore cache only; does not
    // hot-swap patterns mid-session. Clears in-memory cache so the next call
    // to getPatterns() picks up the new version from DataStore.
    suspend fun refreshInBackground() {
        val remote = fetcher.fetch() ?: return
        val cached = cache.load()
        if (cached == null || remote.version > cached.version) {
            cache.save(remote)
            inMemoryPatterns = null
        }
        cache.setLastFetchTime(System.currentTimeMillis())
    }
}
