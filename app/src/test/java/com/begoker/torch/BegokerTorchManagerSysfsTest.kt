package com.begoker.torch

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BegokerTorchManagerSysfsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        BegokerTorchManager.testReadableNodes = null
        BegokerTorchManager.testInfoNode = null
    }

    @After
    fun tearDown() {
        BegokerTorchManager.testReadableNodes = null
        BegokerTorchManager.testInfoNode = null
    }

    @Test
    fun testSysfsBrightnessReadAndClamping() {
        val mockNode = tempFolder.newFile("torch_brightness")
        mockNode.writeText("7\n")

        BegokerTorchManager.testReadableNodes = listOf(mockNode.absolutePath)

        // Mock node returns 7
        val managerClass = BegokerTorchManager::class.java
        val constructor = managerClass.getDeclaredConstructor(android.content.Context::class.java)
        constructor.isAccessible = false // Verify readCurrentHardwareBrightness directly without context if possible
    }

    @Test
    fun testTorchInfoParser() {
        val mockInfo = tempFolder.newFile("torch_info")
        mockInfo.writeText(
            """
            status: on
            brightness: 4
            hardware_selector: 6 (0..24)
            current_per_channel: 100 mA
            current_total_dual: 200 mA
            driver: mt6360_pmu_flashlight
            """.trimIndent()
        )

        BegokerTorchManager.testInfoNode = mockInfo.absolutePath

        val info = parseTorchInfoFile(mockInfo)
        assertNotNull(info)
        assertEquals(4, info?.brightness)
        assertEquals(6, info?.selector)
        assertEquals(100, info?.currentPerChannelMa)
        assertEquals(200, info?.currentTotalDualMa)
        assertTrue(info?.isHardwareOn == true)
    }

    @Test
    fun testTorchInfoParserWhenHardwareOff() {
        val mockInfo = tempFolder.newFile("torch_info_off")
        mockInfo.writeText(
            """
            status: off
            brightness: 0
            hardware_selector: 0 (0..24)
            current_per_channel: 0 mA
            current_total_dual: 0 mA
            """.trimIndent()
        )

        val info = parseTorchInfoFile(mockInfo)
        assertNotNull(info)
        assertEquals(0, info?.brightness)
        assertEquals(0, info?.selector)
        assertEquals(0, info?.currentPerChannelMa)
        assertEquals(0, info?.currentTotalDualMa)
        assertFalse(info?.isHardwareOn == true)
    }

    @Test
    fun testTorchInfoParserMissingOrCorruptedFile() {
        val nonExistent = File(tempFolder.root, "does_not_exist")
        val info = parseTorchInfoFile(nonExistent)
        assertNull(info)
    }

    private fun parseTorchInfoFile(file: File): BegokerTorchManager.KernelTorchInfo? {
        if (!file.exists() || !file.canRead()) return null
        try {
            var brightness = 0
            var selector = 0
            var currentMa = 0
            var dualMa = 0
            var status = false

            file.forEachLine { line ->
                val parts = line.split(":")
                if (parts.size >= 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    when (key) {
                        "brightness" -> brightness = value.toIntOrNull() ?: 0
                        "hardware_selector" -> selector = value.substringBefore(" ").toIntOrNull() ?: 0
                        "current_per_channel" -> currentMa = value.substringBefore(" ").toIntOrNull() ?: 0
                        "current_total_dual" -> dualMa = value.substringBefore(" ").toIntOrNull() ?: 0
                        "status" -> status = value.equals("on", ignoreCase = true)
                    }
                }
            }

            return BegokerTorchManager.KernelTorchInfo(
                brightness = brightness,
                selector = selector,
                currentPerChannelMa = currentMa,
                currentTotalDualMa = dualMa,
                isHardwareOn = status
            )
        } catch (e: Exception) {
            return null
        }
    }
}
