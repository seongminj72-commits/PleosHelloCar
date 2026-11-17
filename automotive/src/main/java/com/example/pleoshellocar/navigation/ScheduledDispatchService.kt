package com.example.pleoshellocar.navigation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.car.app.notification.CarAppExtender
import androidx.car.app.notification.CarNotificationManager
import androidx.core.app.NotificationCompat
import com.example.pleoshellocar.shared.navigation.EtaSource
import com.example.pleoshellocar.vehicle.VehicleApplication

class ScheduledDispatchService : Service() {
    private val app get() = application as VehicleApplication
    private val main = Handler(Looper.getMainLooper())
    private val reservationChanged: () -> Unit = {
        if (app.scheduledOrders.current?.status?.terminal != false) stopSelf()
    }
    private val evaluate = object : Runnable {
        override fun run() {
            val order = app.scheduledOrders.current ?: return
            val now = SystemClock.elapsedRealtime()
            val eta = app.scheduledOrders.demoEta.sample(order.createdAt, now) ?: when (order.source) {
                EtaSource.NAVIGATION -> app.navigationEta.dispatchEta()
                EtaSource.MOCK -> order.eta
                EtaSource.TMAP -> null
            }
            app.scheduledOrders.tick(now, eta, ::announce)
            if (app.scheduledOrders.current?.status?.terminal == false) main.postDelayed(this, 1_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val order = app.scheduledOrders.current
        if (order == null || order.status.terminal) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "DriveFlow 데모 전달", NotificationManager.IMPORTANCE_LOW))
        startForeground(711, NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Pleos DriveFlow")
            .setContentText("데모 전달 예약 대기 중")
            .build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        app.scheduledOrders.addListener(reservationChanged)
        if (order.source == EtaSource.NAVIGATION) app.navigationEta.setDispatchActive(true)
        main.removeCallbacks(evaluate)
        main.post(evaluate)
        return START_NOT_STICKY
    }

    private fun announce(message: String) {
        android.util.Log.i("ScheduledDispatch", message)
        val requestId = app.scheduledOrders.current?.requestId ?: return
        app.dispatchVoice.speak(message) { app.scheduledOrders.voiceFailed(requestId) }
        runCatching {
            val notice = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Pleos DriveFlow")
                .setContentText(message)
                .extend(CarAppExtender.Builder().build())
            CarNotificationManager.from(this).notify(712, notice)
        }.onFailure { android.util.Log.w("ScheduledDispatch", "Notification unavailable") }
    }

    override fun onDestroy() {
        main.removeCallbacks(evaluate)
        app.scheduledOrders.removeListener(reservationChanged)
        app.navigationEta.setDispatchActive(false)
        app.scheduledOrders.failStart()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "driveflow-scheduled-dispatch"
    }
}
