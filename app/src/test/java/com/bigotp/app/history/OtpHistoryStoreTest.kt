package com.bigotp.app.history

import com.bigotp.app.parser.OtpResult
import com.bigotp.app.parser.OtpType
import com.bigotp.app.parser.UrgencyLevel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OtpHistoryStoreTest {

    private var fakeTime = 0L

    private fun store() = OtpHistoryStore(
        cipher  = PlaintextCodeCipher(),
        storage = InMemoryHistoryStorage(),
        clock   = { fakeTime }
    )

    // ── add / retrieve ────────────────────────────────────────────────────────

    @Test
    fun `add one entry and retrieve it`() = runTest {
        val s = store()
        s.add(makeResult("111111"))
        val live = s.getLiveEntries()
        assertEquals(1, live.size)
        assertEquals("111111", live[0].code)
    }

    @Test
    fun `add two entries and retrieve both`() = runTest {
        val s = store()
        s.add(makeResult("111111"))
        s.add(makeResult("222222"))
        assertEquals(2, s.getLiveEntries().size)
    }

    @Test
    fun `add third entry does not evict when under limit`() = runTest {
        val s = store()
        fakeTime = 0L; s.add(makeResult("111111"))
        fakeTime = 1L; s.add(makeResult("222222"))
        fakeTime = 2L; s.add(makeResult("333333"))

        val codes = s.getLiveEntries().map { it.code }
        assertEquals(3, codes.size)
        assertTrue(codes.contains("111111"))
        assertTrue(codes.contains("222222"))
        assertTrue(codes.contains("333333"))
    }

    // ── expiry ────────────────────────────────────────────────────────────────

    @Test
    fun `LOGIN entry expires after 10 minutes`() = runTest {
        val s = store()
        fakeTime = 0L
        s.add(makeResult("777777", type = OtpType.LOGIN))
        fakeTime = 11 * 60 * 1_000L
        assertTrue(s.getLiveEntries().isEmpty())
    }

    @Test
    fun `PAYMENT entry expires after 5 minutes`() = runTest {
        val s = store()
        fakeTime = 0L
        s.add(makeResult("888888", type = OtpType.PAYMENT))
        fakeTime = 6 * 60 * 1_000L
        assertTrue(s.getLiveEntries().isEmpty())
    }

    @Test
    fun `PAYMENT entry still live within 5 minutes`() = runTest {
        val s = store()
        fakeTime = 0L
        s.add(makeResult("444444", type = OtpType.PAYMENT))
        fakeTime = 4 * 60 * 1_000L
        assertEquals(1, s.getLiveEntries().size)
    }

    @Test
    fun `LOGIN entry still live within 10 minutes`() = runTest {
        val s = store()
        fakeTime = 0L
        s.add(makeResult("555555", type = OtpType.LOGIN))
        fakeTime = 9 * 60 * 1_000L
        assertEquals(1, s.getLiveEntries().size)
    }

    @Test
    fun `all entries expired returns empty list`() = runTest {
        val s = store()
        fakeTime = 0L
        s.add(makeResult("111111"))
        s.add(makeResult("222222"))
        fakeTime = 20 * 60 * 1_000L
        assertTrue(s.getLiveEntries().isEmpty())
    }

    // ── clearExpired ──────────────────────────────────────────────────────────

    @Test
    fun `clearExpired removes expired and keeps live entries`() = runTest {
        val s = store()
        fakeTime = 0L
        s.add(makeResult("111111", type = OtpType.LOGIN))  // receivedAt = 0

        fakeTime = 11 * 60 * 1_000L                        // 11 min later
        s.add(makeResult("222222", type = OtpType.LOGIN))  // receivedAt = 11 min

        // 111111: age = 11 min > 10 min → expired
        // 222222: age = 0 → live
        s.clearExpired()

        val live = s.getLiveEntries()
        assertEquals(1, live.size)
        assertEquals("222222", live[0].code)
    }

    @Test
    fun `clearExpired on empty store does not throw`() = runTest {
        store().clearExpired()  // should complete silently
    }

    // ── field preservation ────────────────────────────────────────────────────

    @Test
    fun `all result fields survive round-trip through store`() = runTest {
        val s = store()
        val original = makeResult(
            code         = "123456",
            type         = OtpType.PAYMENT,
            sourceName   = "HDFC",
            amountString = "₹2,400",
            urgencyLevel = UrgencyLevel.WARNING
        )
        s.add(original)
        val r = s.getLiveEntries().first()
        assertEquals("123456",             r.code)
        assertEquals(OtpType.PAYMENT,      r.type)
        assertEquals("HDFC",               r.sourceName)
        assertEquals("₹2,400",             r.amountString)
        assertEquals(UrgencyLevel.WARNING,  r.urgencyLevel)
    }

    @Test
    fun `sourcePackage null is preserved`() = runTest {
        val s = store()
        s.add(makeResult("123456").copy(sourcePackage = null))
        assertNull(s.getLiveEntries().first().sourcePackage)
    }

    @Test
    fun `sourcePackage non-null is preserved`() = runTest {
        val s = store()
        s.add(makeResult("123456").copy(sourcePackage = "com.hdfc.bank"))
        assertEquals("com.hdfc.bank", s.getLiveEntries().first().sourcePackage)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeResult(
        code         : String       = "123456",
        type         : OtpType      = OtpType.LOGIN,
        sourceName   : String       = "TestBank",
        amountString : String?      = null,
        urgencyLevel : UrgencyLevel = UrgencyLevel.HEALTHY
    ) = OtpResult(
        code         = code,
        type         = type,
        sourceName   = sourceName,
        rawMessage   = "OTP is $code",
        confidence   = 0.95f,
        amountString = amountString,
        urgencyLevel = urgencyLevel
    )
}

// ── Fakes ──────────────────────────────────────────────────────────────────────

class PlaintextCodeCipher : CodeCipher {
    override fun encrypt(plaintext: String) = CodeCipher.CipherResult(plaintext, "")
    override fun decrypt(result: CodeCipher.CipherResult) = result.data
}

class InMemoryHistoryStorage : HistoryStorage {
    private var stored: String? = null
    override suspend fun load() = stored
    override suspend fun save(json: String) { stored = json }
}
