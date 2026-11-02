package com.example.pleoshellocar.shared

import com.example.pleoshellocar.shared.navigation.*
import com.example.pleoshellocar.shared.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable

class TmapNavigationEtaProviderTest {
    private class Fixture {
        var now = 0L
        val callbacks = mutableListOf<(EtaResult) -> Unit>()
        var cancelled = 0
        val provider = TmapNavigationEtaProvider(EtaRequest { _, callback ->
            callbacks.add(callback); Closeable { cancelled++ }
        }, { now })
        init { provider.selectDestination(GeoPoint(37.5, 127.0)); provider.setActive(true) }
        fun real() { provider.setMode(EtaMode.REAL) }
        fun success(seconds: Int = 480) { callbacks.last()(EtaResult.Success(seconds)) }
    }
    @Test fun offlineDefaultNeverCallsNetwork() {
        val f = Fixture(); f.provider.refresh()
        assertEquals(8, f.provider.getEstimatedArrivalMinutes()); assertTrue(f.callbacks.isEmpty())
    }
    @Test fun realRoundsSecondsUpAndRecalculatesRecommendation() {
        val f = Fixture(); f.real(); f.success(481)
        assertEquals(9, f.provider.getEstimatedArrivalMinutes())
        assertEquals(2, OrderTimingCalculator().recommend(f.provider.getEstimatedArrivalMinutes(), 6).waitMinutes)
        f.now = 30_000; f.provider.refresh(); f.success(180)
        assertEquals(Timing.NOW, OrderTimingCalculator().recommend(f.provider.getEstimatedArrivalMinutes(), 6).timing)
    }
    @Test fun everyFailureClearsEtaWithoutMockFallback() {
        for (reason in EtaFailure.entries) {
            val f = Fixture(); f.real(); f.success(); f.now = 30_000; f.provider.refresh()
            f.callbacks.last()(EtaResult.Failure(reason))
            assertNull(f.provider.getEstimatedArrivalMinutes())
            assertEquals(Timing.AFTER_ARRIVAL, OrderTimingCalculator().recommend(f.provider.getEstimatedArrivalMinutes(), 6).timing)
            assertTrue(f.provider.statusLabel.contains(reason.label))
        }
    }
    @Test fun ignoresOldDestinationResponse() {
        val f = Fixture(); f.real(); val old = f.callbacks.last()
        f.provider.selectDestination(GeoPoint(37.6, 127.1)); old(EtaResult.Success(100))
        assertNull(f.provider.getEstimatedArrivalMinutes())
        f.now = 30_000; f.provider.refresh(); f.success(600)
        assertEquals(10, f.provider.getEstimatedArrivalMinutes())
    }
    @Test fun modeSwitchDiscardsInFlightResponseAndPreservesMock() {
        val f = Fixture(); f.real(); f.provider.setMode(EtaMode.MOCK); f.success(600)
        assertEquals(8, f.provider.getEstimatedArrivalMinutes()); assertTrue(f.cancelled > 0)
    }
    @Test fun expiredValuesAreNotUsed() {
        val f = Fixture(); f.real(); f.success(); f.now = 60_000
        assertNull(f.provider.getEstimatedArrivalMinutes())
    }
    @Test fun throttlesRefreshEvenAcrossModeSwitches() {
        val f = Fixture(); f.real(); f.success()
        repeat(10) { f.provider.refresh() }
        f.provider.setMode(EtaMode.MOCK); f.real()
        assertEquals(1, f.callbacks.size)
        f.now = 30_000; f.provider.refresh(); assertEquals(2, f.callbacks.size)
    }
    @Test fun backgroundStopsAndDiscardsCallbacks() {
        val f = Fixture(); f.real(); f.provider.setActive(false); f.success()
        assertNull(f.provider.getEstimatedArrivalMinutes())
        f.now = 30_000; f.provider.refresh(); assertEquals(1, f.callbacks.size)
    }
    @Test fun invalidDurationsFailClosed() {
        for (seconds in listOf(-1, 10801, Int.MAX_VALUE)) {
            val f = Fixture(); f.real(); f.success(seconds)
            assertNull(f.provider.getEstimatedArrivalMinutes())
        }
    }
    @Test fun listenersReceiveChangesAndCanUnsubscribe() {
        val f = Fixture(); var updates = 0; val listener: () -> Unit = { updates++ }
        f.provider.addListener(listener); f.real(); f.success(); assertTrue(updates >= 2)
        f.provider.removeListener(listener); val old = updates; f.provider.setMode(EtaMode.MOCK)
        assertEquals(old, updates)
    }
    @Test fun missingDestinationMakesNoRequest() {
        val f = Fixture(); f.provider.selectDestination(null); f.real()
        assertTrue(f.callbacks.isEmpty()); assertNull(f.provider.getEstimatedArrivalMinutes())
    }
    @Test fun syntheticHttpResponsesAreNotCalledReal() {
        val f = Fixture(); f.real(); f.callbacks.last()(EtaResult.Success(480, true, true))
        assertTrue(f.provider.statusLabel.contains("실 API 아님"))
    }
    @Test fun simulatedLocationIsExplicit() {
        val f = Fixture(); f.real(); f.callbacks.last()(EtaResult.Success(480, true))
        assertTrue(f.provider.statusLabel.contains("모의 위치"))
    }
}
