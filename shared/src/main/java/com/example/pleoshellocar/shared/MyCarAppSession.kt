package com.example.pleoshellocar.shared

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import androidx.car.app.CarToast
import androidx.car.app.notification.CarAppExtender
import androidx.car.app.notification.CarNotificationManager
import androidx.core.app.NotificationCompat
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
        val flow = DriveFlowController(vehicle, navigation, MockPaymentSimulator())
        val handler = Handler(Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() { navigation.refreshTmap(); handler.postDelayed(this, 5_000) }
        }
        navigation.addListener { Log.i("DriveFlowETA", navigation.statusLabel) }
        val sessionId = System.identityHashCode(this)
        val notification = Runnable {
            Log.i("DispatchProbe", "session=$sessionId notification probe")
            val manager = carContext.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel("dispatch-probe", "DriveFlow 데모 확인", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
            val notice = NotificationCompat.Builder(carContext, channel.id)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Pleos DriveFlow")
                .setContentText("데모 전달 알림 확인")
                .extend(CarAppExtender.Builder().build())
            runCatching { CarNotificationManager.from(carContext).notify(710, notice) }
                .onFailure { Log.w("DispatchProbe", "notify failed: ${it.javaClass.simpleName}") }
        }
        val toast = Runnable {
            Log.i("DispatchProbe", "session=$sessionId CarToast probe")
            runCatching { CarToast.makeText(carContext, "데모 전달 토스트 확인", CarToast.LENGTH_LONG).show() }
                .onFailure { Log.w("DispatchProbe", "toast failed: ${it.javaClass.simpleName}") }
        }
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                Log.i("DispatchProbe", "session=$sessionId onCreate")
                navigation.setActive(true)
            }
            override fun onStart(owner: LifecycleOwner) {
                Log.i("DispatchProbe", "session=$sessionId onStart")
                handler.removeCallbacks(notification)
                handler.removeCallbacks(toast)
                real?.start()
                handler.post(tick)
            }
            override fun onStop(owner: LifecycleOwner) {
                Log.i("DispatchProbe", "session=$sessionId onStop source=${navigation.source}")
                real?.stop()
                handler.removeCallbacks(tick)
                if (navigation.source == EtaSource.NAVIGATION) {
                    handler.postDelayed(notification, 20_000)
                    handler.postDelayed(toast, 40_000)
                }
            }
            override fun onDestroy(owner: LifecycleOwner) {
                Log.i("DispatchProbe", "session=$sessionId onDestroy")
                handler.removeCallbacks(notification)
                handler.removeCallbacks(toast)
                handler.removeCallbacks(tick); navigation.close(); request.close(); vehicle.close(); real?.close()
            }
        })
        return HomeScreen(carContext, flow)
    }
}
