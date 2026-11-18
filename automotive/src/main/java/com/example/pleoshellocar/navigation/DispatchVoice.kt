package com.example.pleoshellocar.navigation

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import java.util.Locale

/** Owns playback until completion even if the terminal reservation stops its service. */
class DispatchVoice(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var current: Speech? = null

    fun speak(message: String, onFailure: () -> Unit) {
        current?.close()
        current = null
        val speech = try {
            Speech(message, onFailure)
        } catch (_: RuntimeException) {
            onFailure()
            return
        }
        current = speech
        speech.start()
    }

    private inner class Speech(private val message: String, private val onFailure: () -> Unit) {
        private val engine = TextToSpeech(context, Mode.ON_DEVICE)
        private var requested = false
        private val timeout = Runnable { finish(false) }
        private val readyFallback = Runnable { requestSpeech() }
        private val listener = object : EventListener {
            override fun onReady() = runOnMain { requestSpeech() }
            override fun onStart() = runOnMain { Log.i("ScheduledDispatch", "ON_DEVICE TTS started") }
            override fun onDone() = runOnMain { finish(true) }
            override fun onStop() = runOnMain { finish(false) }
            override fun onError(errorType: ErrorType) = runOnMain { finish(false) }
            override fun onUpdatedRms(rms: Double) = Unit
        }

        fun start() {
            main.postDelayed(timeout, 20_000)
            runCatching {
                engine.initialize(object : OnInitializeListener {
                    override fun onSuccess() = runOnMain {
                        runCatching {
                            engine.addEventListener(listener)
                            engine.setLocale(Locale.KOREA)
                            // The probe observed onReady immediately after its delayed speak call.
                            main.postDelayed(readyFallback, 3_000)
                        }.onFailure { finish(false) }
                    }
                    override fun onFailure(errorType: ErrorType) = runOnMain { finish(false) }
                })
            }.onFailure { finish(false) }
        }

        private fun requestSpeech() {
            if (current !== this || requested) return
            requested = true
            main.removeCallbacks(readyFallback)
            runCatching { engine.speak(message) }.onFailure { finish(false) }
        }

        fun close() {
            main.removeCallbacks(readyFallback)
            main.removeCallbacks(timeout)
            runCatching { engine.removeEventListener(listener) }
            runCatching { engine.release() }
        }

        private fun finish(success: Boolean) {
            if (current !== this) return
            current = null
            close()
            Log.i("ScheduledDispatch", if (success) "ON_DEVICE TTS completed" else "ON_DEVICE TTS failed")
            if (!success) onFailure()
        }

        private fun runOnMain(action: () -> Unit) {
            main.post { if (current === this) action() }
        }
    }
}
