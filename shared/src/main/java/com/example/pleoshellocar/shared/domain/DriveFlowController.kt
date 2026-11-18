package com.example.pleoshellocar.shared.domain

import com.example.pleoshellocar.shared.data.*
import com.example.pleoshellocar.shared.vehicle.*
import com.example.pleoshellocar.shared.navigation.*
import com.example.pleoshellocar.shared.payment.*
import java.util.UUID

class DriveFlowController(
    val vehicle: VehicleStateProvider,
    val navigation: NavigationEtaProvider,
    val payment: PaymentSimulator,
    val orders: OrderRepository = MockOrderRepository(),
    val stores: StoreRepository = MockStoreRepository(),
    val scheduledOrders: ScheduledOrderRepository? = null,
    private val dispatchEta: (now: Long) -> DispatchEta? = { null },
    private val clock: () -> Long = { 0L },
    private val startDispatch: () -> Unit = {},
    val completedOrders: CompletedOrderStore? = null,
    private val announcer: VoiceAnnouncer? = null
) {
    var selectedStore: Store? = null; private set
    private val quantities = linkedMapOf<String, Int>()
    private var requestId = UUID.randomUUID().toString()
    var preparationOverride: Int? = null
    var possibleDelay: Int = 0
    var announcement: String = "차량·매장·결제는 Mock입니다. ETA 출처는 별도 표시합니다."
        private set
    fun quantity(id: String) = quantities[id] ?: 0
    fun selectStore(store: Store) {
        InteractionPolicy.requireParked(vehicle)
        requireNoReservation()
        check(orders.latest()?.terminal != false) { "진행 중인 주문이 있습니다." }
        require(store in stores.stores() && store.acceptingOrders) { "현재 주문할 수 없는 매장입니다." }
        selectedStore = store
        navigation.selectStoreDestination(store.name, store.routeTestPoint)
        quantities.clear()
        requestId = UUID.randomUUID().toString()
    }
    fun setQuantity(item: MenuItem, quantity: Int) {
        InteractionPolicy.requireParked(vehicle)
        check(orders.latest()?.terminal != false) { "진행 중인 주문은 변경할 수 없습니다." }
        require(quantity in 0..9)
        require(selectedStore?.menu?.contains(item) == true)
        quantities[item.id] = quantity; requestId = UUID.randomUUID().toString()
    }
    fun lines(): List<OrderLine> = selectedStore?.menu.orEmpty().mapNotNull {
        quantity(it.id).takeIf { n -> n > 0 }?.let { n -> OrderLine(it, n) }
    }
    fun totalWon() = lines().sumOf { it.totalWon }
    fun lastCompleted() = completedOrders?.last()
    fun applyCommand(command: ParsedCommand) {
        InteractionPolicy.requireParked(vehicle)
        when (command) {
            is ParsedCommand.Scheduled -> selectLinesForCommand(listOf(command.item.id to command.quantity))
            is ParsedCommand.Immediate -> selectLinesForCommand(listOf(command.item.id to command.quantity))
            is ParsedCommand.Reorder -> {
                val store = stores.stores().singleOrNull { it.id == command.previous.storeId }
                    ?: error("지난 주문의 매장을 찾을 수 없습니다")
                selectStore(store)
                setLines(command.previous.lines)
            }
            ParsedCommand.Cancel -> if (reservationActive) scheduledOrders?.cancel() else cancel()
            is ParsedCommand.Unknown -> error(command.reason)
        }
    }
    private fun selectLinesForCommand(lines: List<Pair<String, Int>>) {
        if (selectedStore == null) selectStore(stores.stores().first { it.acceptingOrders })
        setLines(lines)
    }
    private fun setLines(lines: List<Pair<String, Int>>) {
        val store = checkNotNull(selectedStore)
        check(lines.isNotEmpty()) { "지난 주문의 메뉴가 없습니다" }
        lines.forEach { (id, count) ->
            val item = store.menu.singleOrNull { it.id == id }
                ?: error("지난 주문의 메뉴를 현재 매장에서 찾을 수 없습니다")
            setQuantity(item, count)
        }
    }
    fun announce(message: String) {
        announcement = message
        announcer?.speak(message) { announcement = "$message · 음성 안내 실패" }
    }
    /** 매장 기본값 + 장바구니(메뉴·수량) 반영 준비시간. 데모 설정의 덮어쓰기 값이 있으면 그 값을 그대로 쓴다. */
    fun preparationEstimate(): PreparationEstimate = preparationOverride?.let { PreparationEstimate(it, 0, 0) }
        ?: PreparationEstimate.of(selectedStore?.preparationMinutes ?: 6, lines())
    fun recommendation() = OrderTimingCalculator().recommend(navigation.getEstimatedArrivalMinutes(),
        preparationEstimate().totalMinutes, possibleDelay = possibleDelay)
    fun recommendationText(): String {
        if (lines().isEmpty()) return "메뉴를 선택하면 주문 시점을 추천합니다"
        val recommendation = recommendation()
        return "${recommendation.label} · ${recommendation.reason}"
    }
    val reservationActive get() = scheduledOrders?.current?.status?.terminal == false

    private fun requireNoReservation() {
        check(!reservationActive) { "내 주문에서 전달 예약을 확인하세요." }
    }

    fun reserveDispatch(customLeadMinutes: Int? = null) {
        InteractionPolicy.requireParked(vehicle)
        val repository = checkNotNull(scheduledOrders) { "자동 전달을 사용할 수 없습니다." }
        val source = (navigation as? SelectableNavigationEtaProvider)?.source ?: EtaSource.MOCK
        check(source != EtaSource.TMAP) { "자동 전달은 내비 또는 Mock ETA를 선택하세요." }
        val now = clock()
        val eta = checkNotNull((repository.demoEta.sample(now, now) ?: dispatchEta(now))?.takeIf { it.fresh(now) && it.arrivalAt > now }) { "최신 도착 예상시간을 확인하세요." }
        val store = checkNotNull(selectedStore) { "매장을 선택하세요." }
        val items = lines()
        check(items.isNotEmpty()) { "메뉴를 선택하세요." }
        require(customLeadMinutes == null || customLeadMinutes in 1..180)
        repository.reserve(ScheduledOrder(requestId, store, items, preparationEstimate(), source, now, eta.arrivalAt, eta,
            customLeadMinutes = customLeadMinutes))
        try {
            startDispatch()
        } catch (_: RuntimeException) {
            repository.failStart()
            error("자동 전달을 시작할 수 없습니다.")
        }
    }

    fun checkout(): Order {
        InteractionPolicy.requireParked(vehicle)
        requireNoReservation()
        val store = requireNotNull(selectedStore) { "매장을 선택하세요." }
        val order = orders.create(requestId, store, lines())
        if (order.status != OrderStatus.ORDER_CREATED) {
            check(!order.terminal) { "종료된 주문입니다. 매장을 다시 선택하세요." }
            return order
        }
        check(payment.approve(order.id)) { "모의 결제 실패 — 청구 없음. 내 주문에서 재시도하거나 취소하세요." }
        announcement = "주문이 접수되었습니다. (모의 결제)"
        return orders.transition(order.id, OrderStatus.PAYMENT_SIMULATED)
    }
    fun advance(): Order {
        InteractionPolicy.requireParked(vehicle)
        val order = requireNotNull(orders.latest()) { "주문이 없습니다." }
        val next = when(order.status) {
            OrderStatus.PAYMENT_SIMULATED -> OrderStatus.PREPARING
            OrderStatus.PREPARING -> OrderStatus.READY_FOR_PICKUP
            OrderStatus.READY_FOR_PICKUP -> OrderStatus.COMPLETED
            else -> error("다음 단계로 진행할 수 없습니다.")
        }
        announcement = when(next) {
            OrderStatus.PREPARING -> "상품을 준비 중입니다."
            OrderStatus.READY_FOR_PICKUP -> "픽업 준비가 완료되었습니다."
            else -> "픽업이 완료되었습니다."
        }
        return orders.transition(order.id, next).also { if (next == OrderStatus.COMPLETED) completedOrders?.save(it) }
    }
    fun cancel(): Order {
        InteractionPolicy.requireParked(vehicle)
        val order = requireNotNull(orders.latest()) { "주문이 없습니다." }
        return orders.transition(order.id, OrderStatus.CANCELLED).also { announcement = "주문이 취소되었습니다. 실제 청구는 없습니다." }
    }
}
