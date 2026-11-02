package com.example.pleoshellocar.shared.navigation

import java.io.Closeable

enum class EtaMode { MOCK, REAL }
enum class EtaFailure(val label: String) {
    CONFIGURATION("프록시 설정 필요"), PERMISSION("정확한 위치 권한 필요"),
    LOCATION("최신 위치 확인 불가"), NETWORK("네트워크 연결 실패"),
    AUTH("인증 만료 또는 거부"), RATE_LIMIT("호출 한도 초과"),
    SERVER("경로 서버 오류"), INVALID_RESPONSE("ETA 응답 오류"), EXPIRED("ETA 유효시간 만료")
}
sealed class EtaResult {
    data class Success(val seconds: Int, val simulatedLocation: Boolean = false, val fixture: Boolean = false) : EtaResult()
    data class Failure(val reason: EtaFailure) : EtaResult()
}
fun interface EtaRequest {
    /** Callback and provider methods run on the main thread. Close cancels pending work. */
    fun request(destination: GeoPoint, callback: (EtaResult) -> Unit): Closeable
}
/** Real results never fall back silently to Mock. No location or route is persisted. */
class TmapNavigationEtaProvider(
    private val request: EtaRequest,
    private val clock: () -> Long,
    val mock: MockNavigationEtaProvider = MockNavigationEtaProvider()
) : NavigationEtaProvider, Closeable {
    var mode = EtaMode.MOCK; private set
    private var destination: GeoPoint? = null
    private var generation = 0L
    private var lastAttempt: Long? = null
    private var receivedAt = 0L
    private var seconds: Int? = null
    private var failure: EtaFailure? = null
    private var simulatedLocation = false
    private var fixture = false
    private var loading = false
    private var active = false
    private var pending: Closeable? = null
    private val listeners = linkedSetOf<() -> Unit>()
    override val statusLabel: String get() = if (mode == EtaMode.MOCK) "[Mock ETA] 오프라인 데모" else when {
        destination == null -> "[TMAP ETA] 가상 매장을 선택하세요"
        loading -> "[TMAP ETA] 조회 중 · 테스트 목적지"
        failure != null -> "[TMAP ETA] ${failure!!.label}"
        getEstimatedArrivalMinutes() != null && fixture -> "[HTTP 테스트 ETA] 합성 응답 · 실 API 아님"
        getEstimatedArrivalMinutes() != null -> "[TMAP ETA] 실제 경로 응답 · ${if (simulatedLocation) "모의 위치" else "기기 위치"} · 가상 매장"
        else -> "[TMAP ETA] 정보 없음"
    }
    override fun getEstimatedArrivalMinutes(): Int? = if (mode == EtaMode.MOCK) mock.minutes
        else seconds?.takeIf { clock() - receivedAt in 0 until 60_000 }?.let { (it + 59) / 60 }
    override fun addListener(listener: () -> Unit) { listeners.add(listener) }
    override fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun changed() { listeners.toList().forEach { it() } }
    private fun clear() { generation++; pending?.close(); pending = null; seconds = null; loading = false; failure = null }
    fun setMode(value: EtaMode) {
        if (mode == value) return
        clear(); mode = value; changed(); refresh()
    }
    override fun selectDestination(point: GeoPoint?) {
        if (destination == point) return
        clear(); destination = point; changed(); refresh()
    }
    fun setActive(value: Boolean) {
        active = value
        if (!value) { clear(); changed() } else refresh()
    }
    /** Tick also invalidates stale UI even while the next request is throttled. */
    fun refresh() {
        if (mode != EtaMode.REAL || !active) return
        if (seconds != null && getEstimatedArrivalMinutes() == null) {
            seconds = null; failure = EtaFailure.EXPIRED; changed()
        }
        val target = destination ?: return
        if (loading || lastAttempt?.let { clock() - it < 30_000 } == true) return
        lastAttempt = clock(); loading = true; seconds = null; failure = null
        val ticket = ++generation
        changed()
        pending = request.request(target) { result ->
            if (ticket == generation && active && mode == EtaMode.REAL) {
                loading = false
                when (result) {
                    is EtaResult.Success -> if (result.seconds in 0..10_800) {
                        seconds = result.seconds; receivedAt = clock(); simulatedLocation = result.simulatedLocation; fixture = result.fixture
                    } else { failure = EtaFailure.INVALID_RESPONSE }
                    is EtaResult.Failure -> failure = result.reason
                }
                changed()
            }
        }
    }
    override fun close() { active = false; clear(); listeners.clear() }
}
