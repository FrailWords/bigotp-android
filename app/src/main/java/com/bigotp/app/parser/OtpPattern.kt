package com.bigotp.app.parser

import kotlinx.serialization.Serializable

/**
 * A single OTP extraction rule loaded from `patterns_fallback.json` (or the remote CDN).
 *
 * Community contributions live in `app/src/main/assets/patterns_fallback.json`.
 * See `CONTRIBUTING.md` for the full pattern authoring guide.
 *
 * @property id             Unique kebab-case identifier (e.g. `"hdfc-payment"`).
 * @property sourceKeywords Substrings matched against the SMS sender ID (case-insensitive).
 *                          Keep these specific — overly broad keywords increase false positives.
 * @property otpRegex       Java regex where **capturing group 1** is the OTP code.
 * @property type           [OtpType.LOGIN] or [OtpType.PAYMENT]. The parser may promote LOGIN
 *                          to PAYMENT when it detects payment keywords and an amount.
 * @property amountRegex    Optional regex (payment patterns only). Capturing group 1 = numeric
 *                          amount without currency symbol.
 * @property friendlyName   Human-readable label shown in the app. Defaults to the first keyword.
 */
@Serializable
data class OtpPattern(
    val id: String,
    val sourceKeywords: List<String>,
    val otpRegex: String,
    val type: OtpType,
    val amountRegex: String? = null,
    val friendlyName: String? = null
)
