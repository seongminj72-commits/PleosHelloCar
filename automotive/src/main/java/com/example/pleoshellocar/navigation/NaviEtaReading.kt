package com.example.pleoshellocar.navigation

import com.example.pleoshellocar.shared.navigation.GeoPoint
import kotlin.math.*

internal const val ETA_EXPIRY_MS = 60_000L

internal fun isRouteGuiding(mode: String): Boolean = mode == "ROUTE_GUIDING"

internal fun isEtaExpired(receivedAt: Long, now: Long): Boolean =
    now - receivedAt !in 0 until ETA_EXPIRY_MS

internal fun etaMinutes(seconds: Int): Int? =
    seconds.takeIf { it >= 0 }?.let { ((it.toLong() + 59) / 60).toInt() }

internal const val MAX_CONNECTION_RETRIES = 3

internal fun connectionRetryDelayMs(retryNumber: Int): Long? =
    5_000L.takeIf { retryNumber in 1..MAX_CONNECTION_RETRIES }

internal fun matchesStoreDestination(store: GeoPoint, latitude: Double, longitude: Double): Boolean {
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return false
    val storeLatitude = Math.toRadians(store.latitude)
    val destinationLatitude = Math.toRadians(latitude)
    val longitudeDelta = Math.toRadians(longitude - store.longitude)
    val a = sin((destinationLatitude - storeLatitude) / 2).pow(2) +
        cos(storeLatitude) * cos(destinationLatitude) * sin(longitudeDelta / 2).pow(2)
    val distanceMeters = 2 * 6_371_000 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    return distanceMeters < 300
}
