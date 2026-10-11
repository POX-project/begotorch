package com.begoker.torch

/**
 * Hardware intensity stops exposed by the Begoker kernel MT6360 flashlight driver.
 *
 * Current per channel formula: 25mA + (selector * 12.5mA)
 * Dual LED total current = Current per channel * 2
 */
data class TorchStop(
    val level: Int,
    val selector: Int,
    val currentMaPerChannel: Double,
    val totalDualMa: Double,
    val title: String,
    val subtitle: String,
    val isStockLimit: Boolean = false,
    val isOverdrive: Boolean = false
) {
    val currentMaFormatted: String
        get() = "${currentMaPerChannel.toInt()} mA"

    val dualMaFormatted: String
        get() = "${totalDualMa.toInt()} mA"

    companion object {
        val STOPS = listOf(
            TorchStop(
                level = 0,
                selector = 0,
                currentMaPerChannel = 0.0,
                totalDualMa = 0.0,
                title = "Off",
                subtitle = "Turned Off"
            ),
            TorchStop(
                level = 1,
                selector = 0,
                currentMaPerChannel = 25.0,
                totalDualMa = 50.0,
                title = "Bedside",
                subtitle = "25 mA • Dim Reading"
            ),
            TorchStop(
                level = 2,
                selector = 2,
                currentMaPerChannel = 50.0,
                totalDualMa = 100.0,
                title = "Soft",
                subtitle = "50 mA • Soft Glow"
            ),
            TorchStop(
                level = 3,
                selector = 4,
                currentMaPerChannel = 75.0,
                totalDualMa = 150.0,
                title = "Low",
                subtitle = "75 mA • Ambient"
            ),
            TorchStop(
                level = 4,
                selector = 6,
                currentMaPerChannel = 100.0,
                totalDualMa = 200.0,
                title = "Stock Standard",
                subtitle = "100 mA • Xiaomi Factory",
                isStockLimit = true
            ),
            TorchStop(
                level = 5,
                selector = 9,
                currentMaPerChannel = 137.5,
                totalDualMa = 275.0,
                title = "Medium",
                subtitle = "138 mA • Enhanced"
            ),
            TorchStop(
                level = 6,
                selector = 12,
                currentMaPerChannel = 175.0,
                totalDualMa = 350.0,
                title = "Bright",
                subtitle = "175 mA • Night Walk"
            ),
            TorchStop(
                level = 7,
                selector = 15,
                currentMaPerChannel = 212.5,
                totalDualMa = 425.0,
                title = "High",
                subtitle = "213 mA • Outdoor"
            ),
            TorchStop(
                level = 8,
                selector = 18,
                currentMaPerChannel = 250.0,
                totalDualMa = 500.0,
                title = "Very High",
                subtitle = "250 mA • Spotlight"
            ),
            TorchStop(
                level = 9,
                selector = 21,
                currentMaPerChannel = 287.5,
                totalDualMa = 575.0,
                title = "Ultra",
                subtitle = "288 mA • High Beam"
            ),
            TorchStop(
                level = 10,
                selector = 24,
                currentMaPerChannel = 325.0,
                totalDualMa = 650.0,
                title = "Max Overdrive",
                subtitle = "325 mA • 650 mA Dual Peak",
                isOverdrive = true
            )
        )

        /**
         * The 4 classic iOS Control Center milestone stops:
         * Stop 0: Off
         * Stop 1: 25 mA (Level 1)
         * Stop 2: 100 mA (Level 4, Xiaomi Stock)
         * Stop 3: 213 mA (Level 7, High)
         * Stop 4: 325 mA (Level 10, Max Overdrive)
         */
        val IOS_STOPS = listOf(
            STOPS[0],  // Off
            STOPS[1],  // 25 mA
            STOPS[4],  // 100 mA
            STOPS[7],  // 213 mA
            STOPS[10]  // 325 mA Max
        )

        fun fromLevel(level: Int): TorchStop {
            val clamped = level.coerceIn(0, 10)
            return STOPS.getOrNull(clamped) ?: STOPS[0]
        }

        fun fromFraction(fraction: Float): TorchStop {
            if (fraction <= 0.02f) return STOPS[0]
            val index = Math.round(fraction * 10).coerceIn(1, 10)
            return STOPS[index]
        }

        fun fromIosFraction(fraction: Float): TorchStop {
            if (fraction <= 0.05f) return IOS_STOPS[0]
            val index = Math.round(fraction * 4).coerceIn(1, 4)
            return IOS_STOPS[index]
        }
    }
}
