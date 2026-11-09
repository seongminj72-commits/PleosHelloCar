package com.example.pleoshellocar.shared.vehicle

enum class VehicleState { PARKED, DRIVING, UNKNOWN }
enum class VehicleStateSource { MOCK, VEHICLE_SDK }
interface VehicleStateProvider {
    fun getVehicleState(): VehicleState
    val source: VehicleStateSource get() = VehicleStateSource.MOCK
    val reason: String get() = "실제 차량 신호 아님"
    fun addListener(listener: () -> Unit) {}
    fun removeListener(listener: () -> Unit) {}
}

/** Mutations and listener delivery are confined to the caller (UI) thread. */
class MockVehicleStateProvider(state: VehicleState = VehicleState.UNKNOWN) : VehicleStateProvider {
    private val listeners = linkedSetOf<() -> Unit>()
    var state: VehicleState = state
        set(value) {
            if (field == value) return
            field = value
            listeners.toList().forEach { it() }
        }
    override fun getVehicleState() = state
    override fun addListener(listener: () -> Unit) { listeners.add(listener) }
    override fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
}
object InteractionPolicy {
    fun requireParked(provider: VehicleStateProvider) {
        check(provider.getVehicleState() == VehicleState.PARKED) { "정차 상태 확인 필요 — 현재 상태에서는 주문 조작이 제한됩니다." }
    }
}
