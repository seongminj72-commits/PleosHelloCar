package com.example.pleoshellocar.shared.domain

import com.example.pleoshellocar.shared.navigation.EtaSource

enum class DispatchStatus(val label: String) {
    SCHEDULED("예약됨"), DISPATCHING("전달 중"), SENT("전달됨"), FAILED("실패"), CANCELLED("취소됨");

    val terminal get() = this == SENT || this == FAILED || this == CANCELLED
}

data class DispatchEta(val minutes: Int, val receivedAt: Long, val remainingSeconds: Long = minutes * 60L) {
    init { require(minutes >= 0 && remainingSeconds >= 0) }
    val arrivalAt get() = receivedAt + remainingSeconds * 1_000L
    fun fresh(now: Long) = now - receivedAt in 0 until 180_000L
}

data class ScheduledOrder(
    val requestId: String,
    val store: Store,
    val lines: List<OrderLine>,
    val preparation: PreparationEstimate,
    val source: EtaSource,
    val createdAt: Long,
    val arrivalAt: Long,
    val eta: DispatchEta?,
    val status: DispatchStatus = DispatchStatus.SCHEDULED,
    val nextAttemptAt: Long = createdAt,
    val failures: Int = 0,
    val heldNoticeSent: Boolean = false,
    val message: String = "데모 전달 예약됨",
    val voiceFailed: Boolean = false
) {
    val serviceDeadline get() = minOf(arrivalAt + 600_000L, createdAt + 10_800_000L)
    val dispatchAt get() = arrivalAt - (preparation.totalMinutes + 1) * 60_000L
}

internal enum class DispatchDecision { WAIT, HOLD, SEND, FAIL }

internal fun decideDispatch(order: ScheduledOrder, now: Long, safetyMinutes: Int = 1): DispatchDecision = when {
    order.status.terminal -> DispatchDecision.WAIT
    now >= minOf(order.arrivalAt, order.serviceDeadline) -> DispatchDecision.FAIL
    order.eta?.fresh(now) != true -> DispatchDecision.HOLD
    now < order.nextAttemptAt -> DispatchDecision.WAIT
    order.eta.minutes - order.preparation.totalMinutes <= safetyMinutes -> DispatchDecision.SEND
    else -> DispatchDecision.WAIT
}

internal fun retryDispatchDelay(failures: Int): Long? = when (failures) {
    1 -> 5_000L
    2 -> 10_000L
    3 -> 20_000L
    else -> null
}

enum class DispatchDemoEta(val label: String) {
    OFF("실제 선택 소스"), DROP("25초 뒤 30→5분"), RISE("25초 뒤 10→30분"), DEADLINE("마감까지 25초");

    fun sample(startAt: Long, now: Long): DispatchEta? = when (this) {
        OFF -> null
        DROP -> if (now - startAt >= 25_000) DispatchEta(5, startAt + 25_000) else DispatchEta(30, startAt)
        RISE -> if (now - startAt >= 25_000) DispatchEta(30, startAt + 25_000) else DispatchEta(10, startAt)
        DEADLINE -> DispatchEta(1, startAt, remainingSeconds = 25)
    }
}
