package com.bigotp.app.config

import com.bigotp.app.parser.OtpPattern
import com.bigotp.app.parser.OtpType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ConfigRepositoryTest {

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private val remotePattern  = makePattern("remote")
    private val cachedPattern  = makePattern("cached")
    private val fallbackPattern = makePattern("fallback")

    private val remoteConfig   = PatternConfig(version = 2, patterns = listOf(remotePattern))
    private val cachedConfig   = PatternConfig(version = 1, patterns = listOf(cachedPattern))
    private val fallbackConfig = PatternConfig(version = 0, patterns = listOf(fallbackPattern))

    private lateinit var fakeFetcher: FakePatternFetcher
    private lateinit var fakeCache: InMemoryPatternCache

    @Before
    fun setUp() {
        fakeFetcher = FakePatternFetcher(result = null)
        fakeCache   = InMemoryPatternCache()
    }

    private fun repo() = ConfigRepository(fakeFetcher, fakeCache, FallbackSource { fallbackConfig })

    // ── getPatterns() ─────────────────────────────────────────────────────────

    @Test
    fun `getPatterns returns cache when available`() = runTest {
        fakeCache.stored = cachedConfig
        assertEquals(listOf(cachedPattern), repo().getPatterns())
    }

    @Test
    fun `getPatterns returns bundled fallback when cache is empty`() = runTest {
        assertEquals(listOf(fallbackPattern), repo().getPatterns())
    }

    @Test
    fun `getPatterns caches result in memory so second call skips DataStore`() = runTest {
        val r = repo()
        fakeCache.stored = cachedConfig
        r.getPatterns()           // warm in-memory cache
        fakeCache.stored = null   // wipe DataStore backing
        assertEquals(listOf(cachedPattern), r.getPatterns())
    }

    // ── refreshInBackground() ─────────────────────────────────────────────────

    @Test
    fun `refresh with newer remote version updates cache`() = runTest {
        fakeCache.stored = cachedConfig   // version 1
        fakeFetcher.result = remoteConfig // version 2
        repo().refreshInBackground()
        assertEquals(remoteConfig, fakeCache.stored)
    }

    @Test
    fun `refresh with same version as cache does not overwrite`() = runTest {
        val sameVersion = PatternConfig(version = 1, patterns = listOf(remotePattern))
        fakeCache.stored = cachedConfig   // version 1
        fakeFetcher.result = sameVersion  // version 1 — no update expected
        repo().refreshInBackground()
        assertEquals(cachedConfig, fakeCache.stored)
    }

    @Test
    fun `refresh when remote fails leaves cache unchanged`() = runTest {
        fakeCache.stored = cachedConfig
        fakeFetcher.result = null         // network failure
        repo().refreshInBackground()
        assertEquals(cachedConfig, fakeCache.stored)
    }

    @Test
    fun `refresh with no cache saves remote config`() = runTest {
        fakeFetcher.result = remoteConfig
        repo().refreshInBackground()
        assertEquals(remoteConfig, fakeCache.stored)
    }

    @Test
    fun `refresh updates last fetch time on success`() = runTest {
        fakeFetcher.result = remoteConfig
        repo().refreshInBackground()
        assertTrue(fakeCache.lastFetch > 0L)
    }

    @Test
    fun `refresh does not update last fetch time on failure`() = runTest {
        fakeFetcher.result = null
        repo().refreshInBackground()
        assertEquals(0L, fakeCache.lastFetch)
    }

    @Test
    fun `refresh invalidates in-memory cache so next getPatterns loads new data`() = runTest {
        val r = repo()
        fakeCache.stored = cachedConfig
        r.getPatterns()                    // warm in-memory cache with v1
        fakeFetcher.result = remoteConfig  // v2 available
        r.refreshInBackground()            // should invalidate in-memory
        assertEquals(listOf(remotePattern), r.getPatterns())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makePattern(id: String) = OtpPattern(
        id             = id,
        sourceKeywords = listOf(id.uppercase()),
        otpRegex       = """\b(\d{6})\b""",
        type           = OtpType.LOGIN
    )
}

// ── Fakes (package-private, visible to this test only) ────────────────────────

class FakePatternFetcher(var result: PatternConfig?) : PatternFetcher {
    override suspend fun fetch() = result
}

class InMemoryPatternCache : PatternCache {
    var stored: PatternConfig? = null
    var lastFetch: Long = 0L

    override suspend fun load() = stored
    override suspend fun save(config: PatternConfig) { stored = config }
    override suspend fun getLastFetchTime() = lastFetch
    override suspend fun setLastFetchTime(epochMs: Long) { lastFetch = epochMs }
}
