package com.example.pleoshellocar.navigation

internal const val ETA_EXPIRY_MS = 60_000L

internal fun isRouteGuiding(mode: String): Boolean = mode == "ROUTE_GUIDING"

internal fun isEtaExpired(receivedAt: Long, now: Long): Boolean =
    now - receivedAt !in 0 until ETA_EXPIRY_MS

internal fun etaMinutes(seconds: Int): Int? =
    seconds.takeIf { it >= 0 }?.let { ((it.toLong() + 59) / 60).toInt() }

internal const val MAX_CONNECTION_RETRIES = 3

internal fun connectionRetryDelayMs(retryNumber: Int): Long? =
    5_000L.takeIf { retryNumber in 1..MAX_CONNECTION_RETRIES }
