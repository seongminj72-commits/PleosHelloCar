package com.example.pleoshellocar.shared.navigation

import androidx.car.app.CarContext
import com.example.pleoshellocar.shared.domain.DispatchEta
import com.example.pleoshellocar.shared.domain.ScheduledOrderRepository

interface NavigationEtaFactory {
    val scheduledOrders: ScheduledOrderRepository? get() = null
    fun startScheduledDispatch() {}
    fun currentNavigationEta(): DispatchEta? = null
    fun createNavigationEta(context: CarContext): LifecycleNavigationEtaProvider
}

interface LifecycleNavigationEtaProvider : NavigationEtaProvider {
    fun setActive(active: Boolean)
    fun requestStoreRoute()
}
