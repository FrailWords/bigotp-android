package com.bigotp.app.parser


object OtpParser {

    // Matches "Rs.", "Rs", "₹", or "INR" at the end of the before-context string,
    // indicating the candidate code is an amount, not an OTP.
    private val AMOUNT_PREFIX = Regex("""(?:Rs\.?|₹|INR)\s*$""")

    // Matches "DD/MM/" at the end of the before-context string.
    private val DATE_SLASH_CONTEXT = Regex("""\d{1,2}/\d{1,2}/$""")

    // Matches common reference-number labels at the end of the before-context string.
    private val REFERENCE_LABEL = Regex(
        """(?:ref(?:erence)?|txn(?:\s*id)?|transaction\s*id|utr)\s*:?\s*$""",
        RegexOption.IGNORE_CASE
    )

    private val PHONE_NUMBER = Regex("""^[6-9]\d{9}$""")
    private val ALL_DIGITS   = Regex("""\d+""")

    // Notification titles that are phone numbers or short codes — not useful as source names.
    private val TITLE_IS_NUMERIC = Regex("""^[\d\s()+\-.]{4,}$""")

    // First captured word must not be one of these pseudo-service-name words.
    private val NOT_SERVICE_FIRST = setOf("OTP", "PIN", "SMS", "The", "Your")

    // Keywords used to score proximity: the candidate closest to one of these
    // is the most likely OTP, not simply the first number in the message.
    private val OTP_PROXIMITY_KEYWORDS = listOf(
        "otp", "one-time", "one time", "code", "passcode", "password", "pin", "verification"
    )

    private val PAYMENT_KEYWORDS  = listOf("payment", "transaction", "purchase", "debit", "transfer")
    private val DEFAULT_AMOUNT_RE = """(?:Rs\.?|₹)\s*(\d[\d,]*)"""

    // ─────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────

    fun parse(
        notificationText: String,
        notificationTitle: String? = null,
        sourcePackage: String? = null,
        patterns: List<OtpPattern>
    ): OtpResult? {
        if (notificationText.isBlank()) return null

        val searchText = buildString {
            append(notificationText)
            notificationTitle?.let { append(' ').append(it) }
            sourcePackage?.let { append(' ').append(it) }
        }

        // Keyword-matched patterns; must have at least one for us to proceed.
        val matchingPatterns = patterns.filter { pattern ->
            pattern.sourceKeywords.any { kw -> searchText.contains(kw, ignoreCase = true) }
        }
        if (matchingPatterns.isEmpty()) return null

        // Normalize text before matching:
        // 1. Collapse spaced digit groups ("47 21 93" → "472193")
        // 2. Insert a space where 4+ digits run directly into letters ("706371Thanks" → "706371 Thanks")
        //    so that the word-boundary anchors in OTP regexes fire correctly.
        val normalizedText = normalizeCodeBoundaries(normalizeSpacedDigits(notificationText))

        // Non-generic patterns before generic so brand-specific rules win.
        val sortedPatterns = matchingPatterns.sortedBy { if (it.id == "generic") 1 else 0 }

        for (pattern in sortedPatterns) {
            val regex = try { Regex(pattern.otpRegex) } catch (_: Exception) { continue }

            val candidates = regex.findAll(normalizedText)
                .mapNotNull { match ->
                    val code = if (match.groups.size > 1) match.groupValues[1] else match.value
                    val pos  = (if (match.groups.size > 1) match.groups[1]?.range?.first else null)
                               ?: match.range.first
                    if (isValidCode(code, normalizedText, match.range.first)) code to pos else null
                }
                .toList()
            if (candidates.isEmpty()) continue
            val validCode = if (candidates.size == 1) {
                candidates[0].first
            } else {
                candidates.minByOrNull { (_, pos) ->
                    distanceToNearestKeyword(normalizedText, pos)
                }!!.first
            }

            val isGeneric = pattern.id == "generic"

            val confidence = when {
                !isGeneric && validCode.matches(ALL_DIGITS) -> 0.95f
                !isGeneric                                  -> 0.85f
                else                                        -> 0.75f
            }

            // Promote generic LOGIN → PAYMENT when message carries both a payment
            // keyword and an amount expression.
            val lowerText = notificationText.lowercase()
            val effectiveType = when {
                pattern.type == OtpType.PAYMENT -> OtpType.PAYMENT
                isGeneric
                    && PAYMENT_KEYWORDS.any { lowerText.contains(it) }
                    && Regex(DEFAULT_AMOUNT_RE, RegexOption.IGNORE_CASE)
                        .containsMatchIn(notificationText) -> OtpType.PAYMENT
                else -> pattern.type
            }

            val amountString = if (effectiveType == OtpType.PAYMENT) {
                extractAmount(notificationText, pattern.amountRegex ?: DEFAULT_AMOUNT_RE)
            } else null

            val sourceName = when {
                !isGeneric && pattern.friendlyName != null -> pattern.friendlyName
                !isGeneric -> pattern.sourceKeywords.first()
                else -> {
                    // For generic matches: prefer the notification title when it looks like a
                    // real sender name, then fall back to text extraction, then "Unknown".
                    // Filter out phone numbers / short codes (TITLE_IS_NUMERIC) so they don't
                    // show up as "9876543210" or "+14155551234".
                    val titleUsable = notificationTitle != null
                        && !looksLikeContactName(notificationTitle)
                        && !TITLE_IS_NUMERIC.matches(notificationTitle)
                    if (titleUsable) {
                        notificationTitle!!
                    } else {
                        extractServiceNameFromText(notificationText) ?: "Unknown"
                    }
                }
            }

            return OtpResult(
                code          = validCode,
                type          = effectiveType,
                sourceName    = sourceName,
                sourcePackage = sourcePackage,
                amountString  = amountString,
                rawMessage    = notificationText,
                confidence    = confidence
            )
        }

        return null
    }

    // ─────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────

    /**
     * Tries to infer a human-readable service/brand name from the raw notification body.
     *
     * All capture groups use bare `[A-Z]` (no IGNORE_CASE) so they only match Title-Case
     * words.  Common OTP keywords — "verification", "security", "one-time" — begin with a
     * lowercase letter and naturally stop the capture; no exclusion list is needed.
     *
     * Returns null when no name can be found with reasonable confidence.
     */
    private fun extractServiceNameFromText(text: String): String? {
        // Trailing OTP keywords that senders sometimes bolt onto their name, e.g. "Amazon OTP:"
        val OTP_SUFFIXES = setOf("OTP", "CODE", "PIN", "SMS")

        // 1. "ServiceName: rest of message" — most reliable; used by PayPal, Apple, Google, etc.
        Regex("""^([A-Z][A-Za-z0-9 &.'()-]{1,35})\s*:""").find(text)?.let { m ->
            val raw   = m.groupValues[1].trim()
            val words = raw.split(Regex("""\s+"""))
            val name  = words.dropLastWhile { it.uppercase() in OTP_SUFFIXES }.joinToString(" ").ifEmpty { raw }
            if (name.split(' ').first() !in NOT_SERVICE_FIRST) return name
        }

        // 2. "Your [Title-Case words] <lowercase-trigger>"
        //    No IGNORE_CASE — [A-Z] only matches uppercase-first words, so "security",
        //    "verification", and "one-time" won't be captured even though they appear right
        //    after the service name.  Trigger list covers common casing variants explicitly.
        Regex(
            """\bYour\s+((?:[A-Z][A-Za-z0-9.'()-]*\s+){1,3})""" +
            """(?:verification|security|access|OTP|otp|account|code|Code|login|passcode|one.time)\b"""
        ).find(text)?.let { m ->
            val name = m.groupValues[1].trim()
            if (name.isNotEmpty() && name.split(' ').first() !in NOT_SERVICE_FIRST) return name
        }

        // 3. "[Title-Case words] OTP|one-time|verification code|security code|access code"
        //    e.g. "Google verification code", "Wells Fargo one-time passcode"
        Regex(
            """\b((?:[A-Z][A-Za-z0-9.'()-]+\s+){1,2})""" +
            """(?:OTP|otp|one.time|verification code|Verification Code|security code|access code)\b"""
        ).find(text)?.let { m ->
            val name = m.groupValues[1].trim()
            if (name.split(' ').first() !in NOT_SERVICE_FIRST) return name
        }

        // 4. "code/OTP from [Title-Case service name]"
        //    e.g. "Your 6-digit code from Apple is 847291"
        //    No IGNORE_CASE — [A-Z] ensures we don't accidentally capture "is", "the", etc.
        Regex(
            """\b(?:code|Code|OTP|otp)\s+from\s+([A-Z][A-Za-z0-9.'()-]+(?:\s+[A-Z][A-Za-z0-9.'()-]+){0,2})\b"""
        ).find(text)?.let { m ->
            return m.groupValues[1].trim()
        }

        return null
    }

    /**
     * Returns the minimum character gap between [codePos] and any occurrence of any
     * OTP proximity keyword in [text].  Used to prefer the candidate closest to "OTP",
     * "code", etc. rather than the first number found in the message.
     */
    private fun distanceToNearestKeyword(text: String, codePos: Int): Int {
        val lower = text.lowercase()
        var best  = Int.MAX_VALUE / 2
        for (kw in OTP_PROXIMITY_KEYWORDS) {
            var idx = lower.indexOf(kw)
            while (idx >= 0) {
                val dist = minOf(
                    kotlin.math.abs(codePos - idx),
                    kotlin.math.abs(codePos - (idx + kw.length))
                )
                if (dist < best) best = dist
                idx = lower.indexOf(kw, idx + 1)
            }
        }
        return best
    }

    private fun isValidCode(code: String, fullText: String, matchStart: Int): Boolean {
        // Indian mobile numbers: 10 digits starting with 6–9.
        if (PHONE_NUMBER.matches(code)) return false

        // Transaction / reference IDs: 12+ digit strings.
        if (code.length >= 12 && code.matches(Regex("""\d+"""))) return false

        // Purely alphabetic strings are words, not OTP codes.
        if (code.all { it.isLetter() }) return false

        val before = fullText.substring(0, matchStart)
        if (AMOUNT_PREFIX.containsMatchIn(before))      return false
        if (DATE_SLASH_CONTEXT.containsMatchIn(before)) return false
        if (REFERENCE_LABEL.containsMatchIn(before))    return false

        return true
    }

    /**
     * Collapses whitespace-separated 2–3 digit groups into a single code when the
     * total digit count falls in [4, 8].  e.g. "47 21 93" → "472193".
     */
    private fun normalizeSpacedDigits(text: String): String =
        Regex("""\b(\d{2,3})(\s+\d{2,3})+\b""").replace(text) { match ->
            val collapsed = match.value.replace(Regex("""\s+"""), "")
            if (collapsed.length in 4..8) collapsed else match.value
        }

    /**
     * Inserts a space where a run of 4+ digits immediately meets a letter (or vice versa),
     * so that word-boundary anchors work correctly on malformed SMS like "706371Thanks".
     * Short digit segments (< 4) in alphanumeric codes like "A8X2K1" are unaffected.
     */
    private fun normalizeCodeBoundaries(text: String): String =
        Regex("""(\d{4,})([A-Za-z])""").replace(
            Regex("""([A-Za-z])(\d{4,})""").replace(text, "$1 $2"),
            "$1 $2"
        )

    // Single mixed-case word consisting entirely of letters → likely a personal contact name
    // (e.g. "Sriram"). Sender IDs are all-caps ("HDFC", "VM-SBIINB") or multi-word.
    private fun looksLikeContactName(title: String): Boolean {
        if (title.contains(' ') || title.any { !it.isLetter() }) return false
        if (title == title.uppercase()) return false
        return true
    }

    private fun extractAmount(text: String, amountRegex: String): String? = try {
        val match = Regex(amountRegex, RegexOption.IGNORE_CASE).find(text) ?: return null
        val raw    = if (match.groups.size > 1) match.groupValues[1] else match.value
        val amount = raw.replace(",", "").toLongOrNull() ?: return null
        formatIndianCurrency(amount)
    } catch (_: Exception) { null }

    // Indian number system: ₹1,00,000 not ₹100,000
    private fun formatIndianCurrency(amount: Long): String {
        val str = amount.toString()
        if (str.length <= 3) return "₹$str"
        val lastThree = str.takeLast(3)
        val rest      = str.dropLast(3)
        val grouped   = rest.reversed().chunked(2).joinToString(",").reversed()
        return "₹$grouped,$lastThree"
    }
}
