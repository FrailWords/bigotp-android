package com.bigotp.app.history

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bigotp.app.parser.OtpResult
import com.bigotp.app.parser.OtpType
import com.bigotp.app.parser.UrgencyLevel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.historyDataStore: DataStore<Preferences> by preferencesDataStore("history")
private val HISTORY_KEY = stringPreferencesKey("history_json")

// ── Storage abstraction (injected in tests) ───────────────────────────────────

interface HistoryStorage {
    suspend fun load(): String?
    suspend fun save(json: String)
}

internal class DataStoreHistoryStorage(private val context: Context) : HistoryStorage {
    override suspend fun load(): String? =
        context.historyDataStore.data.first()[HISTORY_KEY]

    override suspend fun save(json: String) {
        context.historyDataStore.edit { prefs -> prefs[HISTORY_KEY] = json }
    }
}

// ── Serialisable history entry ────────────────────────────────────────────────

@Serializable
private data class HistoryEntry(
    val encryptedCode: String,
    val iv: String,
    val type: OtpType,
    val sourceName: String,
    val sourcePackage: String? = null,
    val amountString: String? = null,
    val rawMessage: String,
    val confidence: Float,
    val receivedAt: Long,
    val urgencyOrdinal: Int = UrgencyLevel.HEALTHY.ordinal
)

private val historyJson = Json { ignoreUnknownKeys = true }

private fun HistoryEntry.toOtpResult(code: String) = OtpResult(
    code          = code,
    type          = type,
    sourceName    = sourceName,
    sourcePackage = sourcePackage,
    amountString  = amountString,
    rawMessage    = rawMessage,
    confidence    = confidence,
    urgencyLevel  = UrgencyLevel.entries[urgencyOrdinal.coerceIn(0, UrgencyLevel.entries.size - 1)]
)

// ── Live entry (includes timing metadata for the main screen) ─────────────────

data class LiveEntry(
    val result: OtpResult,
    val receivedAt: Long,
    val expiresAtMs: Long
)

// ── Store ─────────────────────────────────────────────────────────────────────

class OtpHistoryStore(
    private val cipher:  CodeCipher,
    private val storage: HistoryStorage,
    private val clock:   () -> Long = System::currentTimeMillis
) {
    constructor(context: Context) : this(
        cipher  = KeystoreCodeCipher(),
        storage = DataStoreHistoryStorage(context)
    )

    private val mutex = Mutex()

    suspend fun add(result: OtpResult) = mutex.withLock {
        val entries = loadEntries().toMutableList()
        if (entries.size >= MAX_ENTRIES) {
            // Drop the oldest entry before inserting.
            entries.sortBy { it.receivedAt }
            entries.removeAt(0)
        }
        val encrypted = cipher.encrypt(result.code)
        entries += HistoryEntry(
            encryptedCode = encrypted.data,
            iv            = encrypted.iv,
            type          = result.type,
            sourceName    = result.sourceName,
            sourcePackage = result.sourcePackage,
            amountString  = result.amountString,
            rawMessage    = result.rawMessage,
            confidence    = result.confidence,
            receivedAt    = clock(),
            urgencyOrdinal = result.urgencyLevel.ordinal
        )
        storage.save(historyJson.encodeToString(entries))
    }

    suspend fun getLiveEntries(): List<OtpResult> {
        val now = clock()
        return loadEntries()
            .filter { !isExpired(it, now) }
            .mapNotNull { entry ->
                runCatching {
                    val code = cipher.decrypt(CodeCipher.CipherResult(entry.encryptedCode, entry.iv))
                    entry.toOtpResult(code)
                }.getOrNull()
            }
    }

    suspend fun getLiveEntriesWithTimestamp(): List<LiveEntry> {
        val now = clock()
        return loadEntries()
            .filter { !isExpired(it, now) }
            .mapNotNull { entry ->
                runCatching {
                    val code = cipher.decrypt(CodeCipher.CipherResult(entry.encryptedCode, entry.iv))
                    LiveEntry(
                        result     = entry.toOtpResult(code),
                        receivedAt = entry.receivedAt,
                        expiresAtMs = entry.receivedAt + expiryMs(entry.type)
                    )
                }.getOrNull()
            }
    }

    suspend fun clearExpired() = mutex.withLock {
        val now = clock()
        val live = loadEntries().filter { !isExpired(it, now) }
        storage.save(historyJson.encodeToString(live))
    }

    suspend fun removeEntry(receivedAt: Long) = mutex.withLock {
        val updated = loadEntries().filter { it.receivedAt != receivedAt }
        storage.save(historyJson.encodeToString(updated))
    }

    private suspend fun loadEntries(): List<HistoryEntry> {
        val jsonStr = storage.load() ?: return emptyList()
        return runCatching { historyJson.decodeFromString<List<HistoryEntry>>(jsonStr) }
            .getOrDefault(emptyList())
    }

    private fun expiryMs(type: OtpType): Long = when (type) {
        OtpType.PAYMENT -> 3L * 60 * 1_000   // 3 minutes
        else            -> 2L * 60 * 1_000    // 2 minutes
    }

    private fun isExpired(entry: HistoryEntry, now: Long): Boolean =
        now - entry.receivedAt > expiryMs(entry.type)

    companion object {
        private const val MAX_ENTRIES = 20
    }
}
