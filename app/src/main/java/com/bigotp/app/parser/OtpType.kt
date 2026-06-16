package com.bigotp.app.parser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class OtpType {
    @SerialName("payment") PAYMENT,
    @SerialName("login")   LOGIN,
    @SerialName("unknown") UNKNOWN
}
