package com.example.pleoshellocar.shared.domain

/** extraPreparationMinutes: 매장 기본 준비시간에 더해지는 메뉴별 추가 제조시간(분, 데모 추정값). */
data class MenuItem(val id: String, val name: String, val priceWon: Int, val extraPreparationMinutes: Int = 0) {
    init { require(id.isNotBlank() && priceWon in 1..100_000 && extraPreparationMinutes in 0..30) }
}
data class Store(val id: String, val name: String, val preparationMinutes: Int,
    val acceptingOrders: Boolean, val menu: List<MenuItem>,
    val routeTestPoint: com.example.pleoshellocar.shared.navigation.GeoPoint? = null)
data class OrderLine(val item: MenuItem, val quantity: Int) {
    init { require(quantity in 1..3) { "수량은 1~3개여야 합니다." } }
    val totalWon: Int get() = item.priceWon * quantity
}
enum class OrderStatus(val label: String) {
    ORDER_CREATED("주문 생성"), PAYMENT_SIMULATED("모의 결제 완료"), PREPARING("상품 준비 중"),
    READY_FOR_PICKUP("픽업 준비 완료"), COMPLETED("픽업 완료"), CANCELLED("주문 취소")
}
data class Order(val id: String, val requestId: String, val store: Store,
    val lines: List<OrderLine>, val status: OrderStatus = OrderStatus.ORDER_CREATED) {
    val totalWon: Int get() = lines.sumOf { it.totalWon }
    val terminal: Boolean get() = status == OrderStatus.COMPLETED || status == OrderStatus.CANCELLED
}
