package com.example.pleoshellocar.shared.navigation

interface NavigationEtaProvider {
    fun getEstimatedArrivalMinutes(): Int?
    val statusLabel: String get() = "[Mock ETA] 오프라인 데모"
    fun selectDestination(point: GeoPoint?) {}
    fun addListener(listener: () -> Unit) {}
    fun removeListener(listener: () -> Unit) {}
}
class MockNavigationEtaProvider(var minutes: Int? = 8) : NavigationEtaProvider {
    override fun getEstimatedArrivalMinutes() = minutes
}
data class GeoPoint(val latitude: Double, val longitude: Double) {
    init { require(latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0) }
}
