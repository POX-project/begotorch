package com.begoker.torch

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.begoker.torch.ui.FlashlightStrengthOverlay

/**
 * Native AOSP Quick Settings Tile for Begoker Torch.
 *
 * Click: Presents the Material 3 Flashlight Strength dialog directly over the control center
 * without collapsing or leaving the Quick Settings shade, matching the WiFi/Internet dialog.
 */
class BegotorchTileService : TileService(), BegokerTorchManager.TorchStateListener {

    private lateinit var torchManager: BegokerTorchManager

    override fun onCreate() {
        super.onCreate()
        torchManager = BegokerTorchManager.getInstance(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        torchManager.addListener(this)
        val currentLevel = torchManager.syncHardwareState()
        updateTileState(currentLevel, torchManager.getCurrentStop())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "com.begoker.torch.SHOW_OVERLAY") {
            FlashlightStrengthOverlay.getInstance(this).show()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onStopListening() {
        super.onStopListening()
        torchManager.removeListener(this)
    }

    override fun onClick() {
        super.onClick()
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastClickTime < CLICK_DEBOUNCE_MS) {
            Log.d(TAG, "Debounced rapid duplicate QS click (${now - lastClickTime}ms)")
            return
        }
        lastClickTime = now

        val currentLevel = torchManager.getCurrentLevel()
        Log.d(TAG, "QS Tile clicked: currentLevel=$currentLevel")

        if (currentLevel > 0) {
            // When ON: clicking it turns it OFF immediately
            Log.d(TAG, "Tile is ON -> turning OFF immediately")
            FlashlightStrengthOverlay.getInstance(this).dismiss()
            torchManager.turnOff()
        } else {
            // When OFF: clicking it turns it ON and presents the Flashlight Strength popup in Control Center
            val targetStop = torchManager.getLastSavedLevel()
            Log.d(TAG, "Tile is OFF -> turning ON to saved stop $targetStop and presenting Flashlight Strength overlay")
            torchManager.setBrightness(targetStop)
            FlashlightStrengthOverlay.getInstance(this).show()
        }
    }

    override fun onTorchStateChanged(level: Int, stop: TorchStop) {
        updateTileState(level, stop)
    }

    private fun updateTileState(level: Int, stop: TorchStop) {
        val tile = qsTile ?: return

        val isOn = level > 0
        tile.state = if (isOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isOn) {
                if (stop.isOverdrive) {
                    "${stop.currentMaFormatted} • Overdrive"
                } else {
                    "${stop.title} • ${stop.currentMaFormatted}"
                }
            } else {
                getString(R.string.torch_off)
            }
        }

        val iconRes = if (isOn) R.drawable.ic_qs_torch_on else R.drawable.ic_qs_torch_off
        tile.icon = Icon.createWithResource(this, iconRes)

        tile.updateTile()
    }

    companion object {
        private const val TAG = "BegotorchTileService"
        @Volatile
        private var lastClickTime = 0L
        private const val CLICK_DEBOUNCE_MS = 400L
    }
}
