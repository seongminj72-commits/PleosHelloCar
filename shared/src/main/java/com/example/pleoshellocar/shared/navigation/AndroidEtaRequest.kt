package com.example.pleoshellocar.shared.navigation

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Runtime-provisioned, short-lived proxy credential; never a TMAP appKey. */
class AndroidEtaRequest(private val context: Context) : EtaRequest, Closeable {
    private val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    override fun request(destination: GeoPoint, callback: (EtaResult) -> Unit): Closeable {
        val done = AtomicBoolean(false)
        val signal = CancellationSignal()
        val connection = AtomicReference<HttpURLConnection?>()
        lateinit var timeout: Runnable
        fun finish(result: EtaResult) {
            handler.post {
                if (done.compareAndSet(false, true)) {
                    handler.removeCallbacks(timeout); signal.cancel(); callback(result)
                }
            }
        }
        timeout = Runnable { finish(EtaResult.Failure(EtaFailure.NETWORK)) }
        handler.postDelayed(timeout, 25_000)
        val handle = Closeable {
            done.set(true); handler.removeCallbacks(timeout); signal.cancel(); connection.get()?.disconnect()
        }
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            finish(EtaResult.Failure(EtaFailure.PERMISSION)); return handle
        }
        val config = try {
            val file = File(context.filesDir, "eta-proxy.json")
            require(file.length() in 1..8192)
            val json = JSONObject(file.readText())
            val url = URL(json.getString("url"))
            require(url.userInfo == null && url.query == null && url.ref == null)
            require(url.protocol == "https" || (debug && url.protocol == "http" && url.host == "localhost"))
            val token = json.getString("token")
            require(token.matches(Regex("[A-Za-z0-9_-]{32,256}")))
            require(json.getLong("expiresEpochSeconds") > System.currentTimeMillis() / 1000)
            Pair(url, token)
        } catch (_: Exception) { finish(EtaResult.Failure(EtaFailure.CONFIGURATION)); return handle }
        try {
            val manager = context.getSystemService(LocationManager::class.java)
            val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .firstOrNull { manager.isProviderEnabled(it) }
            if (provider == null) { finish(EtaResult.Failure(EtaFailure.LOCATION)); return handle }
            manager.getCurrentLocation(provider,
                android.location.LocationRequest.Builder(0).setDurationMillis(10_000).build(),
                signal, context.mainExecutor) { location ->
                if (done.get()) return@getCurrentLocation
                if (location == null || !location.hasAccuracy() || location.accuracy > 100 ||
                    (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) !in 0..30_000_000_000L) {
                    finish(EtaResult.Failure(EtaFailure.LOCATION)); return@getCurrentLocation
                }
                val origin = GeoPoint(location.latitude, location.longitude)
                val simulated = location.isMock || android.os.Build.FINGERPRINT.contains("generic") ||
                    android.os.Build.MODEL.contains("sdk", ignoreCase = true) ||
                    android.os.Build.HARDWARE in setOf("ranchu", "goldfish")
                worker.execute {
                    if (done.get()) return@execute
                    try {
                        val conn = config.first.openConnection() as HttpURLConnection
                        connection.set(conn)
                        if (done.get()) { conn.disconnect(); return@execute }
                        conn.requestMethod = "POST"; conn.instanceFollowRedirects = false
                        conn.connectTimeout = 5_000; conn.readTimeout = 8_000; conn.doOutput = true
                        conn.useCaches = false
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.setRequestProperty("Authorization", "Bearer ${config.second}")
                        val body = JSONObject().put("origin", JSONObject().put("latitude", origin.latitude).put("longitude", origin.longitude))
                            .put("destination", JSONObject().put("latitude", destination.latitude).put("longitude", destination.longitude))
                        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                        val result = when (conn.responseCode) {
                            200 -> {
                                val bytes = conn.inputStream.use { it.readNBytes(8193) }
                                require(bytes.size <= 8192)
                                val json = JSONObject(String(bytes, Charsets.UTF_8))
                                val source = json.getString("source")
                                require(source == "TMAP" || (debug && source == "TEST_FIXTURE"))
                                val number = json.get("durationSeconds")
                                require(number is Number && number.toDouble().isFinite() && number.toDouble() == number.toInt().toDouble())
                                EtaResult.Success((number as Number).toInt(), simulated, source == "TEST_FIXTURE")
                            }
                            401, 403 -> EtaResult.Failure(EtaFailure.AUTH)
                            429 -> EtaResult.Failure(EtaFailure.RATE_LIMIT)
                            else -> EtaResult.Failure(EtaFailure.SERVER)
                        }
                        finish(result)
                    } catch (_: java.io.IOException) { finish(EtaResult.Failure(EtaFailure.NETWORK)) }
                    catch (_: Exception) { finish(EtaResult.Failure(EtaFailure.INVALID_RESPONSE)) }
                    finally { connection.get()?.disconnect() }
                }
            }
        } catch (_: SecurityException) { finish(EtaResult.Failure(EtaFailure.PERMISSION)) }
        catch (_: Exception) { finish(EtaResult.Failure(EtaFailure.LOCATION)) }
        return handle
    }
    override fun close() { worker.shutdownNow() }
}
