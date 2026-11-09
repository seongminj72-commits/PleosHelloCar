package com.example.pleoshellocar.shared.domain

enum class Timing { NOW, LATER, AFTER_ARRIVAL }

/**
 * arrivalGapMinutes: 추천대로 주문했을 때 '도착 시각 − 준비 완료 시각'(분, 지연 미발생 가정).
 * 양수면 도착 전에 준비되고, 0이면 도착과 동시, 음수면 도착 후 그만큼 기다린다. 판단할 수 없으면 null.
 */
data class OrderTimingRecommendation(val timing: Timing, val waitMinutes: Int, val reason: String,
    val arrivalGapMinutes: Int? = null) {
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
        val prep = maxOf(preparation, minimumProcessing)
        val required = prep + safety + possibleDelay
        val wait = eta - required
        val basis = "도착 약 ${eta}분 · 준비 약 ${prep}분"
        return if (wait > 0) {
            val gap = safety + possibleDelay
            val delayNote = if (possibleDelay > 0) " (지연 ${possibleDelay}분 대비 포함)" else ""
            OrderTimingRecommendation(Timing.LATER, wait,
                "$basis. 약 ${wait}분 후 주문하면 도착 약 ${gap}분 전에 준비됩니다$delayNote. 추정값입니다.", gap)
        } else {
            val gap = eta - prep
            val outcome = when {
                gap > 0 -> "지금 주문하면 도착 약 ${gap}분 전에 준비됩니다"
                gap == 0 -> "지금 주문하면 도착과 거의 동시에 준비됩니다"
                else -> "지금 주문해도 도착 후 약 ${-gap}분 기다릴 수 있습니다"
            }
            val delayNote = if (possibleDelay > 0) " (매장 지연 시 최대 ${possibleDelay}분 더 걸릴 수 있음)" else ""
            OrderTimingRecommendation(Timing.NOW, 0, "$basis. $outcome$delayNote. 정차 중에만 주문하세요.", gap)
        }
    }
}
