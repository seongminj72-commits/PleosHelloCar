package com.example.pleoshellocar.shared.navigation

import androidx.car.app.CarContext
import com.example.pleoshellocar.shared.domain.DispatchEta
import com.example.pleoshellocar.shared.domain.ScheduledOrderRepository
import com.example.pleoshellocar.shared.domain.CompletedOrderStore
import com.example.pleoshellocar.shared.domain.VoiceAnnouncer
import com.example.pleoshellocar.shared.domain.SpeechInput

interface NavigationEtaFactory {
    val scheduledOrders: ScheduledOrderRepository? get() = null
    val completedOrders: CompletedOrderStore? get() = null
    val voiceAnnouncer: VoiceAnnouncer? get() = null
    val speechInput: SpeechInput? get() = null
    fun startScheduledDispatch() {}
    fun currentNavigationEta(now: Long): DispatchEta? = null
    fun createNavigationEta(context: CarContext): LifecycleNavigationEtaProvider
}

interface LifecycleNavigationEtaProvider : NavigationEtaProvider {
    fun setActive(active: Boolean)
    fun requestStoreRoute()
}
