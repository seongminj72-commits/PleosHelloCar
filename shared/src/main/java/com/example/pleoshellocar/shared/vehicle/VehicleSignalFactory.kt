package com.example.pleoshellocar.shared.vehicle

import androidx.car.app.CarContext

/** Module boundary: the phone implementation never loads android.car classes. */
interface VehicleSignalFactory {
    fun createVehicleSignal(context: CarContext): ManagedVehicleStateProvider
}
interface ManagedVehicleStateProvider : VehicleStateProvider, AutoCloseable {
    fun start()
    fun stop()
    fun requestPermissions()
}
