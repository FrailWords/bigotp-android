package com.bigotp.app.parser

enum class UrgencyLevel(
    val label: String,
    val iconDescription: String,
    val accessibilityHint: String
) {
    HEALTHY ("remaining",    "clock",   "Time remaining"),
    WARNING ("remaining",    "clock",   "Expiring soon"),
    CRITICAL("Expires soon", "warning", "Code expiring imminently"),
    EXPIRED ("Expired",      "expired", "Code has expired")
}
