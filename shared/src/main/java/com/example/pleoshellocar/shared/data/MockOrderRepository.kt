package com.example.pleoshellocar.shared.data

import com.example.pleoshellocar.shared.domain.*
import java.util.UUID
interface OrderRepository {
    fun latest(): Order?
    fun create(requestId: String, store: Store, lines: List<OrderLine>): Order
    fun transition(id: String, next: OrderStatus): Order
}
/** Session-memory demo repository. Requests are idempotent; only one active order is allowed. */
class MockOrderRepository : OrderRepository {
    private val orders = linkedMapOf<String, Order>()
    override fun latest() = orders.values.lastOrNull()
    @Synchronized override fun create(requestId: String, store: Store, lines: List<OrderLine>): Order {
        require(requestId.isNotBlank())
        orders.values.firstOrNull { it.requestId == requestId }?.let {
            require(it.store == store && it.lines == lines) { "동일 요청의 주문 내용이 변경되었습니다." }
            return it
        }
        check(latest()?.terminal != false) { "진행 중인 주문이 있습니다. 내 주문을 확인하세요." }
        require(store.acceptingOrders) { "현재 주문할 수 없는 매장입니다." }
        require(lines.isNotEmpty() && lines.size <= 4) { "메뉴를 한 개 이상 선택하세요." }
        require(lines.map { it.item.id }.distinct().size == lines.size)
        require(lines.all { line -> store.menu.any { it == line.item } }) { "매장 메뉴와 일치하지 않습니다." }
        val order = Order("DF-" + UUID.randomUUID().toString().take(8).uppercase(), requestId,
            store.copy(menu = store.menu.toList()), lines.toList())
        orders[order.id] = order
        return order
    }
    @Synchronized override fun transition(id: String, next: OrderStatus): Order {
        val order = requireNotNull(orders[id]) { "주문을 찾을 수 없습니다." }
        val allowed = when(order.status) {
            OrderStatus.ORDER_CREATED -> setOf(OrderStatus.PAYMENT_SIMULATED, OrderStatus.CANCELLED)
            OrderStatus.PAYMENT_SIMULATED -> setOf(OrderStatus.PREPARING, OrderStatus.CANCELLED)
            OrderStatus.PREPARING -> setOf(OrderStatus.READY_FOR_PICKUP, OrderStatus.CANCELLED)
            OrderStatus.READY_FOR_PICKUP -> setOf(OrderStatus.COMPLETED, OrderStatus.CANCELLED)
            else -> emptySet()
        }
        require(next in allowed) { "허용되지 않는 주문 상태 전환입니다." }
        return order.copy(status = next).also { orders[id] = it }
    }
}
