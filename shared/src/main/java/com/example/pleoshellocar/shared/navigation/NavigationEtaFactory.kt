package com.example.pleoshellocar.shared.navigation

import androidx.car.app.CarContext

interface NavigationEtaFactory {
    fun createNavigationEta(context: CarContext): LifecycleNavigationEtaProvider
}

interface LifecycleNavigationEtaProvider : NavigationEtaProvider {
    fun setActive(active: Boolean)
}
