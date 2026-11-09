package com.example.pleoshellocar.shared.domain

/**
 * 매장 기본 준비시간에 장바구니 구성을 반영한 준비시간 추정값(분).
 * - 메뉴 추가시간: 담은 메뉴 중 가장 오래 걸리는 메뉴의 추가시간만 더한다(병행 제조 가정).
 * - 수량 추가시간: 첫 잔 이후 2잔마다 1분을 더한다.
 * 모든 값은 데모용 추정값이며 실제 매장 POS 데이터가 아니다.
 */
data class PreparationEstimate(val baseMinutes: Int, val menuExtraMinutes: Int, val volumeExtraMinutes: Int) {
    val totalMinutes: Int get() = baseMinutes + menuExtraMinutes + volumeExtraMinutes

    /** 예: "기본 6분 + 메뉴 2분 + 수량 1분" — 추가분이 없으면 생략한다. */
    val breakdown: String get() = buildList {
        add("기본 ${baseMinutes}분")
        if (menuExtraMinutes > 0) add("메뉴 ${menuExtraMinutes}분")
        if (volumeExtraMinutes > 0) add("수량 ${volumeExtraMinutes}분")
    }.joinToString(" + ")

    companion object {
        const val MAX_MINUTES = 120

        fun of(baseMinutes: Int, lines: List<OrderLine>): PreparationEstimate {
            require(baseMinutes in 0..MAX_MINUTES) { "매장 준비시간이 유효하지 않습니다." }
            val menuExtra = lines.maxOfOrNull { it.item.extraPreparationMinutes } ?: 0
            val cups = lines.sumOf { it.quantity }
            val volumeExtra = if (cups <= 1) 0 else (cups - 1) / 2
            // 합계가 상한을 넘지 않도록 추가분을 잘라 계산기 입력 범위(0..120)를 지킨다.
            val menu = minOf(menuExtra, MAX_MINUTES - baseMinutes)
            val volume = minOf(volumeExtra, MAX_MINUTES - baseMinutes - menu)
            return PreparationEstimate(baseMinutes, menu, volume)
        }
    }
}
