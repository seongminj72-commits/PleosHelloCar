package com.example.pleoshellocar.navigation

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import com.example.pleoshellocar.shared.navigation.GeoPoint
import com.example.pleoshellocar.shared.navigation.LifecycleNavigationEtaProvider

/** Reads the Navigation app's active destination. State is confined to the main thread. */
class NaviHelperEtaProvider(context: Context) : LifecycleNavigationEtaProvider {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val listeners = linkedSetOf<() -> Unit>()
    private var connection: Connection? = null
    private var reading: Reading? = null
    private var store: StoreDestination? = null
    private var state = "연결 대기"

    private data class StoreDestination(val name: String, val point: GeoPoint)

    private data class Reading(val seconds: Int, val receivedAt: Long)

    override val statusLabel: String
        get() = "[내비 ETA] " + when {
            getEstimatedArrivalMinutes() != null -> "${store?.name}까지 약 ${getEstimatedArrivalMinutes()}분"
            reading != null -> "ETA 유효시간 만료"
            else -> state
        }

    override fun getEstimatedArrivalMinutes(): Int? =
        reading?.takeUnless { isEtaExpired(it.receivedAt, SystemClock.elapsedRealtime()) }?.let { etaMinutes(it.seconds) }

    override fun addListener(listener: () -> Unit) { listeners.add(listener) }
    override fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

    override fun selectStoreDestination(name: String, point: GeoPoint?) {
        val selected = point?.let { StoreDestination(name, it) }
        if (store == selected) return
        store = selected
        connection?.refreshForStore()
    }

    override fun requestStoreRoute() {
        val target = checkNotNull(store) { "경로를 설정할 매장을 선택하세요." }
        checkNotNull(connection) { "내비 연결이 필요합니다." }.requestRoute(target)
    }

    private val expiry = Runnable { update("ETA 유효시간 만료") }

    private fun update(message: String, value: Reading? = null) {
        val oldSeconds = reading?.seconds
        val oldState = state
        reading = value
        state = message
        main.removeCallbacks(expiry)
        if (value != null) main.postDelayed(expiry, ETA_EXPIRY_MS)
        if (oldSeconds != value?.seconds || oldState != message) {
            listeners.toList().forEach { it() }
        }
    }

    override fun setActive(active: Boolean) {
        if (active == (connection != null)) return
        if (active) {
            update("연결 중")
            val next = Connection(retryNumber = 0)
            connection = next
            next.start()
        } else {
            val previous = connection
            connection = null
            previous?.close()
            update("연결 대기")
        }
    }

    private inner class Connection(private val retryNumber: Int) {
        private val helper = NaviHelper(appContext)
        private var connected = false
        private var guiding = false
        private var closed = false
        private var routePending = false
        private val routeTimeout = Runnable {
            finishRouteRequest()
            update("경로 탐색 응답 없음")
        }
        private val retry = Runnable {
            if (connection === this && !closed) start()
        }
        private val poll = object : Runnable {
            override fun run() {
                helper.getRouteStateInfo()
                helper.getDestinationInfo()
                main.postDelayed(this, 15_000)
            }
        }

        private val listener = object : NaviHelperEventListener {
            override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = dispatch {
                val previouslyGuiding = guiding
                guiding = isRouteGuiding(routeStateInfo.naviMode.name)
                if (!guiding && !routePending) update("경로 없음")
                else if (guiding && !previouslyGuiding) {
                    if (!routePending) update("ETA 수신 대기")
                    helper.getDestinationInfo()
                }
            }

            override fun onDestinationInfo(destinationInfo: DestinationInfo) = dispatch {
                if (guiding) {
                    val target = store
                    when {
                        target == null -> update("매장을 선택하세요")
                        !matchesStoreDestination(target.point, destinationInfo.latitude, destinationInfo.longitude) -> {
                            if (!routePending) update("내비 목적지가 선택한 매장과 다릅니다")
                        }
                        destinationInfo.remainTime < 0 -> update("ETA 정보 없음")
                        else -> {
                            finishRouteRequest()
                            update("경로 안내 중", Reading(destinationInfo.remainTime, SystemClock.elapsedRealtime()))
                        }
                    }
                }
            }

            override fun onRouteStarted(routeStartInfo: RouteStartInfo) = dispatch {
                if (routePending) update("매장 경로 탐색 중")
            }

            override fun onRoutingCompleted() = dispatch {
                if (routePending) update("경로 탐색 완료 · 매장 목적지 확인 중")
                helper.getRouteStateInfo()
                helper.getDestinationInfo()
            }

            override fun onDestinationChanged() = dispatch {
                if (!routePending) update("내비 목적지 확인 중")
                helper.getRouteStateInfo()
                helper.getDestinationInfo()
            }

            override fun onError(errorCode: NaviErrorCode) = dispatch {
                guiding = false
                finishRouteRequest()
                update("내비 오류 (${errorCode.name})")
            }
        }

        fun refreshForStore() {
            finishRouteRequest()
            if (!connected) return
            update(if (store == null) "매장을 선택하세요" else "내비 목적지 확인 중")
            helper.getRouteStateInfo()
            helper.getDestinationInfo()
        }

        fun requestRoute(target: StoreDestination) {
            check(connected && !closed) { "내비 연결을 확인하세요." }
            check(!routePending) { "매장 경로를 탐색 중입니다." }
            routePending = true
            update("매장 경로 요청 중")
            main.postDelayed(routeTimeout, 30_000)
            try {
                helper.requestRoute(RouteInfo(
                    latitude = target.point.latitude,
                    longitude = target.point.longitude,
                    poiName = target.name,
                    poiId = "",
                    address = "",
                    poiSubId = ""
                ))
            } catch (error: RuntimeException) {
                finishRouteRequest()
                update("매장 경로 요청 실패")
            }
        }

        private fun finishRouteRequest() {
            routePending = false
            main.removeCallbacks(routeTimeout)
        }

        fun start() {
            helper.initialize(onConnected = {
                dispatch {
                    connected = true
                    helper.addListener(listener)
                    update("경로 상태 확인 중")
                    poll.run()
                }
            }, onFailed = {
                dispatch {
                    close()
                    val nextRetry = retryNumber + 1
                    val delay = connectionRetryDelayMs(nextRetry)
                    if (delay == null) {
                        update("연결 실패")
                    } else {
                        val replacement = Connection(nextRetry)
                        connection = replacement
                        replacement.scheduleStart(delay)
                        update("연결 재시도 중 ($nextRetry/$MAX_CONNECTION_RETRIES)")
                    }
                }
            })
        }

        fun scheduleStart(delay: Long) {
            main.postDelayed(retry, delay)
        }

        fun close() {
            if (closed) return
            closed = true
            finishRouteRequest()
            main.removeCallbacks(retry)
            main.removeCallbacks(poll)
            if (connected) helper.removeListener(listener)
            helper.release()
        }

        private fun dispatch(action: () -> Unit) {
            main.post { if (connection === this && !closed) action() }
        }
    }
}
