package com.example.pleoshellocar.vehicle

import android.car.Car
import android.car.VehicleGear as AaosGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.car.app.CarContext
import com.example.pleoshellocar.shared.vehicle.*

/** All signal state and notifications are confined to the main looper. */
class AaosVehicleStateProvider(private val context: CarContext) : ManagedVehicleStateProvider {
    private val handler = Handler(Looper.getMainLooper())
    private val resolver = VehicleStateResolver()
    private val listeners = linkedSetOf<() -> Unit>()
    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var active = false
    private var closed = false
    private var callback: CarPropertyManager.CarPropertyEventCallback? = null
    private var connection = VehicleConnection.DISCONNECTED
    private var gear = VehicleGear.UNKNOWN
    private var speed: Double? = null
    private var receivedAt: Long? = null
    private val expiry = Runnable { if (active && !closed) notifyChanged() }
    private val mapping = mapOf(AaosGear.GEAR_PARK to VehicleGear.P,
        AaosGear.GEAR_DRIVE to VehicleGear.D, AaosGear.GEAR_REVERSE to VehicleGear.R,
        AaosGear.GEAR_NEUTRAL to VehicleGear.N)
    override val source = VehicleStateSource.VEHICLE_SDK
    private fun snapshot() = resolver.resolve(gear, speed, receivedAt, SystemClock.elapsedRealtime(), connection)
    override fun getVehicleState() = snapshot().state
    override val reason get() = "AAOS 차량 API · ${snapshot().reason}"
    override fun addListener(listener: () -> Unit) { if (!closed) listeners.add(listener) }
    override fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyChanged() { listeners.toList().forEach { listener -> runCatching { listener() } } }
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post(block)
    }
    private fun reset(state: VehicleConnection) {
        handler.removeCallbacks(expiry)
        connection = state; gear = VehicleGear.UNKNOWN; speed = null; receivedAt = null
        notifyChanged()
    }
    private fun failure(error: Throwable) = reset(if (error is SecurityException)
        VehicleConnection.PERMISSION_DENIED else VehicleConnection.INITIALIZATION_FAILED)
    override fun start() = onMain {
        if (!closed && !active) {
            active = true
            try {
                if (context.checkSelfPermission(Car.PERMISSION_SPEED) != PackageManager.PERMISSION_GRANTED) {
                    reset(VehicleConnection.PERMISSION_DENIED)
                } else if (car == null) {
                    car = Car.createCar(context, handler, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                        onMain {
                            if (!closed) {
                                if (!ready) { unsubscribe(); reset(VehicleConnection.DISCONNECTED) }
                                else if (active) attach(connected)
                            }
                        }
                    }
                    if (car == null) reset(VehicleConnection.INITIALIZATION_FAILED)
                } else if (car?.isConnected == true) attach(car!!)
                else reset(VehicleConnection.DISCONNECTED)
            } catch (e: Exception) { failure(e) }
            catch (e: LinkageError) { failure(e) }
        }
    }
    @Suppress("DEPRECATION") // API 34 supports registerCallback; periodic duplicate speed samples are required.
    private fun attach(connected: Car) {
        try {
            unsubscribe()
            reset(VehicleConnection.CONNECTED)
            val properties = connected.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                ?: error("CarPropertyManager unavailable")
            manager = properties
            val events = object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) = onMain {
                    if (active && !closed && callback === this) {
                        try { receive(value) } catch (e: Exception) { failure(e) }
                    }
                }
                override fun onErrorEvent(propertyId: Int, areaId: Int) = onMain {
                    if (active && !closed && callback === this) reset(VehicleConnection.INITIALIZATION_FAILED)
                }
            }
            callback = events
            check(properties.registerCallback(events, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE))
            check(properties.registerCallback(events, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL))
            // A one-time initial read; subsequent gear changes arrive through the callback.
            if (gear == VehicleGear.UNKNOWN) receive(properties.getProperty(Integer::class.java,
                VehiclePropertyIds.GEAR_SELECTION, 0))
        } catch (e: Exception) { unsubscribe(); failure(e) }
        catch (e: LinkageError) { unsubscribe(); failure(e) }
    }
    private fun receive(value: CarPropertyValue<*>?) {
        if (value == null) { reset(VehicleConnection.INITIALIZATION_FAILED); return }
        val available = value.status == CarPropertyValue.STATUS_AVAILABLE
        when (value.propertyId) {
            VehiclePropertyIds.GEAR_SELECTION -> gear = if (available) convertGear(value.value as? Int, mapping) else VehicleGear.UNKNOWN
            VehiclePropertyIds.PERF_VEHICLE_SPEED -> {
                speed = if (available) speedKmh((value.value as? Float)?.toDouble()) else null
                receivedAt = SystemClock.elapsedRealtime()
                handler.removeCallbacks(expiry)
                handler.postDelayed(expiry, 3_000)
            }
            else -> return
        }
        notifyChanged()
    }
    private fun unsubscribe() {
        val old = callback; callback = null
        try { if (old != null) manager?.unregisterCallback(old) } catch (_: Exception) { /* Stop remains fail-closed. */ }
        catch (_: LinkageError) { /* Missing API must not terminate the app. */ }
        manager = null
        handler.removeCallbacks(expiry)
    }
    override fun stop() = onMain {
        active = false; unsubscribe(); reset(VehicleConnection.DISCONNECTED)
    }
    override fun requestPermissions() = onMain {
        if (!closed) try {
            context.requestPermissions(listOf(Car.PERMISSION_SPEED)) { _, _ ->
                onMain { if (!closed) { val restart = active; stop(); if (restart) start() } }
            }
        } catch (e: Exception) { failure(e) }
    }
    override fun close() = onMain {
        if (!closed) {
            stop(); closed = true
            try { car?.disconnect() } catch (_: Exception) { /* Already stopped. */ }
            catch (_: LinkageError) { /* Already stopped. */ }
            car = null; listeners.clear()
        }
    }
}
