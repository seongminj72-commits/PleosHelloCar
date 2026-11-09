package com.example.pleoshellocar.shared

import org.junit.Assert.*
import org.junit.Test
import com.example.pleoshellocar.shared.domain.*
import com.example.pleoshellocar.shared.data.*
import com.example.pleoshellocar.shared.vehicle.*
import com.example.pleoshellocar.shared.navigation.*
import com.example.pleoshellocar.shared.payment.*

/** 메뉴별 준비시간 반영과 사용자용 추천 문구 검증. 메뉴 순서: 아메리카노(0), 카페라떼(+1), 바닐라라떼(+2), 콜드브루(0). */
class RecommendationUxTest {
    private val calculator = OrderTimingCalculator()
    private val storeA = MockStoreRepository().stores()[0]
    private val menu = storeA.menu

    private fun controller(etaMinutes: Int? = 8): DriveFlowController {
        val c = DriveFlowController(MockVehicleStateProvider(VehicleState.PARKED),
            MockNavigationEtaProvider(etaMinutes), MockPaymentSimulator())
        c.selectStore(c.stores.stores().first())
        return c
    }

    // --- 준비시간 추정 ---

    @Test fun demoBaselineIsUnchanged() {
        val c = controller(); c.setQuantity(menu[0], 1)
        assertEquals(6, c.preparationEstimate().totalMinutes)
        val r = c.recommendation()
        assertEquals(Timing.LATER, r.timing); assertEquals(1, r.waitMinutes)
        assertEquals("약 1분 후 주문 추천", r.label)
        assertTrue(r.reason, r.reason.contains("약 1분 후 주문하면 도착 약 1분 전에 준비됩니다"))
        assertEquals(1, r.arrivalGapMinutes)
    }
    @Test fun emptyCartUsesStoreBase() {
        assertEquals(PreparationEstimate(6, 0, 0), controller().preparationEstimate())
    }
    @Test fun onlySlowestMenuExtraCounts() {
        val c = controller(); c.setQuantity(menu[0], 1); c.setQuantity(menu[2], 1)
        assertEquals(PreparationEstimate(6, 2, 0), c.preparationEstimate())
    }
    @Test fun quantityAddsOneMinutePerTwoExtraCups() {
        assertEquals(0, PreparationEstimate.of(6, listOf(OrderLine(menu[0], 2))).volumeExtraMinutes)
        assertEquals(1, PreparationEstimate.of(6, listOf(OrderLine(menu[0], 3))).volumeExtraMinutes)
        assertEquals(PreparationEstimate(6, 1, 2),
            PreparationEstimate.of(6, listOf(OrderLine(menu[0], 3), OrderLine(menu[1], 3))))
    }
    @Test fun cartChangeShiftsRecommendation() {
        val c = controller(); c.setQuantity(menu[0], 1)
        assertEquals(Timing.LATER, c.recommendation().timing)
        c.setQuantity(menu[0], 3)   // 6 + 수량 1 = 7분 → ETA 8에서 지금 주문
        assertEquals(7, c.preparationEstimate().totalMinutes)
        assertEquals(Timing.NOW, c.recommendation().timing)
    }
    @Test fun demoOverrideReplacesWholeEstimate() {
        val c = controller(10); c.setQuantity(menu[2], 3); c.preparationOverride = 5
        assertEquals(5, c.preparationEstimate().totalMinutes)
        assertEquals(4, c.recommendation().waitMinutes)   // 기존 시연: ETA 10 · 준비 5 → 약 4분 후
    }
    @Test fun estimateClampedToCalculatorRange() {
        val e = PreparationEstimate.of(120, listOf(OrderLine(menu[2], 3), OrderLine(menu[1], 3)))
        assertEquals(120, e.totalMinutes)
        assertEquals(Timing.LATER, calculator.recommend(180, e.totalMinutes).timing)
    }
    @Test fun invalidPreparationInputsRejected() {
        try { PreparationEstimate.of(-1, emptyList()); fail() } catch (_: IllegalArgumentException) { }
        try { PreparationEstimate.of(121, emptyList()); fail() } catch (_: IllegalArgumentException) { }
        try { MenuItem("x", "x", 1000, 31); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun breakdownOmitsZeroParts() {
        assertEquals("기본 6분", PreparationEstimate(6, 0, 0).breakdown)
        assertEquals("기본 6분 + 메뉴 2분 + 수량 1분", PreparationEstimate(6, 2, 1).breakdown)
    }

    // --- 추천 문구 ---

    @Test fun nowReadyBeforeArrival() {
        val r = calculator.recommend(6, 4, safety = 2)
        assertEquals(Timing.NOW, r.timing); assertEquals(2, r.arrivalGapMinutes)
        assertTrue(r.reason, r.reason.contains("지금 주문하면 도착 약 2분 전에 준비됩니다"))
    }
    @Test fun nowReadyAtArrival() {
        val r = calculator.recommend(8, 8)
        assertEquals(0, r.arrivalGapMinutes)
        assertTrue(r.reason, r.reason.contains("도착과 거의 동시에"))
    }
    @Test fun nowWaitsAfterArrival() {
        val r = calculator.recommend(3, 5)
        assertEquals(Timing.NOW, r.timing); assertEquals(-2, r.arrivalGapMinutes)
        assertTrue(r.reason, r.reason.contains("도착 후 약 2분 기다릴 수 있습니다"))
    }
    @Test fun laterWithDelayExplainsBuffer() {
        val r = calculator.recommend(10, 5, possibleDelay = 2)
        assertEquals(2, r.waitMinutes); assertEquals(3, r.arrivalGapMinutes)
        assertTrue(r.reason, r.reason.contains("지연 2분 대비 포함"))
    }
    @Test fun nowWithDelayWarnsOfDelay() {
        val r = calculator.recommend(8, 6, possibleDelay = 2)
        assertEquals(Timing.NOW, r.timing)
        assertTrue(r.reason, r.reason.contains("최대 2분 더 걸릴 수 있음"))
    }
    @Test fun afterArrivalHasNoGap() {
        assertNull(calculator.recommend(null, 5).arrivalGapMinutes)
        assertNull(calculator.recommend(1, 5).arrivalGapMinutes)
    }
    @Test fun minimumProcessingShownAsPreparation() {
        val r = calculator.recommend(5, 0)
        assertTrue(r.reason, r.reason.contains("준비 약 1분"))
    }
}
