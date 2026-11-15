package com.begoker.torch

import android.content.Context
import android.content.SharedPreferences
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * High-performance hardware bridge for the Begoker kernel flashlight subsystem.
 *
 * Exposes multi-tiered brightness control via rootless sysfs/procfs nodes:
 * - /proc/perfmgr/torch_brightness (0666)
 * - /proc/perfmgr/flashlight_brightness (0666)
 * - /sys/kernel/torch_brightness (0666)
 * - /sys/class/leds/flashlight/brightness (0666)
 */
class BegokerTorchManager private constructor(private val context: Context) {

    interface TorchStateListener {
        fun onTorchStateChanged(level: Int, stop: TorchStop)
    }

    data class KernelTorchInfo(
        val brightness: Int,
        val selector: Int,
        val currentPerChannelMa: Int,
        val currentTotalDualMa: Int,
        val isHardwareOn: Boolean
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<TorchStateListener>()

    @Volatile
    private var currentLevel: Int = 0

    @Volatile
    private var isWriting: Boolean = false

    @Volatile
    private var suAvailable: Boolean = true

    @Volatile
    private var cachedFlashCameraId: String? = null

    @Volatile
    private var cachedMaxCameraTorchLevel: Int = 1

    private var cameraTorchCallback: CameraManager.TorchCallback? = null

    init {
        // Asynchronously read initial hardware state to prevent main thread ANR / StrictMode violations
        executor.execute {
            val hw = readCurrentHardwareBrightness()
            if (hw != currentLevel) {
                currentLevel = hw
                val stop = TorchStop.fromLevel(hw)
                mainHandler.post {
                    for (listener in listeners) {
                        listener.onTorchStateChanged(hw, stop)
                    }
                }
            }
        }
        initCameraManager()
    }

    private fun initCameraManager() {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                if (chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                    cachedFlashCameraId = id
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        cachedMaxCameraTorchLevel =
                            chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                    }
                    break
                }
            }

            val callback = object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == cachedFlashCameraId) {
                        // If state changed externally (e.g. system QS tile or camera app)
                        val targetLevel = if (enabled) {
                            if (currentLevel > 0) currentLevel else getLastSavedLevel()
                        } else {
                            0
                        }
                        if (!isWriting && currentLevel != targetLevel) {
                            currentLevel = targetLevel
                            val stop = TorchStop.fromLevel(targetLevel)
                            mainHandler.post {
                                for (listener in listeners) {
                                    listener.onTorchStateChanged(targetLevel, stop)
                                }
                            }
                        }
                    }
                }

                override fun onTorchModeUnavailable(cameraId: String) {
                    if (cameraId == cachedFlashCameraId) {
                        if (!isWriting && currentLevel != 0) {
                            currentLevel = 0
                            val stop = TorchStop.fromLevel(0)
                            mainHandler.post {
                                for (listener in listeners) {
                                    listener.onTorchStateChanged(0, stop)
                                }
                            }
                        }
                    }
                }
            }
            cameraTorchCallback = callback
            cameraManager.registerTorchCallback(callback, mainHandler)
        } catch (e: Exception) {
            Log.w(TAG, "CameraManager init failed: ${e.message}")
        }
    }

    fun addListener(listener: TorchStateListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
        listener.onTorchStateChanged(currentLevel, TorchStop.fromLevel(currentLevel))
    }

    fun removeListener(listener: TorchStateListener) {
        listeners.remove(listener)
    }

    fun isTorchOn(): Boolean = currentLevel > 0

    fun getCurrentLevel(): Int = currentLevel

    fun getCurrentStop(): TorchStop = TorchStop.fromLevel(currentLevel)

    fun getLastSavedLevel(): Int {
        val saved = prefs.getInt(KEY_LAST_LEVEL, DEFAULT_RESTORE_LEVEL)
        return if (saved in 1..10) saved else DEFAULT_RESTORE_LEVEL
    }

    fun saveLastSavedLevel(level: Int) {
        if (level in 1..10) {
            prefs.edit().putInt(KEY_LAST_LEVEL, level).apply()
        }
    }

    /**
     * Sets flashlight intensity stop (0 for OFF, 1..10 for graded kernel levels).
     */
    fun setBrightness(level: Int, callback: ((Boolean) -> Unit)? = null) {
        val target = level.coerceIn(0, 10)
        currentLevel = target
        if (target > 0) {
            saveLastSavedLevel(target)
        }

        // Notify listeners immediately on main thread for 0-latency UI responsiveness
        val stop = TorchStop.fromLevel(target)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            for (listener in listeners) {
                listener.onTorchStateChanged(target, stop)
            }
        } else {
            mainHandler.post {
                for (listener in listeners) {
                    listener.onTorchStateChanged(target, stop)
                }
            }
        }

        executor.execute {
            // If target was superseded while waiting in queue, skip writing stale value
            if (target != currentLevel) {
                Log.d(TAG, "Skipping superseded write $target (latest is $currentLevel)")
                return@execute
            }
            isWriting = true
            val success = try {
                writeKernelBrightness(target)
            } finally {
                isWriting = false
            }

            if (!success) {
                // If write failed, resync state to real hardware status
                val actual = readCurrentHardwareBrightness()
                if (actual != currentLevel) {
                    currentLevel = actual
                    val fallbackStop = TorchStop.fromLevel(actual)
                    mainHandler.post {
                        for (listener in listeners) {
                            listener.onTorchStateChanged(actual, fallbackStop)
                        }
                    }
                }
            }

            mainHandler.post {
                try {
                    android.service.quicksettings.TileService.requestListeningState(
                        context,
                        android.content.ComponentName(context, BegotorchTileService::class.java)
                    )
                } catch (e: Exception) {
                    // Ignore if QS service not bound yet
                }
                callback?.invoke(success)
            }
        }
    }

    /**
     * Toggles flashlight On/Off. When turning on, restores last chosen stop.
     */
    fun toggleTorch(callback: ((Boolean) -> Unit)? = null) {
        if (isTorchOn()) {
            setBrightness(0, callback)
        } else {
            setBrightness(getLastSavedLevel(), callback)
        }
    }

    fun turnOff(callback: ((Boolean) -> Unit)? = null) {
        setBrightness(0, callback)
    }

    /**
     * Asynchronously synchronizes current level with actual kernel hardware status without blocking the UI.
     */
    fun syncHardwareStateAsync(callback: ((Int) -> Unit)? = null) {
        if (isWriting) {
            callback?.invoke(currentLevel)
            return
        }
        executor.execute {
            val hw = readCurrentHardwareBrightness()
            if (hw != currentLevel) {
                currentLevel = hw
                val stop = TorchStop.fromLevel(hw)
                mainHandler.post {
                    for (listener in listeners) {
                        listener.onTorchStateChanged(hw, stop)
                    }
                    callback?.invoke(hw)
                }
            } else {
                mainHandler.post {
                    callback?.invoke(hw)
                }
            }
        }
    }

    /**
     * Synchronizes current level with actual kernel hardware status.
     * Non-blocking when called on main thread; dispatches background check.
     */
    fun syncHardwareState(): Int {
        if (isWriting) {
            return currentLevel
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            syncHardwareStateAsync()
            return currentLevel
        }
        val hw = readCurrentHardwareBrightness()
        if (hw != currentLevel) {
            currentLevel = hw
            val stop = TorchStop.fromLevel(hw)
            mainHandler.post {
                for (listener in listeners) {
                    listener.onTorchStateChanged(hw, stop)
                }
            }
        }
        return currentLevel
    }

    /**
     * Attempts to write brightness directly to rootless begoker nodes, falling back to su or CameraManager.
     */
    private fun writeKernelBrightness(level: Int): Boolean {
        val payload = "$level\n".toByteArray()
        val writableNodes = customWritableNodes ?: WRITABLE_NODES

        // 1. Try direct file write to rootless Begoker nodes
        for (nodePath in writableNodes) {
            val nodeFile = File(nodePath)
            if (nodeFile.exists()) {
                try {
                    FileOutputStream(nodeFile).use { fos ->
                        fos.write(payload)
                        fos.flush()
                    }
                    Log.d(TAG, "Successfully wrote level $level to $nodePath")
                    return true
                } catch (e: Exception) {
                    Log.w(TAG, "Direct write to $nodePath failed: ${e.message}")
                }
            }
        }

        // 2. Try root 'su' command write if device is rooted but nodes have restrictive SELinux
        if (suAvailable) {
            var suSucceeded = false
            for (nodePath in writableNodes) {
                val nodeFile = File(nodePath)
                if (nodeFile.exists()) {
                    try {
                        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo $level > $nodePath"))
                        val finished = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            process.waitFor(300, TimeUnit.MILLISECONDS)
                        } else {
                            process.waitFor()
                            true
                        }
                        if (finished && process.exitValue() == 0) {
                            Log.d(TAG, "Su wrote level $level to $nodePath")
                            suSucceeded = true
                            return true
                        } else {
                            process.destroyForcibly()
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "su execution unavailable: ${e.message}")
                        suAvailable = false
                        break
                    }
                }
            }
            if (!suSucceeded) {
                // Short-circuit future su attempts to avoid lagging the executor
                suAvailable = false
            }
        }

        // 3. Fallback to CameraManager
        return fallbackCameraManager(level)
    }

    private fun fallbackCameraManager(level: Int): Boolean {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return false
            val cameraId = cachedFlashCameraId ?: cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false

            if (level <= 0) {
                cameraManager.setTorchMode(cameraId, false)
                return true
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val maxLevel = cachedMaxCameraTorchLevel
                if (maxLevel > 1) {
                    val mapped = ((level.toFloat() / 10f) * maxLevel).toInt().coerceIn(1, maxLevel)
                    cameraManager.turnOnTorchWithStrengthLevel(cameraId, mapped)
                    return true
                }
            }

            cameraManager.setTorchMode(cameraId, true)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "CameraManager fallback failed: ${e.message}", e)
            return false
        }
    }

    /**
     * Reads the current brightness from the kernel.
     */
    fun readCurrentHardwareBrightness(): Int {
        val readableNodes = customReadableNodes ?: READABLE_NODES
        for (nodePath in readableNodes) {
            val file = File(nodePath)
            if (file.exists()) {
                try {
                    val content = file.readText().trim()
                    val value = content.toIntOrNull()
                    if (value != null) {
                        return value.coerceIn(0, 10)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed reading $nodePath: ${e.message}")
                }
            }
        }
        return currentLevel
    }

    /**
     * Reads and parses /proc/perfmgr/torch_info for live telemetry.
     */
    fun readTorchInfo(): KernelTorchInfo? {
        val infoPath = customInfoNode ?: INFO_NODE
        val file = File(infoPath)
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
                        "hardware_selector" -> {
                            selector = value.substringBefore(" ").toIntOrNull() ?: 0
                        }
                        "current_per_channel" -> {
                            currentMa = value.substringBefore(" ").toIntOrNull() ?: 0
                        }
                        "current_total_dual" -> {
                            dualMa = value.substringBefore(" ").toIntOrNull() ?: 0
                        }
                        "status" -> status = value.equals("on", ignoreCase = true)
                    }
                }
            }

            return KernelTorchInfo(
                brightness = brightness,
                selector = selector,
                currentPerChannelMa = currentMa,
                currentTotalDualMa = dualMa,
                isHardwareOn = status
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read torch_info: ${e.message}")
            return null
        }
    }

    companion object {
        private const val TAG = "BegokerTorch"
        private const val PREFS_NAME = "begotorch_prefs"
        private const val KEY_LAST_LEVEL = "last_torch_level"
        private const val DEFAULT_RESTORE_LEVEL = 4 // Stock Xiaomi 100 mA standard

        @Volatile
        var customWritableNodes: List<String>? = null
        @Volatile
        var customReadableNodes: List<String>? = null
        @Volatile
        var customInfoNode: String? = null

        private val WRITABLE_NODES = listOf(
            "/proc/perfmgr/torch_brightness",
            "/proc/perfmgr/flashlight_brightness",
            "/sys/kernel/torch_brightness",
            "/sys/kernel/flashlight_brightness",
            "/sys/class/leds/flashlight/brightness",
            "/sys/class/leds/torch-light2/brightness"
        )

        private val READABLE_NODES = listOf(
            "/proc/perfmgr/torch_brightness",
            "/sys/kernel/torch_brightness",
            "/sys/class/leds/flashlight/brightness"
        )

        private const val INFO_NODE = "/proc/perfmgr/torch_info"

        @Volatile
        private var INSTANCE: BegokerTorchManager? = null

        fun getInstance(context: Context): BegokerTorchManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BegokerTorchManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
