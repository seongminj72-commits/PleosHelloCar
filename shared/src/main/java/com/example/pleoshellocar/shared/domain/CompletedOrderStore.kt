package com.example.pleoshellocar.shared.domain

interface CompletedOrderStore {
    fun last(): CompletedOrderSnapshot?
    fun save(order: Order)
}

interface VoiceAnnouncer {
    fun speak(message: String, onFailure: () -> Unit = {})
}
