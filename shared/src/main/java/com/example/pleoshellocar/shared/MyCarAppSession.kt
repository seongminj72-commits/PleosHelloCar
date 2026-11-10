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
import com.example.pleoshellocar.shared.domain.DriveFlowController
import com.example.pleoshellocar.shared.vehicle.SwitchableVehicleStateProvider
import com.example.pleoshellocar.shared.navigation.*
import com.example.pleoshellocar.shared.payment.MockPaymentSimulator

class MyCarAppSession(private val vehicleFactory: com.example.pleoshellocar.shared.vehicle.VehicleSignalFactory? = null) : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        val request = AndroidEtaRequest(carContext)
        val navigation = TmapNavigationEtaProvider(request, SystemClock::elapsedRealtime)
        val real = vehicleFactory?.createVehicleSignal(carContext)
        val vehicle = SwitchableVehicleStateProvider(real = real)
        val flow = DriveFlowController(vehicle, navigation, MockPaymentSimulator())
        val handler = Handler(Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() { navigation.refresh(); handler.postDelayed(this, 5_000) }
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
