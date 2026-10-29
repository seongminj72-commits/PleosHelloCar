package com.example.pleoshellocar.shared.vehicle

enum class VehicleState { PARKED, DRIVING, UNKNOWN }
interface VehicleStateProvider { fun getVehicleState(): VehicleState }
class MockVehicleStateProvider(var state: VehicleState = VehicleState.UNKNOWN) : VehicleStateProvider {
    override fun getVehicleState() = state
}
object InteractionPolicy {
    fun requireParked(provider: VehicleStateProvider) {
        check(provider.getVehicleState() == VehicleState.PARKED) { "정차 상태 확인 필요 — 현재 상태에서는 주문 조작이 제한됩니다." }
    }
}
