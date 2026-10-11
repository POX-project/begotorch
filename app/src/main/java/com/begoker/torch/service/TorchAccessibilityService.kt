package com.begoker.torch.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.begoker.torch.ui.FlashlightStrengthOverlay

/**
 * Accessibility Service allowing Begotorch to render the Material 3 Flashlight Strength dialog
 * directly on top of the expanded Control Center using TYPE_ACCESSIBILITY_OVERLAY without
 * collapsing the Quick Settings panel.
 */
class TorchAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "TorchAccessibilityService connected - ready for in-shade overlays")
        activeService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No event inspection needed; service is purely for non-collapsing overlay rendering
    }

    override fun onInterrupt() {
        Log.d(TAG, "TorchAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeService == this) {
            activeService = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_OVERLAY -> {
                FlashlightStrengthOverlay.getInstance(this).show()
            }
            ACTION_DISMISS_OVERLAY -> {
                FlashlightStrengthOverlay.getInstance(this).dismiss()
            }
            ACTION_EXPAND_QS -> {
                expandQuickSettings()
            }
        }
        return START_NOT_STICKY
    }

    fun expandQuickSettings() {
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    }

    companion object {
        private const val TAG = "TorchAccessibility"
        const val ACTION_SHOW_OVERLAY = "com.begoker.torch.action.SHOW_OVERLAY"
        const val ACTION_DISMISS_OVERLAY = "com.begoker.torch.action.DISMISS_OVERLAY"
        const val ACTION_EXPAND_QS = "com.begoker.torch.action.EXPAND_QS"

        @Volatile
        var activeService: TorchAccessibilityService? = null
            private set

        fun isRunning(): Boolean = activeService != null
    }
}
