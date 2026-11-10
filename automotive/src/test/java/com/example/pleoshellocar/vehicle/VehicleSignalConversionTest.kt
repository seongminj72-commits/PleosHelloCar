package com.example.pleoshellocar.vehicle

import com.example.pleoshellocar.shared.vehicle.VehicleGear
import org.junit.Assert.*
import org.junit.Test

class VehicleSignalConversionTest {
    @Test fun convertsSuppliedGearMapping() {
        val mapping = mapOf(101 to VehicleGear.P, 102 to VehicleGear.D, 103 to VehicleGear.R, 104 to VehicleGear.N)
        mapping.forEach { (raw, expected) -> assertEquals(expected, convertGear(raw, mapping)) }
    }
    @Test fun unknownGearIsNotParked() {
        assertEquals(VehicleGear.UNKNOWN, convertGear(999, emptyMap()))
        assertEquals(VehicleGear.UNKNOWN, convertGear(null, emptyMap()))
    }
    @Test fun convertsMetersPerSecond() {
        assertEquals(36.0, speedKmh(10.0)!!, 0.0001)
        assertEquals(0.0, speedKmh(0.0)!!, 0.0)
    }
    @Test fun invalidSpeedIsMissing() {
        for (value in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MAX_VALUE))
            assertNull(speedKmh(value))
    }
}
