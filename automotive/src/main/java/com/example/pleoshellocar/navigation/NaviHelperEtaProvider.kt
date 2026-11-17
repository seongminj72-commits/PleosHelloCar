package com.example.pleoshellocar.navigation

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import com.example.pleoshellocar.shared.navigation.LifecycleNavigationEtaProvider

/** Reads the Navigation app's active destination. State is confined to the main thread. */
class NaviHelperEtaProvider(context: Context) : LifecycleNavigationEtaProvider {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val listeners = linkedSetOf<() -> Unit>()
    private var connection: Connection? = null
    private var reading: Reading? = null
    private var state = "연결 대기"

    private data class Reading(val seconds: Int, val receivedAt: Long)

    override val statusLabel: String
        get() = "[내비 ETA] " + when {
            getEstimatedArrivalMinutes() != null -> "현재 내비 목적지까지 약 ${getEstimatedArrivalMinutes()}분"
            reading != null -> "ETA 유효시간 만료"
            else -> state
        }

    override fun getEstimatedArrivalMinutes(): Int? =
        reading?.takeUnless { isEtaExpired(it.receivedAt, SystemClock.elapsedRealtime()) }?.let { etaMinutes(it.seconds) }

    override fun addListener(listener: () -> Unit) { listeners.add(listener) }
    override fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

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
            val next = Connection()
            connection = next
            next.start()
        } else {
            val previous = connection
            connection = null
            previous?.close()
            update("연결 대기")
        }
    }

    private inner class Connection {
        private val helper = NaviHelper(appContext)
        private var connected = false
        private var guiding = false
        private var retriesScheduled = 0
        private val retry = Runnable { start() }
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
                if (!guiding) update("경로 없음")
                else if (!previouslyGuiding) {
                    update("ETA 수신 대기")
                    helper.getDestinationInfo()
                }
            }

            override fun onDestinationInfo(destinationInfo: DestinationInfo) = dispatch {
                if (guiding) {
                    val seconds = destinationInfo.remainTime
                    if (seconds < 0) update("ETA 정보 없음")
                    else update("경로 안내 중", Reading(seconds, SystemClock.elapsedRealtime()))
                }
            }

            override fun onError(errorCode: NaviErrorCode) = dispatch {
                guiding = false
                update("내비 오류 (${errorCode.name})")
            }
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
                    val nextRetry = retriesScheduled + 1
                    val delay = connectionRetryDelayMs(nextRetry)
                    if (delay == null) {
                        update("연결 실패")
                    } else {
                        retriesScheduled = nextRetry
                        main.postDelayed(retry, delay)
                        update("연결 재시도 중 ($nextRetry/$MAX_CONNECTION_RETRIES)")
                    }
                }
            })
        }

        fun close() {
            main.removeCallbacks(retry)
            main.removeCallbacks(poll)
            if (connected) helper.removeListener(listener)
            helper.release()
        }

        private fun dispatch(action: () -> Unit) {
            main.post { if (connection === this) action() }
        }
    }
}
