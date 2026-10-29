package com.example.pleoshellocar.shared.domain

enum class Timing { NOW, LATER, AFTER_ARRIVAL }
data class OrderTimingRecommendation(val timing: Timing, val waitMinutes: Int, val reason: String) {
    val label: String get() = when(timing) {
        Timing.NOW -> "지금 주문 추천"
        Timing.LATER -> "약 ${waitMinutes}분 후 주문 추천"
        Timing.AFTER_ARRIVAL -> "도착 후 주문 추천"
    }
}
/** All inputs are estimates in whole minutes. No automatic order or promise is made. */
class OrderTimingCalculator {
    fun recommend(eta: Int?, preparation: Int, safety: Int = 1,
        minimumProcessing: Int = 1, possibleDelay: Int = 0): OrderTimingRecommendation {
        if (eta == null || eta !in 0..180 || preparation !in 0..120 || safety !in 0..30 ||
            minimumProcessing !in 1..30 || possibleDelay !in 0..60) {
            return OrderTimingRecommendation(Timing.AFTER_ARRIVAL, 0, "예상시간 정보가 없거나 유효하지 않습니다. 도착 후 확인하세요.")
        }
        if (eta <= minimumProcessing) return OrderTimingRecommendation(Timing.AFTER_ARRIVAL, 0,
            "도착이 임박해 최소 주문 처리시간이 부족합니다. 도착 후 정차하여 주문하세요.")
        val required = maxOf(preparation, minimumProcessing) + safety + possibleDelay
        val wait = eta - required
        return if (wait > 0) OrderTimingRecommendation(Timing.LATER, wait,
            "ETA ${eta}분 − 준비 ${maxOf(preparation, minimumProcessing)}분 − 여유 ${safety}분 − 지연 ${possibleDelay}분. 추정값입니다.")
        else OrderTimingRecommendation(Timing.NOW, 0,
            "준비·여유·지연 예상 ${required}분, ETA ${eta}분. 도착 후 대기할 수 있습니다. 정차 중에만 주문하세요.")
    }
}
