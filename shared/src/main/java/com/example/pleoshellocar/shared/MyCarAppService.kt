package com.example.pleoshellocar.shared

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.example.pleoshellocar.shared.navigation.NavigationEtaFactory
import com.example.pleoshellocar.shared.vehicle.VehicleSignalFactory

class MyCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return MyCarAppSession(application as? VehicleSignalFactory, application as? NavigationEtaFactory)
    }
}