package com.example.pleoshellocar.shared.domain

interface CompletedOrderStore {
    fun last(): CompletedOrderSnapshot?
    fun save(order: Order)
}

interface VoiceAnnouncer {
    val isSpeaking: Boolean get() = false
    fun speak(message: String, onFailure: () -> Unit = {})
}
