package com.example.pleoshellocar.probe

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.tts.TextToSpeech
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttErrorType
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitListener
import ai.pleos.playground.tts.constant.ErrorType as TtsErrorType
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitListener

/**
 * 11/16 Pleos SDK 확인용 화면. 결과는 logcat 태그 [TAG]와 화면에 같이 남는다.
 *
 * 실행 예 (모두 adb):
 *   adb shell am start -n com.example.pleoshellocar.probe/.PleosSdkProbeActivity
 *   adb shell am start -n com.example.pleoshellocar.probe/.PleosSdkProbeActivity --ez route true
 *   adb shell am start -n com.example.pleoshellocar.probe/.PleosSdkProbeActivity --es stt listen
 *
 * extra
 *   navi  (bool, 기본 true)   NaviHelper 연결, 이벤트·정보 조회 기록
 *   route (bool, 기본 false)  공식 가이드 예시 목적지(42dot)로 requestRoute 호출
 *   tts   (bool, 기본 true)   TextToSpeech 초기화 후 한국어 문장 1회 재생
 *   ttsMode (string, 기본 ON_DEVICE)  ON_DEVICE | HYBRID | SERVER
 *   stt   (string, 기본 init) off | init | listen   listen은 마이크 인식 1회
 *
 * 공식 문서에 시그니처가 나온 API만 직접 호출한다. 문서에 없는 부분(정보 조회 메서드 목록,
 * 이벤트 콜백 인자 타입, TTS/STT 이벤트 리스너 타입)은 리플렉션으로 찾아 그대로 기록한다.
 * 그래서 SDK 필드 이름을 몰라도 컴파일되고, 로그에 실제 필드 이름과 값이 찍힌다.
 */
class PleosSdkProbeActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var out: TextView
    private var navi: NaviHelper? = null
    private var naviListener: NaviHelperEventListener? = null
    private var tts: TextToSpeech? = null
    private var stt: SpeechToText? = null
    private var ttsSpoken = false
    private var pollCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        out = TextView(this).apply { textSize = 14f; setPadding(32, 32, 32, 32) }
        setContentView(ScrollView(this).apply { addView(out) })

        log("=== DriveFlow Pleos SDK probe start ===")
        logPermissions()
        if (intent.getBooleanExtra("navi", true)) startNavi(intent.getBooleanExtra("route", false))
        if (intent.getBooleanExtra("tts", true)) startTts(intent.getStringExtra("ttsMode") ?: "ON_DEVICE")
        when (intent.getStringExtra("stt") ?: "init") {
            "init" -> startStt(listen = false)
            "listen" -> startStt(listen = true)
        }
    }

    // ---------------- NaviHelper ----------------

    private fun startNavi(requestRoute: Boolean) {
        log("[NAVI] public methods: " + ProbeReflection.describeMethods(NaviHelper::class.java))
        val helper = NaviHelper(applicationContext)
        navi = helper
        val t0 = System.currentTimeMillis()
        try {
            helper.initialize(
                onConnected = {
                    log("[NAVI] onConnected (+${System.currentTimeMillis() - t0}ms, thread=${Thread.currentThread().name})")
                    main.post { onNaviConnected(helper, requestRoute) }
                },
                onFailed = { error -> log("[NAVI] onFailed: ${error.javaClass.name}: ${error.message}") }
            )
            log("[NAVI] initialize() returned")
        } catch (t: Throwable) {
            log("[NAVI] initialize threw ${t.javaClass.name}: ${t.message}")
        }
        // onConnected가 오지 않는 경우를 구분하기 위한 기록
        main.postDelayed({ if (naviListener == null) log("[NAVI] 10초 안에 onConnected 없음") }, 10_000)
    }

    private fun onNaviConnected(helper: NaviHelper, requestRoute: Boolean) {
        val listener = ProbeReflection.loggingProxy(NaviHelperEventListener::class.java, "NAVI-EVENT", ::log)
        naviListener = listener
        try { helper.addListener(listener); log("[NAVI] addListener ok") }
        catch (t: Throwable) { log("[NAVI] addListener threw ${t.javaClass.name}: ${t.message}") }

        if (requestRoute) {
            // 공식 가이드(Destinations)의 예시 값 그대로
            try {
                helper.requestRoute(
                    RouteInfo(
                        latitude = 37.48544722,
                        longitude = 127.03636666,
                        displayLongitude = null,
                        displayLatitude = null,
                        poiName = "42dot",
                        poiId = "36khp37hh8gqcm5nqhe5",
                        poiSubId = "0",
                        address = "서울시 강남구 남부순환로 2621"
                    )
                )
                log("[NAVI] requestRoute(42dot) 호출. 결과는 onRouteStarted/onRoutingCompleted/onError로 온다")
            } catch (t: Throwable) {
                log("[NAVI] requestRoute threw ${t.javaClass.name}: ${t.message}")
            }
        }
        pollNaviInfo(helper)
    }

    /** get*Info() 계열을 10초마다 12회(2분) 호출. 결과는 대응 콜백(onRouteStateInfo 등)으로 온다. */
    private fun pollNaviInfo(helper: NaviHelper) {
        pollCount++
        log("[NAVI] --- info poll #$pollCount ---")
        ProbeReflection.invokeInfoGetters(helper, ::log)
        if (pollCount < 12) main.postDelayed({ if (navi === helper) pollNaviInfo(helper) }, 10_000)
    }

    // ---------------- TextToSpeech ----------------

    private fun startTts(modeName: String) {
        // 문서에 나온 세 값만 쓴다(Mode가 enum인지 문서에 없어서 valueOf를 쓰지 않음).
        val mode = when (modeName) {
            "HYBRID" -> TtsMode.HYBRID
            "SERVER" -> TtsMode.SERVER
            else -> TtsMode.ON_DEVICE
        }
        log("[TTS] public methods: " + ProbeReflection.describeMethods(TextToSpeech::class.java))
        val engine = try { TextToSpeech(applicationContext, mode) } catch (t: Throwable) {
            log("[TTS] constructor threw ${t.javaClass.name}: ${t.message}"); return
        }
        tts = engine
        try {
            engine.initialize(object : TtsInitListener {
                override fun onSuccess() {
                    log("[TTS] initialize onSuccess (mode=$mode)")
                    main.post { onTtsReady(engine) }
                }
                override fun onFailure(errorType: TtsErrorType) {
                    log("[TTS] initialize onFailure: $errorType")
                }
            })
        } catch (t: Throwable) {
            log("[TTS] initialize threw ${t.javaClass.name}: ${t.message}")
        }
    }

    private fun onTtsReady(engine: TextToSpeech) {
        ProbeReflection.registerListener(engine, "addEventListener", "TTS-EVENT", ::log) { method ->
            if (method == "onReady") main.post { speakOnce(engine, "onReady") }
        }
        runCatching { engine.setLocale(Locale.KOREA) }
            .onSuccess { log("[TTS] setLocale(ko-KR) ok") }
            .onFailure { log("[TTS] setLocale threw ${it.javaClass.name}: ${it.message}") }
        // onReady가 오지 않으면 3초 뒤 한 번 시도해서, 어느 쪽인지 기록한다.
        main.postDelayed({ speakOnce(engine, "3초 대기 후") }, 3_000)
    }

    private fun speakOnce(engine: TextToSpeech, reason: String) {
        if (ttsSpoken) return
        ttsSpoken = true
        try {
            engine.speak("드라이브플로우 음성 안내 테스트입니다. 도착 십 분 전에 주문을 전달합니다.")
            log("[TTS] speak 호출 ($reason)")
        } catch (t: Throwable) {
            log("[TTS] speak threw ${t.javaClass.name}: ${t.message}")
        }
    }

    // ---------------- SpeechToText ----------------

    private fun startStt(listen: Boolean) {
        log("[STT] public methods: " + ProbeReflection.describeMethods(SpeechToText::class.java))
        val engine = try { SpeechToText(applicationContext, SttMode.ON_DEVICE) } catch (t: Throwable) {
            log("[STT] constructor threw ${t.javaClass.name}: ${t.message}"); return
        }
        stt = engine
        try {
            engine.initialize(object : SttInitListener {
                override fun onSuccess() {
                    log("[STT] initialize onSuccess (mode=ON_DEVICE)")
                    main.post {
                        ProbeReflection.registerListener(engine, "addListener", "STT-EVENT", ::log) { }
                        if (listen) startListening(engine)
                    }
                }
                override fun onFailure(errorType: SttErrorType) {
                    log("[STT] initialize onFailure: $errorType")
                }
            })
        } catch (t: Throwable) {
            log("[STT] initialize threw ${t.javaClass.name}: ${t.message}")
        }
    }

    private fun startListening(engine: SpeechToText) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            log("[STT] RECORD_AUDIO 미허용. 권한 요청 후 다시 실행하세요")
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        try { engine.request(); log("[STT] request() 호출. 지금 '아메리카노 한 잔 주문해 줘'라고 말하세요") }
        catch (t: Throwable) { log("[STT] request threw ${t.javaClass.name}: ${t.message}") }
    }

    // ---------------- common ----------------

    private fun logPermissions() {
        listOf(
            "pleos.car.permission.NAVI_ROUTE",
            "pleos.car.permission.NAVI_ROUTE_SEARCH",
            "pleos.car.permission.TTS_SERVICE",
            "pleos.car.permission.STT_SERVICE",
            Manifest.permission.RECORD_AUDIO
        ).forEach { name ->
            val granted = checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED
            val info = runCatching { packageManager.getPermissionInfo(name, 0) }.getOrNull()
            val level = info?.let { "protection=${it.protection}" } ?: "기기에 정의 없음"
            log("[PERM] $name granted=$granted ($level)")
        }
    }

    private fun log(message: String) {
        Log.i(TAG, message)
        main.post { out.append(message + "\n") }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        runCatching { naviListener?.let { navi?.removeListener(it) }; navi?.release() }
        runCatching { tts?.stop(); tts?.release() }
        runCatching { stt?.stop(); stt?.release() }
        navi = null; tts = null; stt = null
        Log.i(TAG, "=== probe released ===")
        super.onDestroy()
    }

    companion object { const val TAG = "DriveFlowProbe" }
}
