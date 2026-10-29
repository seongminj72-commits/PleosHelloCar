package com.example.pleoshellocar.shared

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import com.example.pleoshellocar.shared.car.HomeScreen
import com.example.pleoshellocar.shared.domain.DriveFlowController
import com.example.pleoshellocar.shared.vehicle.MockVehicleStateProvider
import com.example.pleoshellocar.shared.navigation.MockNavigationEtaProvider
import com.example.pleoshellocar.shared.payment.MockPaymentSimulator

class MyCarAppSession : Session() {
    private val flow = DriveFlowController(MockVehicleStateProvider(), MockNavigationEtaProvider(), MockPaymentSimulator())
    override fun onCreateScreen(intent: Intent): Screen = HomeScreen(carContext, flow)
}
