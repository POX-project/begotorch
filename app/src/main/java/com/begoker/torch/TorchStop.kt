package com.begoker.torch

/**
 * Hardware intensity stops exposed by the Begoker kernel MT6360 flashlight driver.
 *
 * Current per channel formula: 25mA + (selector * 12.5mA)
 * Dual LED total current = Current per channel * 2
 *
 * 20-step grading (levels 0..20).
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
                selector = 1,
                currentMaPerChannel = 37.5,
                totalDualMa = 75.0,
                title = "Bedside",
                subtitle = "37 mA • Dim Reading"
            ),
            TorchStop(
                level = 2,
                selector = 2,
                currentMaPerChannel = 50.0,
                totalDualMa = 100.0,
                title = "Level 2",
                subtitle = "50 mA"
            ),
            TorchStop(
                level = 3,
                selector = 4,
                currentMaPerChannel = 75.0,
                totalDualMa = 150.0,
                title = "Level 3",
                subtitle = "75 mA"
            ),
            TorchStop(
                level = 4,
                selector = 5,
                currentMaPerChannel = 87.5,
                totalDualMa = 175.0,
                title = "Level 4",
                subtitle = "87 mA"
            ),
            TorchStop(
                level = 5,
                selector = 6,
                currentMaPerChannel = 100.0,
                totalDualMa = 200.0,
                title = "Stock Standard",
                subtitle = "100 mA • Xiaomi Factory",
                isStockLimit = true
            ),
            TorchStop(
                level = 6,
                selector = 7,
                currentMaPerChannel = 112.5,
                totalDualMa = 225.0,
                title = "Level 6",
                subtitle = "112 mA"
            ),
            TorchStop(
                level = 7,
                selector = 8,
                currentMaPerChannel = 125.0,
                totalDualMa = 250.0,
                title = "Level 7",
                subtitle = "125 mA"
            ),
            TorchStop(
                level = 8,
                selector = 10,
                currentMaPerChannel = 150.0,
                totalDualMa = 300.0,
                title = "Level 8",
                subtitle = "150 mA"
            ),
            TorchStop(
                level = 9,
                selector = 11,
                currentMaPerChannel = 162.5,
                totalDualMa = 325.0,
                title = "Level 9",
                subtitle = "162 mA"
            ),
            TorchStop(
                level = 10,
                selector = 12,
                currentMaPerChannel = 175.0,
                totalDualMa = 350.0,
                title = "Bright",
                subtitle = "175 mA • Night Walk"
            ),
            TorchStop(
                level = 11,
                selector = 13,
                currentMaPerChannel = 187.5,
                totalDualMa = 375.0,
                title = "Level 11",
                subtitle = "187 mA"
            ),
            TorchStop(
                level = 12,
                selector = 14,
                currentMaPerChannel = 200.0,
                totalDualMa = 400.0,
                title = "Level 12",
                subtitle = "200 mA"
            ),
            TorchStop(
                level = 13,
                selector = 16,
                currentMaPerChannel = 225.0,
                totalDualMa = 450.0,
                title = "Level 13",
                subtitle = "225 mA"
            ),
            TorchStop(
                level = 14,
                selector = 17,
                currentMaPerChannel = 237.5,
                totalDualMa = 475.0,
                title = "Level 14",
                subtitle = "237 mA"
            ),
            TorchStop(
                level = 15,
                selector = 18,
                currentMaPerChannel = 250.0,
                totalDualMa = 500.0,
                title = "High",
                subtitle = "250 mA • Outdoor"
            ),
            TorchStop(
                level = 16,
                selector = 19,
                currentMaPerChannel = 262.5,
                totalDualMa = 525.0,
                title = "Level 16",
                subtitle = "262 mA"
            ),
            TorchStop(
                level = 17,
                selector = 20,
                currentMaPerChannel = 275.0,
                totalDualMa = 550.0,
                title = "Level 17",
                subtitle = "275 mA"
            ),
            TorchStop(
                level = 18,
                selector = 22,
                currentMaPerChannel = 300.0,
                totalDualMa = 600.0,
                title = "Level 18",
                subtitle = "300 mA"
            ),
            TorchStop(
                level = 19,
                selector = 23,
                currentMaPerChannel = 312.5,
                totalDualMa = 625.0,
                title = "Level 19",
                subtitle = "312 mA"
            ),
            TorchStop(
                level = 20,
                selector = 24,
                currentMaPerChannel = 325.0,
                totalDualMa = 650.0,
                title = "Max Overdrive",
                subtitle = "325 mA • 650 mA Dual Peak",
                isOverdrive = true
            )
        )

        val IOS_STOPS = listOf(
            STOPS[0],  // Off
            STOPS[1],  // dim
            STOPS[5],  // stock 100 mA
            STOPS[11], // high
            STOPS[20]  // max overdrive
        )

        fun fromLevel(level: Int): TorchStop {
            val clamped = level.coerceIn(0, 20)
            return STOPS.getOrNull(clamped) ?: STOPS[0]
        }

        fun fromFraction(fraction: Float): TorchStop {
            if (fraction <= 0.02f) return STOPS[0]
            val index = Math.round(fraction * 20).coerceIn(1, 20)
            return STOPS[index]
        }

        fun fromIosFraction(fraction: Float): TorchStop {
            if (fraction <= 0.05f) return IOS_STOPS[0]
            val index = Math.round(fraction * 4).coerceIn(1, 4)
            return IOS_STOPS[index]
        }
    }
}
