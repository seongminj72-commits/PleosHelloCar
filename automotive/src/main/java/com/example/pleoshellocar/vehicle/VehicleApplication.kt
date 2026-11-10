package com.example.pleoshellocar.vehicle

import android.app.Application
import androidx.car.app.CarContext
import com.example.pleoshellocar.shared.vehicle.VehicleSignalFactory

class VehicleApplication : Application(), VehicleSignalFactory {
    override fun createVehicleSignal(context: CarContext) = AaosVehicleStateProvider(context)
}
