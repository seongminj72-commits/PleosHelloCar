package com.example.pleoshellocar.shared

import org.junit.Assert.*
import org.junit.Test
import com.example.pleoshellocar.shared.domain.*
import com.example.pleoshellocar.shared.data.*
import com.example.pleoshellocar.shared.vehicle.*
import com.example.pleoshellocar.shared.navigation.*
import com.example.pleoshellocar.shared.payment.*

class DriveFlowTest {
    private val calculator = OrderTimingCalculator()
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) { } catch (_: IllegalStateException) { }
    }
    private fun controller(): DriveFlowController {
        val c = DriveFlowController(MockVehicleStateProvider(VehicleState.PARKED), MockNavigationEtaProvider(), MockPaymentSimulator())
        c.selectStore(c.stores.stores().first()); c.setQuantity(c.selectedStore!!.menu.first(), 1)
        return c
    }
    @Test fun eightSixOneRecommendsOneMinute() { assertEquals(1, calculator.recommend(8,6).waitMinutes) }
    @Test fun tenToThreeChangesRecommendation() {
        assertEquals(Timing.LATER, calculator.recommend(10,5).timing)
        assertEquals(Timing.NOW, calculator.recommend(3,5).timing)
    }
    @Test fun delaysBringRecommendationForward() { assertEquals(Timing.NOW, calculator.recommend(8,6, possibleDelay=2).timing) }
    @Test fun arrivalAndMinimumProcessingBoundary() {
        for (eta in 0..1) assertEquals(Timing.AFTER_ARRIVAL, calculator.recommend(eta,5).timing)
        assertEquals(Timing.NOW, calculator.recommend(2,0).timing)
        assertEquals(Timing.AFTER_ARRIVAL, calculator.recommend(3,5,minimumProcessing=3).timing)
    }
    @Test fun unavailableAndInvalidEta() {
        for (eta in listOf(null,-1,181,Int.MAX_VALUE,Int.MIN_VALUE))
            assertEquals(Timing.AFTER_ARRIVAL, calculator.recommend(eta,5).timing)
    }
    @Test fun invalidOtherInputs() {
        assertEquals(Timing.AFTER_ARRIVAL, calculator.recommend(8,-1).timing)
        assertEquals(Timing.AFTER_ARRIVAL, calculator.recommend(8,121).timing)
        assertEquals(Timing.AFTER_ARRIVAL, calculator.recommend(8,5,safety=-1).timing)
        assertEquals(Timing.AFTER_ARRIVAL, calculator.recommend(8,5,minimumProcessing=0).timing)
        assertEquals(Timing.AFTER_ARRIVAL, calculator.recommend(8,5,possibleDelay=Int.MAX_VALUE).timing)
    }
    @Test fun defaultVehicleStateIsUnknown() { assertEquals(VehicleState.UNKNOWN, MockVehicleStateProvider().getVehicleState()) }
    @Test fun unknownAndDrivingBlockEveryMutation() {
        for(state in listOf(VehicleState.UNKNOWN,VehicleState.DRIVING)) {
            val c=controller(); c.checkout(); (c.vehicle as MockVehicleStateProvider).state=state
            rejected { c.setQuantity(c.selectedStore!!.menu.first(),2) }; rejected { c.checkout() }
            rejected { c.cancel() }; rejected { c.advance() }; rejected { c.selectStore(c.stores.stores()[1]) }
            assertEquals(OrderStatus.PAYMENT_SIMULATED,c.orders.latest()!!.status)
        }
    }
    @Test fun unavailableStoreRejected() { val c=controller(); rejected { c.selectStore(c.stores.stores()[2]) } }
    @Test fun zeroQuantityCannotOrder() {
        val c=controller(); c.setQuantity(c.selectedStore!!.menu.first(),0); rejected { c.checkout() }
        assertNull(c.orders.latest())
    }
    @Test fun quantityAndTotal() {
        val c=controller(); val menu=c.selectedStore!!.menu
        c.setQuantity(menu[0],2); c.setQuantity(menu[1],3); assertEquals(20500,c.totalWon())
        rejected { c.setQuantity(menu[0],4) }; rejected { c.setQuantity(menu[0],-1) }
    }
    @Test fun paymentFailureAndRetryUseSameOrder() {
        val c=controller(); (c.payment as MockPaymentSimulator).failNext=true
        rejected { c.checkout() }; val id=c.orders.latest()!!.id
        assertEquals(OrderStatus.ORDER_CREATED,c.orders.latest()!!.status)
        assertEquals(id,c.checkout().id); assertEquals(OrderStatus.PAYMENT_SIMULATED,c.orders.latest()!!.status)
    }
    @Test fun doubleCheckoutIsIdempotent() { val c=controller(); assertEquals(c.checkout(),c.checkout()) }
    @Test fun activeOrderBlocksNewCart() {
        val c=controller(); c.checkout(); rejected { c.selectStore(c.stores.stores()[1]) }
        rejected { c.setQuantity(c.selectedStore!!.menu[0],2) }
    }
    @Test fun happyPathAndTerminalProtection() {
        val c=controller(); c.checkout()
        assertEquals(OrderStatus.PREPARING,c.advance().status)
        assertEquals(OrderStatus.READY_FOR_PICKUP,c.advance().status)
        assertEquals(OrderStatus.COMPLETED,c.advance().status)
        rejected { c.advance() }; rejected { c.cancel() }; rejected { c.checkout() }
        c.selectStore(c.stores.stores()[0]); c.setQuantity(c.selectedStore!!.menu[0],1)
        assertEquals(OrderStatus.PAYMENT_SIMULATED,c.checkout().status)
    }
    @Test fun cancelledOrderCannotBePaidOrAdvanced() {
        val c=controller(); (c.payment as MockPaymentSimulator).failNext=true; rejected { c.checkout() }
        assertEquals(OrderStatus.CANCELLED,c.cancel().status); rejected { c.checkout() }; rejected { c.advance() }
    }
    @Test fun invalidStateTransitionRejected() {
        val c=controller(); val o=c.checkout()
        rejected { c.orders.transition(o.id,OrderStatus.COMPLETED) }
        rejected { c.orders.transition(o.id,OrderStatus.ORDER_CREATED) }
    }
    @Test fun allStateEdgesValidated() {
        val paths=mapOf(
            OrderStatus.ORDER_CREATED to emptyList(),
            OrderStatus.PAYMENT_SIMULATED to listOf(OrderStatus.PAYMENT_SIMULATED),
            OrderStatus.PREPARING to listOf(OrderStatus.PAYMENT_SIMULATED,OrderStatus.PREPARING),
            OrderStatus.READY_FOR_PICKUP to listOf(OrderStatus.PAYMENT_SIMULATED,OrderStatus.PREPARING,OrderStatus.READY_FOR_PICKUP),
            OrderStatus.COMPLETED to listOf(OrderStatus.PAYMENT_SIMULATED,OrderStatus.PREPARING,OrderStatus.READY_FOR_PICKUP,OrderStatus.COMPLETED),
            OrderStatus.CANCELLED to listOf(OrderStatus.CANCELLED))
        val allowed=mapOf(OrderStatus.ORDER_CREATED to setOf(OrderStatus.PAYMENT_SIMULATED,OrderStatus.CANCELLED),
            OrderStatus.PAYMENT_SIMULATED to setOf(OrderStatus.PREPARING,OrderStatus.CANCELLED),
            OrderStatus.PREPARING to setOf(OrderStatus.READY_FOR_PICKUP,OrderStatus.CANCELLED),
            OrderStatus.READY_FOR_PICKUP to setOf(OrderStatus.COMPLETED,OrderStatus.CANCELLED))
        for ((state,path) in paths) for(next in OrderStatus.entries) {
            val r=MockOrderRepository(); val s=MockStoreRepository().stores()[0]
            val o=r.create("r",s,listOf(OrderLine(s.menu[0],1))); path.forEach { r.transition(o.id,it) }
            if(next in allowed[state].orEmpty()) assertEquals(next,r.transition(o.id,next).status)
            else rejected { r.transition(o.id,next) }
        }
    }
    @Test fun repositoryRejectsDuplicateRequestWithChangedPayload() {
        val r=MockOrderRepository(); val s=MockStoreRepository().stores()[0]
        r.create("same",s,listOf(OrderLine(s.menu[0],1)))
        rejected { r.create("same",s,listOf(OrderLine(s.menu[0],2))) }
    }
    @Test fun repositoryRejectsForgedMenuAndSecondRequest() {
        val r=MockOrderRepository(); val s=MockStoreRepository().stores()[0]
        rejected { r.create("r",s,listOf(OrderLine(s.menu[0].copy(priceWon=1),1))) }
        r.create("r",s,listOf(OrderLine(s.menu[0],1)))
        rejected { r.create("another",s,listOf(OrderLine(s.menu[0],1))) }
    }
}
