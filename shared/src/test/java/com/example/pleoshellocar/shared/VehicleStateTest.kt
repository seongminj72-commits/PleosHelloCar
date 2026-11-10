package com.example.pleoshellocar.shared

import com.example.pleoshellocar.shared.vehicle.*
import com.example.pleoshellocar.shared.domain.*
import com.example.pleoshellocar.shared.navigation.MockNavigationEtaProvider
import com.example.pleoshellocar.shared.payment.MockPaymentSimulator
import org.junit.Assert.*
import org.junit.Test

class VehicleStateResolverTest {
    private val resolver = VehicleStateResolver()
    private fun state(gear: VehicleGear, speed: Double?, received: Long? = 100, now: Long = 100,
                      connection: VehicleConnection = VehicleConnection.CONNECTED) =
        resolver.resolve(gear, speed, received, now, connection).state
    @Test fun parkAndZero() { assertEquals(VehicleState.PARKED, state(VehicleGear.P, 0.0)) }
    @Test fun missingSpeed() {
        for (gear in VehicleGear.entries) assertEquals(VehicleState.UNKNOWN, state(gear, null))
    }
    @Test fun conflictingParkAndFive() { assertEquals(VehicleState.UNKNOWN, state(VehicleGear.P, 5.0)) }
    @Test fun driveAndZero() { assertEquals(VehicleState.DRIVING, state(VehicleGear.D, 0.0)) }
    @Test fun reverseAndZero() { assertEquals(VehicleState.DRIVING, state(VehicleGear.R, 0.0)) }
    @Test fun neutralCannotConfirmPark() {
        for (speed in listOf(0.0, 3.0)) assertEquals(VehicleState.UNKNOWN, state(VehicleGear.N, speed))
    }
    @Test fun neutralMovingIsDriving() {
        for (speed in listOf(3.0001, 10.0, 30.0)) assertEquals(VehicleState.DRIVING, state(VehicleGear.N, speed))
    }
    @Test fun speedTenWithoutGear() { assertEquals(VehicleState.DRIVING, state(VehicleGear.UNKNOWN, 10.0)) }
    @Test fun parkedSpeedBoundary() {
        assertEquals(VehicleState.PARKED, state(VehicleGear.P, 1.0))
        assertEquals(VehicleState.UNKNOWN, state(VehicleGear.P, 1.0001))
    }
    @Test fun drivingSpeedBoundary() {
        assertEquals(VehicleState.UNKNOWN, state(VehicleGear.UNKNOWN, 3.0))
        assertEquals(VehicleState.DRIVING, state(VehicleGear.UNKNOWN, 3.0001))
    }
    @Test fun freshnessBoundary() {
        assertEquals(VehicleState.PARKED, state(VehicleGear.P, 0.0, now = 3099))
        assertEquals(VehicleState.UNKNOWN, state(VehicleGear.P, 0.0, now = 3100))
    }
    @Test fun configurableFreshness() {
        assertEquals(VehicleState.UNKNOWN, VehicleStateResolver(500).resolve(VehicleGear.P, 0.0, 0, 500, VehicleConnection.CONNECTED).state)
    }
    @Test fun disconnectedAlwaysUnknown() {
        for (gear in VehicleGear.entries) assertEquals(VehicleState.UNKNOWN, state(gear, 10.0, connection = VehicleConnection.DISCONNECTED))
    }
    @Test fun permissionAndInitializationFailure() {
        for (connection in listOf(VehicleConnection.PERMISSION_DENIED, VehicleConnection.INITIALIZATION_FAILED)) {
            val result = resolver.resolve(VehicleGear.P, 0.0, 100, 100, connection)
            assertEquals(VehicleState.UNKNOWN, result.state)
            assertTrue(result.reason.isNotBlank())
        }
    }
    @Test fun invalidSpeedsNeverPark() {
        for (speed in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY))
            assertEquals(VehicleState.UNKNOWN, state(VehicleGear.P, speed))
    }
    @Test fun invalidTimestamps() {
        for (received in listOf(null, -1L, 101L)) assertEquals(VehicleState.UNKNOWN, state(VehicleGear.P, 0.0, received))
    }
    @Test fun expiredDrivingAlsoUnknown() { assertEquals(VehicleState.UNKNOWN, state(VehicleGear.D, 20.0, now = 3100)) }
    @Test(expected = IllegalArgumentException::class) fun invalidFreshnessRejected() { VehicleStateResolver(0) }
}

class SwitchableVehicleStateProviderTest {
    private class RealProvider : VehicleStateProvider {
        override val source = VehicleStateSource.VEHICLE_SDK
        override val reason = "테스트 신호"
        var state = VehicleState.PARKED
        var fail = false
        val listeners = linkedSetOf<() -> Unit>()
        override fun getVehicleState(): VehicleState { check(!fail); return state }
        override fun addListener(listener: () -> Unit) { listeners.add(listener) }
        override fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
        fun changed() { listeners.toList().forEach { it() } }
    }
    @Test fun defaultsToMockUnknown() {
        val provider = SwitchableVehicleStateProvider()
        assertEquals(VehicleStateMode.MOCK, provider.mode)
        assertEquals(VehicleStateSource.MOCK, provider.source)
        assertEquals(VehicleState.UNKNOWN, provider.getVehicleState())
    }
    @Test fun absentRealNeverLeaksParkedMock() {
        val provider = SwitchableVehicleStateProvider(MockVehicleStateProvider(VehicleState.PARKED))
        provider.setMode(VehicleStateMode.REAL)
        assertEquals(VehicleState.UNKNOWN, provider.getVehicleState())
        assertEquals(VehicleStateSource.VEHICLE_SDK, provider.source)
        assertTrue(provider.reason.contains("SDK 미연동"))
    }
    @Test fun modeChangeNotifiesOnceAndRemoveWorks() {
        val provider = SwitchableVehicleStateProvider()
        var changes = 0; val listener = { changes++; Unit }
        provider.addListener(listener)
        provider.setMode(VehicleStateMode.REAL); provider.setMode(VehicleStateMode.REAL)
        assertEquals(1, changes)
        provider.removeListener(listener); provider.setMode(VehicleStateMode.MOCK)
        assertEquals(1, changes)
    }
    @Test fun realFailureNeverLeaksMockAndRecovers() {
        val real = RealProvider()
        val provider = SwitchableVehicleStateProvider(MockVehicleStateProvider(VehicleState.PARKED), real)
        provider.setMode(VehicleStateMode.REAL); real.fail = true
        assertEquals(VehicleState.UNKNOWN, provider.getVehicleState())
        assertEquals("SDK 연결 실패", provider.reason)
        real.fail = false
        assertEquals(VehicleState.PARKED, provider.getVehicleState())
    }
    @Test fun rejectsMockAsRealProvider() {
        val provider = SwitchableVehicleStateProvider(real = MockVehicleStateProvider(VehicleState.PARKED))
        provider.setMode(VehicleStateMode.REAL)
        assertEquals(VehicleState.UNKNOWN, provider.getVehicleState())
    }
    @Test fun forwardsRealEventsInBothModesAndMockOnlyInMock() {
        val real = RealProvider(); val provider = SwitchableVehicleStateProvider(real = real)
        var changes = 0; provider.addListener { changes++ }
        real.changed(); assertEquals(1, changes)   // MOCK reacts to real changes (DRIVING override)
        provider.cycleMockState(); assertEquals(2, changes)
        provider.setMode(VehicleStateMode.REAL); assertEquals(3, changes)
        provider.mock.state = VehicleState.DRIVING; assertEquals(3, changes)
        real.state = VehicleState.DRIVING; real.changed()
        assertEquals(4, changes); assertEquals(VehicleState.DRIVING, provider.getVehicleState())
    }
    @Test fun realDrivingBlocksReturnToMock() {
        val real = RealProvider().apply { state = VehicleState.DRIVING }
        val provider = SwitchableVehicleStateProvider(MockVehicleStateProvider(VehicleState.PARKED), real)
        provider.setMode(VehicleStateMode.REAL)
        try { provider.setMode(VehicleStateMode.MOCK); fail("REAL DRIVING must not switch to MOCK") }
        catch (_: IllegalStateException) { }
        assertEquals(VehicleStateMode.REAL, provider.mode)
        assertEquals(VehicleState.DRIVING, provider.getVehicleState())
    }
    @Test fun realUnknownBlocksReturnToMock() {
        val real = RealProvider(); val provider = SwitchableVehicleStateProvider(MockVehicleStateProvider(VehicleState.PARKED), real)
        provider.setMode(VehicleStateMode.REAL); real.fail = true
        try { provider.setMode(VehicleStateMode.MOCK); fail("REAL UNKNOWN must not switch to MOCK") }
        catch (_: IllegalStateException) { }
        assertEquals(VehicleStateMode.REAL, provider.mode)
    }
    @Test fun realParkedAllowsReturnToMock() {
        val real = RealProvider(); val provider = SwitchableVehicleStateProvider(real = real)
        provider.setMode(VehicleStateMode.REAL); provider.setMode(VehicleStateMode.MOCK)
        assertEquals(VehicleStateMode.MOCK, provider.mode)
    }
    @Test fun mockParkedCannotUnlockWhileRealDriving() {
        val real = RealProvider().apply { state = VehicleState.DRIVING }
        val provider = SwitchableVehicleStateProvider(MockVehicleStateProvider(VehicleState.PARKED), real)
        assertEquals(VehicleStateMode.MOCK, provider.mode)
        assertEquals(VehicleState.DRIVING, provider.getVehicleState())
        assertTrue(provider.reason.contains("Mock 상태 무시"))
        val flow = DriveFlowController(provider, MockNavigationEtaProvider(), MockPaymentSimulator())
        try { flow.selectStore(flow.stores.stores().first()); fail("real DRIVING must block in MOCK") }
        catch (_: IllegalStateException) { }
        real.state = VehicleState.PARKED
        assertEquals(VehicleState.PARKED, provider.getVehicleState())
    }
    @Test fun mockModeIgnoresNonDrivingOrFailingReal() {
        val real = RealProvider(); val provider = SwitchableVehicleStateProvider(MockVehicleStateProvider(VehicleState.DRIVING), real)
        assertEquals(VehicleState.DRIVING, provider.getVehicleState())   // Mock may restrict while real is PARKED
        provider.mock.state = VehicleState.PARKED; real.fail = true
        assertEquals(VehicleState.PARKED, provider.getVehicleState())    // labelled demo; real failure does not override
    }
    @Test(expected = IllegalStateException::class) fun cannotCycleMockInReal() {
        val provider = SwitchableVehicleStateProvider()
        provider.setMode(VehicleStateMode.REAL); provider.cycleMockState()
    }
    @Test fun explicitReturnToMockRestoresDemoState() {
        val provider = SwitchableVehicleStateProvider()
        provider.cycleMockState(); provider.setMode(VehicleStateMode.REAL); provider.setMode(VehicleStateMode.MOCK)
        assertEquals(VehicleState.PARKED, provider.getVehicleState())
        provider.cycleMockState(); assertEquals(VehicleState.DRIVING, provider.getVehicleState())
        provider.cycleMockState(); assertEquals(VehicleState.UNKNOWN, provider.getVehicleState())
    }
    @Test fun closeDetachesAndFailsClosed() {
        val real = RealProvider(); val provider = SwitchableVehicleStateProvider(real = real)
        var changes = 0; provider.addListener { changes++ }
        provider.close(); provider.close()
        provider.mock.state = VehicleState.PARKED; real.changed()
        assertTrue(real.listeners.isEmpty()); assertEquals(0, changes)
        assertEquals(VehicleState.UNKNOWN, provider.getVehicleState())
    }
    @Test fun listenerRegistrationFailureFailsClosed() {
        val real = object : VehicleStateProvider {
            override val source = VehicleStateSource.VEHICLE_SDK
            override fun getVehicleState() = VehicleState.PARKED
            override fun addListener(listener: () -> Unit) { error("registration failure") }
        }
        val provider = SwitchableVehicleStateProvider(real = real)
        provider.setMode(VehicleStateMode.REAL)
        assertEquals(VehicleState.UNKNOWN, provider.getVehicleState())
    }
    @Test fun mockNotifiesOnlyActualChanges() {
        val mock = MockVehicleStateProvider(); var changes = 0; val listener = { changes++; Unit }
        mock.addListener(listener); mock.state = VehicleState.UNKNOWN; mock.state = VehicleState.PARKED
        assertEquals(1, changes)
        mock.removeListener(listener); mock.state = VehicleState.DRIVING; assertEquals(1, changes)
    }
    @Test fun realUnknownBlocksAllOrderMutations() {
        val provider = SwitchableVehicleStateProvider(MockVehicleStateProvider(VehicleState.PARKED))
        val flow = DriveFlowController(provider, MockNavigationEtaProvider(), MockPaymentSimulator())
        val store = flow.stores.stores().first()
        flow.selectStore(store); flow.setQuantity(store.menu.first(), 1); val order = flow.checkout()
        provider.setMode(VehicleStateMode.REAL)
        val mutations: List<() -> Unit> = listOf(
            { flow.selectStore(flow.stores.stores()[1]) }, { flow.setQuantity(store.menu.first(), 2) },
            { flow.checkout() }, { flow.cancel() }, { flow.advance() })
        for (mutation in mutations) {
            try { mutation(); fail("REAL UNKNOWN must block mutation") } catch (_: IllegalStateException) { }
        }
        assertEquals(order, flow.orders.latest()); assertEquals(1, flow.quantity(store.menu.first().id))
        assertEquals(store, flow.selectedStore)
    }
}
