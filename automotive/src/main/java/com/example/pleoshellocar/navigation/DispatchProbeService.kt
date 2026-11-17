package com.example.pleoshellocar.navigation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.car.app.notification.CarAppExtender
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.pleoshellocar.shared.navigation.GeoPoint

/** Time-limited Stage 0 emulator measurement. */
class DispatchProbeService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var eta: NaviHelperEtaProvider? = null
    private val stopProbe = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val name = intent?.getStringExtra("store")
        if (name == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val point = GeoPoint(intent.getDoubleExtra("latitude", Double.NaN), intent.getDoubleExtra("longitude", Double.NaN))
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel("dispatch-fgs-probe", "DriveFlow 경로 시험", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
        val notice = NotificationCompat.Builder(this, channel.id)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Pleos DriveFlow")
            .setContentText("데모 경로 확인 중")
            .extend(CarAppExtender.Builder().build())
            .build()
        startForeground(711, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        Log.i("DispatchProbe", "Foreground Service started")
        eta?.setActive(false)
        eta = NaviHelperEtaProvider(this, probeOwner = "service").also {
            it.selectStoreDestination(name, point)
            it.setActive(true)
        }
        main.removeCallbacks(stopProbe)
        main.postDelayed(stopProbe, 12 * 60_000L)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacks(stopProbe)
        eta?.setActive(false)
        Log.i("DispatchProbe", "Foreground Service destroyed")
        super.onDestroy()
    }

    companion object {
        fun start(context: Context, name: String, point: GeoPoint) {
            val request = Intent(context, DispatchProbeService::class.java)
                .putExtra("store", name)
                .putExtra("latitude", point.latitude)
                .putExtra("longitude", point.longitude)
            runCatching { ContextCompat.startForegroundService(context, request) }
                .onFailure { Log.w("DispatchProbe", "Foreground Service start failed: ${it.javaClass.simpleName}") }
        }
    }
}
