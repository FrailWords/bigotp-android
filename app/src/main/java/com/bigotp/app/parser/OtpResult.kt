package com.bigotp.app.parser

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class OtpResult(
    val code: String,
    val type: OtpType,
    val sourceName: String,
    val sourcePackage: String? = null,
    val amountString: String? = null,
    val rawMessage: String,
    val confidence: Float,
    val urgencyLevel: UrgencyLevel = UrgencyLevel.HEALTHY
) : Parcelable
