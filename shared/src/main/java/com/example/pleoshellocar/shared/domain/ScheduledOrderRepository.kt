package com.example.pleoshellocar.shared.domain

import com.example.pleoshellocar.shared.data.MockOrderRepository

/** Process-memory demo state, used on the main thread by the service and screens. */
class ScheduledOrderRepository(val orders: MockOrderRepository = MockOrderRepository()) {
    var current: ScheduledOrder? = null
        private set
    var demoEta = DispatchDemoEta.OFF
    private val listeners = linkedSetOf<() -> Unit>()

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

    fun reserve(order: ScheduledOrder) {
        check(current?.status?.terminal != false) { "이미 전달 예약이 있습니다." }
        check(orders.latest()?.terminal != false) { "진행 중인 주문이 있습니다." }
        replace(order)
    }

    fun cancel() {
        val order = checkNotNull(current) { "예약이 없습니다." }
        check(!order.status.terminal) { "이미 종료된 예약입니다." }
        replace(order.copy(status = DispatchStatus.CANCELLED, message = "데모 전달 예약 취소"))
    }

    fun failStart() {
        current?.takeUnless { it.status.terminal }?.let {
            replace(it.copy(status = DispatchStatus.FAILED, message = "자동 전달을 시작할 수 없습니다"))
        }
    }

    fun voiceFailed(requestId: String) {
        current?.takeIf { it.requestId == requestId }?.let { replace(it.copy(voiceFailed = true)) }
    }

    fun tick(now: Long, sample: DispatchEta?, announce: (String) -> Unit) {
        var order = current?.takeUnless { it.status.terminal } ?: return
        // A later sample can extend the estimate only if it was received before the old deadline.
        if (sample != null && sample != order.eta && sample.fresh(now) &&
            sample.receivedAt < order.arrivalAt && now < minOf(order.arrivalAt, order.serviceDeadline)) {
            order = order.copy(eta = sample, arrivalAt = sample.arrivalAt)
        }
        if (sample == null) order = order.copy(eta = null)
        if (order.eta?.fresh(now) == true && order.message.startsWith("예상시간을 확인할 수 없어")) {
            order = order.copy(message = "데모 전달 대기 중")
        }
        when (decideDispatch(order, now)) {
            DispatchDecision.WAIT -> replace(order)
            DispatchDecision.HOLD -> {
                val message = "예상시간을 확인할 수 없어 전달을 보류합니다"
                val first = !order.heldNoticeSent
                replace(order.copy(message = message, heldNoticeSent = true))
                if (first) announce(message)
            }
            DispatchDecision.FAIL -> finish(order, DispatchStatus.FAILED, "전달 마감이 지났습니다", announce)
            DispatchDecision.SEND -> {
                replace(order.copy(status = DispatchStatus.DISPATCHING, message = "데모 전달 중"))
                try {
                    orders.dispatch(order.requestId, order.store, order.lines)
                } catch (_: IllegalStateException) {
                    val failures = order.failures + 1
                    val delay = retryDispatchDelay(failures)
                    if (delay == null) finish(order, DispatchStatus.FAILED, "데모 전달 실패", announce)
                    else replace(order.copy(status = DispatchStatus.DISPATCHING, failures = failures,
                        nextAttemptAt = now + delay, message = "데모 전달 재시도 대기"))
                    return
                }
                val late = order.preparation.totalMinutes - checkNotNull(order.eta).minutes
                finish(order, DispatchStatus.SENT,
                    if (late > 0) "데모 전달 완료. 약 ${late}분 늦게 준비될 수 있습니다" else "데모 전달 완료", announce)
            }
        }
    }

    private fun finish(order: ScheduledOrder, status: DispatchStatus, message: String, announce: (String) -> Unit) {
        replace(order.copy(status = status, message = message))
        announce(message)
    }

    private fun replace(order: ScheduledOrder) {
        if (current == order) return
        current = order
        listeners.toList().forEach { it() }
    }
}
