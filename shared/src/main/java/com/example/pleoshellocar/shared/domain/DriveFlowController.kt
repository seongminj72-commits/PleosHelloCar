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
    val stores: StoreRepository = MockStoreRepository()
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
        check(orders.latest()?.terminal != false) { "진행 중인 주문이 있습니다." }
        require(store in stores.stores() && store.acceptingOrders) { "현재 주문할 수 없는 매장입니다." }
        selectedStore = store; navigation.selectDestination(store.routeTestPoint); quantities.clear(); requestId = UUID.randomUUID().toString()
    }
    fun setQuantity(item: MenuItem, quantity: Int) {
        InteractionPolicy.requireParked(vehicle)
        check(orders.latest()?.terminal != false) { "진행 중인 주문은 변경할 수 없습니다." }
        require(quantity in 0..3)
        require(selectedStore?.menu?.contains(item) == true)
        quantities[item.id] = quantity; requestId = UUID.randomUUID().toString()
    }
    fun lines(): List<OrderLine> = selectedStore?.menu.orEmpty().mapNotNull {
        quantity(it.id).takeIf { n -> n > 0 }?.let { n -> OrderLine(it, n) }
    }
    fun totalWon() = lines().sumOf { it.totalWon }
    fun recommendation() = OrderTimingCalculator().recommend(navigation.getEstimatedArrivalMinutes(),
        preparationOverride ?: selectedStore?.preparationMinutes ?: 6, possibleDelay = possibleDelay)
    fun checkout(): Order {
        InteractionPolicy.requireParked(vehicle)
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
        return orders.transition(order.id, next)
    }
    fun cancel(): Order {
        InteractionPolicy.requireParked(vehicle)
        val order = requireNotNull(orders.latest()) { "주문이 없습니다." }
        return orders.transition(order.id, OrderStatus.CANCELLED).also { announcement = "주문이 취소되었습니다. 실제 청구는 없습니다." }
    }
}
