package com.example.pleoshellocar.shared.domain

/** Completed text is only a proposal; the caller must show confirmation before execution. */
interface SpeechInput {
    interface Listener {
        fun onState(message: String)
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(message: String)
    }

    fun listen(listener: Listener)
    fun stop()
}
