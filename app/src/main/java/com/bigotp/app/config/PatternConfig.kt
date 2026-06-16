package com.bigotp.app.config

import com.bigotp.app.parser.OtpPattern
import kotlinx.serialization.Serializable

@Serializable
data class PatternConfig(
    val version: Int,
    val patterns: List<OtpPattern>
)
