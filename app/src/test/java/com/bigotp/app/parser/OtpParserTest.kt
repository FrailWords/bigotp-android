package com.bigotp.app.parser

import org.junit.Assert.*
import org.junit.Test

class OtpParserTest {

    // ── Minimal pattern set covering every test case ──────────────────

    private val patterns = listOf(
        OtpPattern(
            id             = "sbi",
            sourceKeywords = listOf("SBI", "SBIINB", "State Bank", "SBIPSG"),
            otpRegex       = """\b(\d{6})\b""",
            type           = OtpType.LOGIN
        ),
        OtpPattern(
            id             = "sbi-payment",
            sourceKeywords = listOf("SBI", "SBIINB", "State Bank"),
            otpRegex       = """\b(\d{6})\b""",
            type           = OtpType.PAYMENT,
            amountRegex    = """(?:Rs\.?|₹)\s*(\d[\d,]*)"""
        ),
        OtpPattern(
            id             = "hdfc",
            sourceKeywords = listOf("HDFC", "HDFCBK", "HDFC Bank"),
            otpRegex       = """\b(\d{6})\b""",
            type           = OtpType.LOGIN
        ),
        OtpPattern(
            id             = "phonepe",
            sourceKeywords = listOf("PhonePe", "PHONEPE", "Phone Pe"),
            otpRegex       = """\b(\d{6})\b""",
            type           = OtpType.PAYMENT,
            amountRegex    = """(?:Rs\.?|₹)\s*(\d[\d,]*)"""
        ),
        OtpPattern(
            id             = "generic",
            sourceKeywords = listOf(
                "OTP", "one-time", "one time", "verification code",
                "access code", "security code", "login code",
                "share", "do not share"
            ),
            otpRegex       = """\b(\d{4,8}|[A-Z0-9]{4,8})\b""",
            type           = OtpType.LOGIN
        )
    )

    // ══════════════════════════════════════════════════════════════════
    // 10 valid OTP message formats
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `format 10 - code immediately followed by word no space`() {
        // Real Indian SMS: "706371Thanks" — no space between code and next word.
        val r = parse("Dear User ,Please find the OTP :  706371Thanks and Regards HITPA Team")
        assertNotNull(r)
        assertEquals("706371", r!!.code)
        assertEquals(OtpType.LOGIN, r.type)
    }

    @Test
    fun `format 1 - your OTP is`() {
        val r = parse("Your OTP is 472193")
        assertNotNull(r)
        assertEquals("472193", r!!.code)
        assertEquals(OtpType.LOGIN, r.type)
    }

    @Test
    fun `format 2 - OTP colon`() {
        val r = parse("OTP: 472193")
        assertNotNull(r)
        assertEquals("472193", r!!.code)
    }

    @Test
    fun `format 3 - code before source name`() {
        val r = parse("472193 is your PhonePe OTP")
        assertNotNull(r)
        assertEquals("472193", r!!.code)
        assertEquals(OtpType.PAYMENT, r.type)
        assertEquals("PhonePe", r.sourceName)
    }

    @Test
    fun `format 4 - do not share`() {
        val r = parse("Do not share 472193 with anyone")
        assertNotNull(r)
        assertEquals("472193", r!!.code)
    }

    @Test
    fun `format 5 - hinglish aapka OTP hai`() {
        val r = parse("Aapka OTP 472193 hai")
        assertNotNull(r)
        assertEquals("472193", r!!.code)
    }

    @Test
    fun `format 6 - payment OTP with Rs amount`() {
        val r = parse("OTP for payment of Rs.2400 is 4721")
        assertNotNull(r)
        assertEquals("4721", r!!.code)
        assertEquals(OtpType.PAYMENT, r.type)
        assertEquals("₹2,400", r.amountString)
    }

    @Test
    fun `format 7 - spaced verification code normalised`() {
        val r = parse("Your verification code: 47 21 93")
        assertNotNull(r)
        assertEquals("472193", r!!.code)
    }

    @Test
    fun `format 8 - alphanumeric login code`() {
        val r = parse("A8X2K1 is your login code")
        assertNotNull(r)
        assertEquals("A8X2K1", r!!.code)
        assertEquals(OtpType.LOGIN, r.type)
    }

    @Test
    fun `format 9 - SBI net banking OTP`() {
        val r = parse("Dear Customer, OTP to login to SBI net banking is 847291. NEVER share")
        assertNotNull(r)
        assertEquals("847291", r!!.code)
        assertEquals("SBI", r.sourceName)
    }

    // ══════════════════════════════════════════════════════════════════
    // 5 false positives → must return null
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `fp1 - phone number not matched`() {
        assertNull(parse("9876543210 missed call"))
    }

    @Test
    fun `fp2 - debit amount without OTP keyword`() {
        assertNull(parse("Rs.5000 debited from your account"))
    }

    @Test
    fun `fp3 - date in message not matched`() {
        assertNull(parse("Your appointment is on 01/06/2025"))
    }

    @Test
    fun `fp4 - 12 digit reference number not matched`() {
        assertNull(parse("Ref: 123456789012 processed"))
    }

    @Test
    fun `fp5 - UPI transaction ID not matched`() {
        // HDFC pattern matches on "HDFC" keyword but the 12-digit txn ID cannot
        // satisfy \b(\d{6})\b, so no valid code is found.
        assertNull(parse("UPI txn 407052352305 to HDFC"))
    }

    // ══════════════════════════════════════════════════════════════════
    // Spaced code normalisation
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `spaced 3-group 2-digit normalised to 6 digits`() {
        val r = parse("Your verification code: 47 21 93")
        assertNotNull(r); assertEquals("472193", r!!.code)
    }

    @Test
    fun `spaced 2-group 2-digit normalised to 4 digits`() {
        val r = parse("Your OTP is 47 21")
        assertNotNull(r); assertEquals("4721", r!!.code)
    }

    @Test
    fun `non-otp spaced digits are not collapsed`() {
        // "9876 5432 10" — total 10 digits — must NOT be collapsed to a 10-digit phone
        // The normaliser only collapses when total is 4–8 digits.
        assertNull(parse("Call 987 654 321 0"))
    }

    // ══════════════════════════════════════════════════════════════════
    // Amount extraction
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `amount with Rs dot prefix`() {
        val r = parse("OTP for payment of Rs.2400 is 4721")
        assertNotNull(r); assertEquals("₹2,400", r!!.amountString)
    }

    @Test
    fun `amount with rupee symbol`() {
        val r = parse("OTP for payment of ₹1200 is 9921")
        assertNotNull(r)
        assertEquals("9921", r!!.code)
        assertEquals(OtpType.PAYMENT, r.type)
        assertEquals("₹1,200", r.amountString)
    }

    @Test
    fun `amount with Indian comma formatting`() {
        // Rs.1,50,000 → strip commas → 150000 → format as ₹1,50,000 (Indian lakh system)
        val r = parse("Payment OTP for Rs.1,50,000 is 998821")
        assertNotNull(r)
        assertEquals("998821", r!!.code)
        assertEquals("₹1,50,000", r.amountString)
    }

    @Test
    fun `amount not present for login OTP`() {
        val r = parse("Your OTP is 472193")
        assertNotNull(r)
        assertNull(r!!.amountString)
    }

    // ══════════════════════════════════════════════════════════════════
    // Multiple numbers in message → picks OTP, not amount or reference
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `multiple numbers picks OTP not amount`() {
        val r = parse("OTP for Rs.5000 txn is 472193. Ref:123456789012")
        assertNotNull(r)
        assertEquals("472193", r!!.code)
    }

    @Test
    fun `amount before OTP not mistaken as code`() {
        val r = parse("Use OTP 8821 to authorise Rs.9999 payment")
        assertNotNull(r)
        assertEquals("8821", r!!.code)
    }

    @Test
    fun `multiple numbers - picks number closest to OTP keyword not first`() {
        val r = parse("aapka serial number is 561413 and OTP is 863523")
        assertNotNull(r)
        assertEquals("863523", r!!.code)
    }

    // ══════════════════════════════════════════════════════════════════
    // Null / empty input
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `empty string returns null`() {
        assertNull(parse(""))
    }

    @Test
    fun `blank whitespace returns null`() {
        assertNull(parse("   "))
    }

    @Test
    fun `no matching keyword returns null`() {
        assertNull(parse("Transaction complete. Balance Rs.500"))
    }

    // ══════════════════════════════════════════════════════════════════
    // sourcePackage and notificationTitle pass-through
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `sourcePackage is preserved in result`() {
        val r = OtpParser.parse(
            notificationText = "Your OTP is 472193",
            sourcePackage    = "com.sbi.mobile",
            patterns         = patterns
        )
        assertNotNull(r)
        assertEquals("com.sbi.mobile", r!!.sourcePackage)
    }

    @Test
    fun `notificationTitle used as sourceName on generic match`() {
        val r = OtpParser.parse(
            notificationText  = "Your OTP is 472193",
            notificationTitle = "MyBank Alert",
            patterns          = patterns
        )
        assertNotNull(r)
        assertEquals("MyBank Alert", r!!.sourceName)
    }

    @Test
    fun `sourceName falls back to Unknown when no title`() {
        val r = parse("Your OTP is 472193")
        assertNotNull(r)
        assertEquals("Unknown", r!!.sourceName)
    }

    // ══════════════════════════════════════════════════════════════════
    // Source name extraction — non-Indian / international services
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `source extracted from leading colon - single word`() {
        val r = OtpParser.parse(
            notificationText = "PayPal: Your security code is 847291. Never share it.",
            patterns         = patterns
        )
        assertNotNull(r)
        assertEquals("PayPal", r!!.sourceName)
    }

    @Test
    fun `source extracted from leading colon - two words`() {
        val r = OtpParser.parse(
            notificationText = "Wells Fargo: Your access code is 482911.",
            patterns         = patterns
        )
        assertNotNull(r)
        assertEquals("Wells Fargo", r!!.sourceName)
    }

    @Test
    fun `source extracted from Your X verification pattern`() {
        val r = OtpParser.parse(
            notificationText = "Your Chase Bank verification code is 123456.",
            patterns         = patterns
        )
        assertNotNull(r)
        assertEquals("Chase Bank", r!!.sourceName)
    }

    @Test
    fun `source extracted from Your X single word`() {
        val r = OtpParser.parse(
            notificationText = "Your PayPal security code is 847291. Do not share.",
            patterns         = patterns
        )
        assertNotNull(r)
        assertEquals("PayPal", r!!.sourceName)
    }

    @Test
    fun `source extracted from service before OTP keyword`() {
        val r = OtpParser.parse(
            notificationText = "Amazon OTP: 293847. Valid for 10 minutes.",
            patterns         = patterns
        )
        assertNotNull(r)
        assertEquals("Amazon", r!!.sourceName)
    }

    @Test
    fun `source extracted from code from pattern`() {
        val r = OtpParser.parse(
            notificationText = "Your 6-digit code from Apple is 847291. Do not share.",
            patterns         = patterns
        )
        assertNotNull(r)
        assertEquals("Apple", r!!.sourceName)
    }

    @Test
    fun `phone number title is filtered out and text extraction used`() {
        val r = OtpParser.parse(
            notificationText  = "Amazon: 293847 is your verification code. Do not share.",
            notificationTitle = "+15005550001",
            patterns          = patterns
        )
        assertNotNull(r)
        assertEquals("Amazon", r!!.sourceName)
    }

    @Test
    fun `numeric short code title is filtered - no name in text returns Unknown`() {
        val r = OtpParser.parse(
            notificationText  = "134332 is your verification code.",
            notificationTitle = "74782",
            patterns          = patterns
        )
        assertNotNull(r)
        assertEquals("Unknown", r!!.sourceName)
    }

    @Test
    fun `alphanumeric sender ID title still used as source`() {
        val r = OtpParser.parse(
            notificationText  = "134332 is your verification code.",
            notificationTitle = "PAYPAL",
            patterns          = patterns
        )
        assertNotNull(r)
        assertEquals("PAYPAL", r!!.sourceName)
    }

    // ══════════════════════════════════════════════════════════════════
    // UrgencyLevel
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `default urgency level is HEALTHY`() {
        val r = parse("Your OTP is 472193")
        assertNotNull(r)
        assertEquals(UrgencyLevel.HEALTHY, r!!.urgencyLevel)
    }

    // ══════════════════════════════════════════════════════════════════
    // Confidence
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `specific pattern match has high confidence`() {
        val r = OtpParser.parse(
            notificationText = "SBI OTP 847291",
            patterns         = patterns
        )
        assertNotNull(r)
        assertTrue("Expected confidence >= 0.9, got ${r!!.confidence}", r.confidence >= 0.9f)
    }

    @Test
    fun `generic match has medium confidence`() {
        val r = parse("Your OTP is 472193")
        assertNotNull(r)
        assertTrue("Expected confidence in [0.5, 0.9), got ${r!!.confidence}",
            r.confidence >= 0.5f && r.confidence < 0.9f)
    }

    // ══════════════════════════════════════════════════════════════════
    // UrgencyLevel enum contract
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `HEALTHY and WARNING share clock icon`() {
        assertEquals(UrgencyLevel.HEALTHY.iconDescription, UrgencyLevel.WARNING.iconDescription)
    }

    @Test
    fun `CRITICAL uses warning icon`() {
        assertEquals("warning", UrgencyLevel.CRITICAL.iconDescription)
    }

    @Test
    fun `EXPIRED label is Expired`() {
        assertEquals("Expired", UrgencyLevel.EXPIRED.label)
    }

    // ─────────────────────────────────────────────────────────────────

    private fun parse(text: String) = OtpParser.parse(text, patterns = patterns)
}
