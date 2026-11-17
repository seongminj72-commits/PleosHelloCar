package com.example.pleoshellocar.vehicle

import android.app.Application
import androidx.car.app.CarContext
import com.example.pleoshellocar.navigation.NaviHelperEtaProvider
import com.example.pleoshellocar.shared.navigation.NavigationEtaFactory
import com.example.pleoshellocar.shared.vehicle.VehicleSignalFactory

class VehicleApplication : Application(), VehicleSignalFactory, NavigationEtaFactory {
    override fun createNavigationEta(context: CarContext) = NaviHelperEtaProvider(context)

    override fun createVehicleSignal(context: CarContext) = AaosVehicleStateProvider(context)
}
