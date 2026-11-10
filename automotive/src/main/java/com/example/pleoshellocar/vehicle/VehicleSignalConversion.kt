package com.example.pleoshellocar.vehicle

import com.example.pleoshellocar.shared.vehicle.VehicleGear

/** The adapter supplies platform constants; this function needs no Android runtime. */
internal fun convertGear(value: Int?, mapping: Map<Int, VehicleGear>): VehicleGear =
    mapping[value] ?: VehicleGear.UNKNOWN

internal fun speedKmh(metersPerSecond: Double?): Double? = metersPerSecond
    ?.takeIf { it.isFinite() && it >= 0 }
    ?.times(3.6)?.takeIf { it.isFinite() }
