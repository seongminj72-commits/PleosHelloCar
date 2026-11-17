package com.example.pleoshellocar.navigation

import com.example.pleoshellocar.shared.navigation.GeoPoint
import org.junit.Assert.*
import org.junit.Test

class NaviEtaReadingTest {
    @Test fun roundsUpRemainingSeconds() {
        assertNull(etaMinutes(-1))
        assertEquals(0, etaMinutes(0))
        assertEquals(1, etaMinutes(1))
        assertEquals(1, etaMinutes(60))
        assertEquals(2, etaMinutes(61))
        assertEquals(35_791_395, etaMinutes(Int.MAX_VALUE))
    }

    @Test fun expiresAtSixtySecondsAndRejectsFutureTimestamps() {
        assertFalse(isEtaExpired(100, 60_099))
        assertTrue(isEtaExpired(100, 60_100))
        assertTrue(isEtaExpired(100, 99))
    }

    @Test fun acceptsOnlyActiveRouteGuidance() {
        assertTrue(isRouteGuiding("ROUTE_GUIDING"))
        assertFalse(isRouteGuiding("SAFE_GUIDING"))
        assertFalse(isRouteGuiding("UNKNOWN"))
    }
    @Test fun schedulesOnlyThreeConnectionRetriesAtFiveSecondIntervals() {
        assertNull(connectionRetryDelayMs(0))
        assertEquals(5_000L, connectionRetryDelayMs(1))
        assertEquals(5_000L, connectionRetryDelayMs(2))
        assertEquals(5_000L, connectionRetryDelayMs(3))
        assertNull(connectionRetryDelayMs(4))
    }

    @Test fun comparesDestinationCoordinatesAgainstThreeHundredMeters() {
        val store = GeoPoint(37.5665, 126.9780)
        assertTrue(matchesStoreDestination(store, store.latitude, store.longitude))
        assertTrue(matchesStoreDestination(store, 37.5691, store.longitude))
        assertFalse(matchesStoreDestination(store, 37.5693, store.longitude))
        assertFalse(matchesStoreDestination(store, Double.NaN, store.longitude))
        assertTrue(matchesStoreDestination(GeoPoint(0.0, 179.9999), 0.0, -179.9999))
    }
}
