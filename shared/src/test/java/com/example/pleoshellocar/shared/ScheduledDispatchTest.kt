package com.example.pleoshellocar.shared

import com.example.pleoshellocar.shared.data.MockStoreRepository
import com.example.pleoshellocar.shared.domain.*
import com.example.pleoshellocar.shared.navigation.EtaSource
import com.example.pleoshellocar.shared.navigation.MockNavigationEtaProvider
import com.example.pleoshellocar.shared.payment.MockPaymentSimulator
import com.example.pleoshellocar.shared.vehicle.MockVehicleStateProvider
import com.example.pleoshellocar.shared.vehicle.VehicleState
import org.junit.Assert.*
import org.junit.Test

class ScheduledDispatchTest {
    private val store = MockStoreRepository().stores().first()
    private val preparation = PreparationEstimate(6, 0, 0)

    private fun reservation(
        eta: DispatchEta,
        source: EtaSource = EtaSource.NAVIGATION,
        preparation: PreparationEstimate = this.preparation
    ) = ScheduledOrder(
        "request", store, listOf(OrderLine(store.menu.first(), 1)), preparation,
        source, eta.receivedAt, eta.arrivalAt, eta
    )

    @Test fun sendsAtExactSafetyMargin() {
        assertEquals(DispatchDecision.SEND, decideDispatch(reservation(DispatchEta(7, 0)), 0))
        assertEquals(DispatchDecision.WAIT, decideDispatch(reservation(DispatchEta(8, 0)), 0))
    }

    @Test fun customLeadUsesArrivalTimeWithoutChangingDefault() {
        val original = reservation(DispatchEta(20, 0), EtaSource.MOCK)
        val custom = original.copy(customLeadMinutes = 10)
        assertEquals(780_000L, original.dispatchAt)
        assertEquals(600_000L, custom.dispatchAt)
        assertEquals(DispatchDecision.WAIT, decideDispatch(custom, 599_999))
        assertEquals(DispatchDecision.SEND, decideDispatch(custom, 600_000))
    }

    @Test fun mockReservationUsesTheSameClockReadingForEtaReceipt() {
        var elapsed = 10_000L
        val repository = ScheduledOrderRepository()
        val flow = DriveFlowController(
            vehicle = MockVehicleStateProvider(VehicleState.PARKED),
            navigation = MockNavigationEtaProvider(),
            payment = MockPaymentSimulator(),
            scheduledOrders = repository,
            dispatchEta = { now -> DispatchEta(8, now) },
            clock = { ++elapsed }
        )
        flow.selectStore(store)
        flow.setQuantity(store.menu.first(), 1)

        flow.reserveDispatch()

        val reserved = requireNotNull(repository.current)
        assertEquals(reserved.createdAt, reserved.eta?.receivedAt)
        assertTrue(reserved.eta!!.fresh(reserved.createdAt))
    }

    @Test fun freshnessAllowsAtMostOneSecondOfFutureClockSkew() {
        assertTrue(DispatchEta(8, receivedAt = 10_500L).fresh(now = 10_000L))
        assertFalse(DispatchEta(8, receivedAt = 12_000L).fresh(now = 10_000L))
    }

    @Test fun mockEtaDispatchesAtAbsoluteArrivalDeadline() {
        val order = reservation(DispatchEta(8, 0), EtaSource.MOCK)
        assertEquals(60_000L, order.dispatchAt)
        assertEquals(DispatchDecision.WAIT, decideDispatch(order, 59_999))
        assertEquals(DispatchDecision.SEND, decideDispatch(order, 60_000))
        val state = ScheduledOrderRepository()
        state.reserve(order)
        state.tick(60_000, order.eta) {}
        assertEquals(DispatchStatus.SENT, state.current?.status)
    }

    @Test fun mockDoesNotHoldOnAStaleSampleButNavigationDoes() {
        val eta = DispatchEta(8, 0)
        val mock = reservation(eta, EtaSource.MOCK, PreparationEstimate(0, 0, 0))
        val navigation = reservation(eta, EtaSource.NAVIGATION, PreparationEstimate(0, 0, 0))
        assertEquals(DispatchDecision.WAIT, decideDispatch(mock, 180_001))
        assertEquals(DispatchDecision.HOLD, decideDispatch(navigation, 180_000))
    }

    @Test fun delayUsesCeilingOfRemainingArrivalTime() {
        val eta = DispatchEta(8, 0)
        val order = reservation(eta)
        assertEquals(6, remainingArrivalMinutes(order, order.arrivalAt - 300_001))
        assertEquals(5, remainingArrivalMinutes(order, order.arrivalAt - 300_000))
        assertEquals(1, latePreparationMinutes(order, order.arrivalAt - 300_000))
        assertEquals(2, latePreparationMinutes(order, order.arrivalAt - 240_000))
    }

    @Test fun etaIncreaseDelaysAndDecreaseDispatchesWithLateWarning() {
        val state = ScheduledOrderRepository()
        val notes = mutableListOf<String>()
        state.reserve(reservation(DispatchEta(7, 0)))
        state.tick(1_000, DispatchEta(30, 1_000), notes::add)
        assertEquals(DispatchStatus.SCHEDULED, state.current?.status)
        state.tick(2_000, DispatchEta(5, 2_000), notes::add)
        assertEquals(DispatchStatus.SENT, state.current?.status)
        assertTrue(notes.single().contains("약 1분 늦게"))
    }

    @Test fun staleEtaBoundaryAndSingleHoldNotice() {
        val order = reservation(DispatchEta(8, 0))
        assertEquals(DispatchDecision.SEND, decideDispatch(order, 179_999))
        assertEquals(DispatchDecision.HOLD, decideDispatch(order, 180_000))
        val state = ScheduledOrderRepository()
        state.reserve(order)
        val notes = mutableListOf<String>()
        state.tick(180_000, order.eta, notes::add)
        state.tick(181_000, null, notes::add)
        assertEquals(1, notes.size)
        assertNull(state.orders.latest())
    }

    @Test fun missedDeadlineDuringRetryPreventsOrder() {
        val state = ScheduledOrderRepository()
        val order = reservation(DispatchEta(1, 0))
        state.reserve(order)
        state.orders.dispatchOffline = true
        state.tick(59_000, order.eta) {}
        assertEquals(64_000L, state.current?.nextAttemptAt)
        state.orders.dispatchOffline = false
        state.tick(60_000, DispatchEta(7, 60_000)) {}
        assertEquals(DispatchStatus.FAILED, state.current?.status)
        assertNull(state.orders.latest())
    }

    @Test fun retriesAtFiveTenAndTwentySecondsAndSendsOnce() {
        val state = ScheduledOrderRepository()
        val order = reservation(DispatchEta(5, 0))
        state.reserve(order)
        state.orders.dispatchOffline = true
        listOf(0L to 5_000L, 5_000L to 15_000L, 15_000L to 35_000L).forEach { (now, next) ->
            state.tick(now, order.eta) {}
            assertEquals(next, state.current?.nextAttemptAt)
        }
        state.orders.dispatchOffline = false
        state.tick(34_999, order.eta) {}
        assertNull(state.orders.latest())
        state.tick(35_000, order.eta) {}
        val sent = checkNotNull(state.orders.latest())
        state.tick(36_000, order.eta) {}
        assertEquals(sent, state.orders.dispatch(order.requestId, order.store, order.lines))
        assertEquals(DispatchStatus.SENT, state.current?.status)
    }

    @Test fun demoChangesEtaAtTwentyFiveSecondsAndKeepsExactDeadline() {
        assertEquals(30, DispatchDemoEta.DROP.sample(1_000, 25_999)?.minutes)
        assertEquals(5, DispatchDemoEta.DROP.sample(1_000, 26_000)?.minutes)
        assertEquals(10, DispatchDemoEta.RISE.sample(1_000, 25_999)?.minutes)
        assertEquals(30, DispatchDemoEta.RISE.sample(1_000, 26_000)?.minutes)
        assertEquals(26_000L, DispatchDemoEta.DEADLINE.sample(1_000, 1_000)?.arrivalAt)
        assertNull(DispatchDemoEta.OFF.sample(1_000, 26_000))
    }

    @Test fun cancelledReservationNeverDispatches() {
        val state = ScheduledOrderRepository()
        val order = reservation(DispatchEta(5, 0))
        state.reserve(order)
        state.cancel()
        state.tick(0, order.eta) {}
        assertEquals(DispatchStatus.CANCELLED, state.current?.status)
        assertNull(state.orders.latest())
    }
}
