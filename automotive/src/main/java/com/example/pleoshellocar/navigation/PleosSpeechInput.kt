package com.example.pleoshellocar.navigation

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import com.example.pleoshellocar.shared.domain.SpeechInput
import java.util.Locale

/** One ON_DEVICE engine per application process. SDK callbacks are marshalled to main. */
class PleosSpeechInput(private val context: Context, private val mayListen: () -> Boolean) : SpeechInput {
    private val main = Handler(Looper.getMainLooper())
    private var engine: SpeechToText? = null
    private var ready = false
    private var initializing = false
    private var generation = 0
    private var active: SpeechInput.Listener? = null
    private var sdkListener: ResultListener? = null

    override fun listen(listener: SpeechInput.Listener) = onMain {
        stopCurrent()
        if (!mayListen()) { listener.onError("음성 안내가 끝난 뒤 다시 시도하세요"); return@onMain }
        active = listener
        val token = ++generation
        listener.onState("마이크 연결 중")
        if (ready) request(token) else initialize(token)
    }

    private fun initialize(token: Int) {
        if (initializing) return // onSuccess resumes the latest active generation.
        initializing = true
        val helper = try { engine ?: SpeechToText(context, Mode.ON_DEVICE).also { engine = it } }
            catch (_: RuntimeException) { fail(token, "음성 인식을 시작할 수 없습니다"); return }
        try {
            helper.initialize(object : OnInitializeListener {
                override fun onSuccess() = onMain {
                    initializing = false
                    ready = true
                    if (active != null) request(generation)
                }
                override fun onFailure(errorType: ErrorType) = onMain {
                    initializing = false
                    fail(generation, "음성 인식 연결 실패: $errorType")
                }
            })
        } catch (_: RuntimeException) { initializing = false; fail(token, "음성 인식 연결 실패") }
    }

    private fun request(token: Int) {
        if (generation != token || active == null) return
        if (!mayListen()) { fail(token, "음성 안내가 끝난 뒤 다시 시도하세요"); return }
        val helper = engine ?: run { fail(token, "음성 인식 연결 실패"); return }
        val callback = object : ResultListener {
            override fun onReady() = deliver(token) { it.onState("듣는 중") }
            override fun onStartedRecognition() = deliver(token) { it.onState("말씀하세요") }
            override fun onUpdated(stt: String, completed: Boolean) = deliver(token) {
                if (completed) {
                    val result = stt.trim()
                    stopCurrent()
                    if (result.isEmpty()) it.onError("인식된 문장이 없습니다") else it.onFinal(result)
                } else it.onPartial(stt)
            }
            override fun onEndedRecognition() = Unit
            override fun onUpdatedEpdData(on: Long, off: Long) = Unit
            override fun onUpdatedRms(rms: Double) = Unit
            override fun onError(errorType: ErrorType) = deliver(token) {
                stopCurrent()
                it.onError("음성 인식 오류: $errorType")
            }
        }
        sdkListener = callback
        try {
            helper.setLocale(Locale.KOREA)
            helper.addListener(callback)
            helper.request()
        } catch (_: RuntimeException) { fail(token, "음성 인식을 시작할 수 없습니다") }
    }

    private fun deliver(token: Int, block: (SpeechInput.Listener) -> Unit) = onMain {
        if (generation == token) active?.let(block)
    }

    private fun fail(token: Int, message: String) {
        if (generation != token) return
        val listener = active
        stopCurrent()
        runCatching { engine?.release() }
        engine = null
        ready = false
        listener?.onError(message)
    }

    override fun stop() = onMain { stopCurrent() }

    private fun stopCurrent() {
        generation++
        active = null
        sdkListener?.let { listener -> runCatching { engine?.removeListener(listener) } }
        sdkListener = null
        runCatching { engine?.stop() }
    }

    fun release() = onMain {
        stopCurrent()
        runCatching { engine?.release() }
        engine = null
        ready = false
        initializing = false
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
