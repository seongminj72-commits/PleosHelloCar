package com.example.pleoshellocar.shared.vehicle

/** App-owned values, not speculative SDK constants. */
enum class VehicleGear { P, R, N, D, UNKNOWN }
enum class VehicleConnection { CONNECTED, DISCONNECTED, PERMISSION_DENIED, INITIALIZATION_FAILED }
data class VehicleStateResult(val state: VehicleState, val reason: String)

/** Times must use the same monotonic millisecond clock. Expiry is exclusive. */
class VehicleStateResolver(private val freshnessMs: Long = 3_000) {
    init { require(freshnessMs > 0) }

    fun resolve(gear: VehicleGear, speedKmh: Double?, receivedAtMs: Long?, nowMs: Long,
                connection: VehicleConnection): VehicleStateResult {
        fun unknown(reason: String) = VehicleStateResult(VehicleState.UNKNOWN, reason)
        when (connection) {
            VehicleConnection.DISCONNECTED -> return unknown("차량 신호 없음")
            VehicleConnection.PERMISSION_DENIED -> return unknown("권한 필요")
            VehicleConnection.INITIALIZATION_FAILED -> return unknown("SDK 연결 실패")
            VehicleConnection.CONNECTED -> Unit
        }
        if (receivedAtMs == null || receivedAtMs < 0 || nowMs < receivedAtMs)
            return unknown("차량 신호 시각 없음 또는 오류")
        if (nowMs - receivedAtMs >= freshnessMs) return unknown("차량 신호 만료")
        if (speedKmh == null || !speedKmh.isFinite() || speedKmh < 0)
            return unknown("유효한 속도 신호 없음")
        // Conflicting P/speed must never become PARKED (nor mask the conflict as DRIVING).
        if (gear == VehicleGear.P) return if (speedKmh <= 1.0)
            VehicleStateResult(VehicleState.PARKED, "P 기어 · 정차 확인")
        else unknown("기어와 속도 신호 상충")
        if (gear == VehicleGear.N) return unknown("N 기어 · 정차 확인 불가")
        if (gear == VehicleGear.D || gear == VehicleGear.R || speedKmh > 3.0)
            return VehicleStateResult(VehicleState.DRIVING, "주행 기어 또는 속도 감지")
        return unknown("정차 확인 불가")
    }
}
