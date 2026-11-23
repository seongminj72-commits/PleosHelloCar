package com.example.pleoshellocar.vehicle

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.car.app.CarContext
import com.example.pleoshellocar.navigation.DispatchVoice
import com.example.pleoshellocar.navigation.PleosSpeechInput
import com.example.pleoshellocar.navigation.PreferencesCompletedOrderStore
import com.example.pleoshellocar.navigation.NaviHelperEtaProvider
import com.example.pleoshellocar.navigation.ScheduledDispatchService
import com.example.pleoshellocar.shared.domain.ScheduledOrderRepository
import com.example.pleoshellocar.shared.navigation.NavigationEtaFactory
import com.example.pleoshellocar.shared.vehicle.VehicleSignalFactory

class VehicleApplication : Application(), VehicleSignalFactory, NavigationEtaFactory {
    val dispatchVoice by lazy { DispatchVoice(this) }
    override val voiceAnnouncer get() = dispatchVoice
    private val speechInputOwner = lazy { PleosSpeechInput(this) { !dispatchVoice.isSpeaking } }
    override val speechInput get() = speechInputOwner.value
    override val completedOrders by lazy { PreferencesCompletedOrderStore(this) }
    val navigationEta by lazy { NaviHelperEtaProvider(this) }
    override val scheduledOrders = ScheduledOrderRepository()

    override fun createNavigationEta(context: CarContext) = navigationEta
    override fun currentNavigationEta(now: Long) = navigationEta.dispatchEta(now)

    override fun startScheduledDispatch() {
        ContextCompat.startForegroundService(this, Intent(this, ScheduledDispatchService::class.java))
    }

    override fun createVehicleSignal(context: CarContext) = AaosVehicleStateProvider(context)

    override fun onTerminate() {
        if (speechInputOwner.isInitialized()) speechInputOwner.value.release()
        super.onTerminate()
    }
}
