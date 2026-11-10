package com.example.pleoshellocar.shared.vehicle

enum class VehicleStateMode { MOCK, REAL }

/** REAL never falls back to Mock. A future SDK adapter must deliver on the main thread. */
class SwitchableVehicleStateProvider(
    val mock: MockVehicleStateProvider = MockVehicleStateProvider(),
    private val real: VehicleStateProvider? = null
) : VehicleStateProvider, AutoCloseable {
    private val listeners = linkedSetOf<() -> Unit>()
    private var closed = false
    private var subscriptionFailed = false
    var mode: VehicleStateMode = VehicleStateMode.MOCK
        private set
    private val mockChanged: () -> Unit = { if (mode == VehicleStateMode.MOCK) notifyChanged() }
    // Real changes matter in both modes: MOCK must react when a real DRIVING signal starts or ends.
    private val realChanged: () -> Unit = { notifyChanged() }

    init {
        mock.addListener(mockChanged)
        try { real?.addListener(realChanged) } catch (_: Exception) { subscriptionFailed = true }
    }

    override val source: VehicleStateSource get() =
        if (mode == VehicleStateMode.MOCK) VehicleStateSource.MOCK else VehicleStateSource.VEHICLE_SDK

    private fun snapshot(): VehicleStateResult {
        if (closed) return VehicleStateResult(VehicleState.UNKNOWN, "차량 상태 공급자 종료")
        if (mode == VehicleStateMode.MOCK) {
            // Mock may only add restrictions: a real DRIVING signal is never masked by a demo PARKED value.
            return if (realReportsDriving()) VehicleStateResult(VehicleState.DRIVING, "실제 차량 주행 감지 · Mock 상태 무시")
            else VehicleStateResult(mock.state, mock.reason)
        }
        if (real == null) return VehicleStateResult(VehicleState.UNKNOWN, "차량 신호 없음 · SDK 미연동")
        if (subscriptionFailed) return VehicleStateResult(VehicleState.UNKNOWN, "SDK 연결 실패")
        return try {
            if (real.source != VehicleStateSource.VEHICLE_SDK)
                VehicleStateResult(VehicleState.UNKNOWN, "실제 차량 공급자 없음")
            else VehicleStateResult(real.getVehicleState(), real.reason)
        } catch (_: Exception) { VehicleStateResult(VehicleState.UNKNOWN, "SDK 연결 실패") }
    }
    private fun realReportsDriving(): Boolean = real != null && !subscriptionFailed && try {
        real.source == VehicleStateSource.VEHICLE_SDK && real.getVehicleState() == VehicleState.DRIVING
    } catch (_: Exception) { false }
    override fun getVehicleState() = snapshot().state
    override val reason: String get() = snapshot().reason

    /**
     * MOCK → REAL is always allowed (it can only add restrictions).
     * REAL → MOCK requires a confirmed real PARKED signal, because the host parked-only gate is not
     * guaranteed (it did not engage on the Pleos emulator at D·30). Restarting the app starts in MOCK.
     */
    fun setMode(value: VehicleStateMode) {
        check(!closed) { "차량 상태 공급자 종료" }
        if (mode == value) return
        if (mode == VehicleStateMode.REAL && value == VehicleStateMode.MOCK && real != null)
            check(getVehicleState() == VehicleState.PARKED) {
                "실제 차량 정차(P·0)가 확인될 때만 Mock으로 전환할 수 있습니다. 앱을 다시 시작하면 Mock 모드로 시작합니다."
            }
        mode = value
        notifyChanged()
    }
    val canRequestVehiclePermissions: Boolean get() = real is ManagedVehicleStateProvider
    fun requestVehiclePermissions() { if (!closed) (real as? ManagedVehicleStateProvider)?.requestPermissions() }

    fun cycleMockState() {
        check(!closed && mode == VehicleStateMode.MOCK) { "실제 차량 모드에서는 Mock 상태를 바꿀 수 없습니다." }
        mock.state = when (mock.state) {
            VehicleState.UNKNOWN -> VehicleState.PARKED
            VehicleState.PARKED -> VehicleState.DRIVING
            VehicleState.DRIVING -> VehicleState.UNKNOWN
        }
    }
    private fun notifyChanged() { if (!closed) listeners.toList().forEach { it() } }
    override fun addListener(listener: () -> Unit) { if (!closed) listeners.add(listener) }
    override fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    override fun close() {
        if (closed) return
        closed = true
        mock.removeListener(mockChanged)
        try { real?.removeListener(realChanged) } catch (_: Exception) { /* Already fail-closed. */ }
        listeners.clear()
    }
}
