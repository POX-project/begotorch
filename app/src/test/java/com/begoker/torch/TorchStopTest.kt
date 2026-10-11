package com.begoker.torch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorchStopTest {

    @Test
    fun testStopsCountAndBoundaries() {
        assertEquals(11, TorchStop.STOPS.size)

        val off = TorchStop.fromLevel(0)
        assertEquals(0, off.level)
        assertEquals(0.0, off.currentMaPerChannel, 0.001)

        val max = TorchStop.fromLevel(10)
        assertEquals(10, max.level)
        assertEquals(325.0, max.currentMaPerChannel, 0.001)
        assertEquals(650.0, max.totalDualMa, 0.001)
        assertTrue(max.isOverdrive)
    }

    @Test
    fun testStockXiaomiStop() {
        val stock = TorchStop.fromLevel(4)
        assertEquals(4, stock.level)
        assertEquals(6, stock.selector)
        assertEquals(100.0, stock.currentMaPerChannel, 0.001)
        assertEquals(200.0, stock.totalDualMa, 0.001)
        assertTrue(stock.isStockLimit)
    }

    @Test
    fun testFractionMapping() {
        // Zero or near zero
        assertEquals(0, TorchStop.fromFraction(0.0f).level)
        assertEquals(0, TorchStop.fromFraction(0.01f).level)

        // Mid points
        assertEquals(1, TorchStop.fromFraction(0.1f).level)
        assertEquals(4, TorchStop.fromFraction(0.4f).level)
        assertEquals(7, TorchStop.fromFraction(0.7f).level)
        assertEquals(10, TorchStop.fromFraction(1.0f).level)
    }

    @Test
    fun testIosMilestoneStops() {
        assertEquals(5, TorchStop.IOS_STOPS.size)
        assertEquals(0, TorchStop.IOS_STOPS[0].level) // Off
        assertEquals(1, TorchStop.IOS_STOPS[1].level) // 25 mA
        assertEquals(4, TorchStop.IOS_STOPS[2].level) // 100 mA (Stock Xiaomi)
        assertEquals(7, TorchStop.IOS_STOPS[3].level) // 213 mA
        assertEquals(10, TorchStop.IOS_STOPS[4].level) // 325 mA Overdrive
    }

    @Test
    fun testClamping() {
        assertEquals(0, TorchStop.fromLevel(-5).level)
        assertEquals(10, TorchStop.fromLevel(99).level)
    }
}
