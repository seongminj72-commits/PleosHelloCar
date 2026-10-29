package com.example.pleoshellocar.shared.navigation

interface NavigationEtaProvider { fun getEstimatedArrivalMinutes(): Int? }
class MockNavigationEtaProvider(var minutes: Int? = 8) : NavigationEtaProvider {
    override fun getEstimatedArrivalMinutes() = minutes
}
