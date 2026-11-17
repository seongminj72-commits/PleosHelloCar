package com.example.pleoshellocar.shared

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.pleoshellocar.shared.car.HomeScreen
import com.example.pleoshellocar.shared.domain.DispatchEta
import com.example.pleoshellocar.shared.domain.DriveFlowController
import com.example.pleoshellocar.shared.data.MockOrderRepository
import com.example.pleoshellocar.shared.vehicle.SwitchableVehicleStateProvider
import com.example.pleoshellocar.shared.vehicle.VehicleSignalFactory
import com.example.pleoshellocar.shared.navigation.*
import com.example.pleoshellocar.shared.payment.MockPaymentSimulator

class MyCarAppSession(
    private val vehicleFactory: VehicleSignalFactory? = null,
    private val navigationFactory: NavigationEtaFactory? = null
) : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        val request = AndroidEtaRequest(carContext)
        val navigation = SelectableNavigationEtaProvider(
            TmapNavigationEtaProvider(request, SystemClock::elapsedRealtime),
            navigationFactory?.createNavigationEta(carContext)
        )
        val real = vehicleFactory?.createVehicleSignal(carContext)
        val vehicle = SwitchableVehicleStateProvider(real = real)
        val scheduled = navigationFactory?.scheduledOrders
        scheduled?.current?.takeUnless { it.status.terminal }?.let { navigation.setSource(it.source) }
        val flow = DriveFlowController(vehicle, navigation, MockPaymentSimulator(),
            orders = scheduled?.orders ?: MockOrderRepository(),
            scheduledOrders = scheduled,
            dispatchEta = { now ->
                when (navigation.source) {
                    EtaSource.NAVIGATION -> navigationFactory?.currentNavigationEta(now)
                    EtaSource.MOCK -> navigation.mock.minutes?.let { DispatchEta(it, now) }
                    EtaSource.TMAP -> null
                }
            },
            clock = SystemClock::elapsedRealtime,
            startDispatch = { navigationFactory?.startScheduledDispatch() }
        )
        val handler = Handler(Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() { navigation.refreshTmap(); handler.postDelayed(this, 5_000) }
        }
        navigation.addListener { Log.i("DriveFlowETA", navigation.statusLabel) }
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { real?.start(); navigation.setActive(true); handler.post(tick) }
            override fun onStop(owner: LifecycleOwner) { real?.stop(); handler.removeCallbacks(tick); navigation.setActive(false) }
            override fun onDestroy(owner: LifecycleOwner) {
                handler.removeCallbacks(tick); navigation.close(); request.close(); vehicle.close(); real?.close()
            }
        })
        return HomeScreen(carContext, flow)
    }
}
