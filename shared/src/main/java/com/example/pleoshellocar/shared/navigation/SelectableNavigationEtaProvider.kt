package com.example.pleoshellocar.shared.navigation

import java.io.Closeable

enum class EtaSource(val label: String) { MOCK("Mock"), TMAP("TMAP"), NAVIGATION("내비") }

class SelectableNavigationEtaProvider(
    private val tmap: TmapNavigationEtaProvider,
    private val navigation: LifecycleNavigationEtaProvider?
) : NavigationEtaProvider, Closeable {
    var source = EtaSource.MOCK
        private set
    val mock get() = tmap.mock
    val hasNavigation get() = navigation != null
    private var active = false
    private val listeners = linkedSetOf<() -> Unit>()
    private val tmapChanged: () -> Unit = { if (source != EtaSource.NAVIGATION) changed() }
    private val navigationChanged: () -> Unit = { if (source == EtaSource.NAVIGATION) changed() }

    init {
        tmap.addListener(tmapChanged)
        navigation?.addListener(navigationChanged)
    }

    private val selected get() = if (source == EtaSource.NAVIGATION) checkNotNull(navigation) else tmap
    override val statusLabel get() = selected.statusLabel
    override fun getEstimatedArrivalMinutes() = selected.getEstimatedArrivalMinutes()
    override fun selectDestination(point: GeoPoint?) = tmap.selectDestination(point)
    override fun addListener(listener: () -> Unit) { listeners.add(listener) }
    override fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun changed() = listeners.toList().forEach { it() }

    fun setSource(value: EtaSource) {
        if (source == value || (value == EtaSource.NAVIGATION && !hasNavigation)) return
        source = value
        tmap.setMode(if (value == EtaSource.TMAP) EtaMode.REAL else EtaMode.MOCK)
        navigation?.setActive(active && value == EtaSource.NAVIGATION)
        changed()
    }

    fun setActive(value: Boolean) {
        active = value
        tmap.setActive(value)
        navigation?.setActive(value && source == EtaSource.NAVIGATION)
    }

    fun refreshTmap() = tmap.refresh()

    override fun close() {
        listeners.clear()
        navigation?.setActive(false)
        navigation?.removeListener(navigationChanged)
        tmap.close()
    }
}
