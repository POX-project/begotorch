package com.begoker.torch.ui

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.begoker.torch.BegokerTorchManager
import com.begoker.torch.R
import com.begoker.torch.TorchStop
import com.begoker.torch.service.TorchAccessibilityService

/**
 * Material 3 In-Shade Flashlight Strength Overlay.
 *
 * Appears directly over the Android Control Center / Quick Settings tiles without
 * collapsing or closing the shade, exactly replicating the native AOSP 16 / Pixel experience.
 *
 * Uses TYPE_ACCESSIBILITY_OVERLAY when available to guarantee placement on top of
 * TYPE_NOTIFICATION_SHADE with zero panel collapse.
 */
class FlashlightStrengthOverlay private constructor(private val appContext: Context) :
    BegokerTorchManager.TorchStateListener {

    private val torchManager = BegokerTorchManager.getInstance(appContext)

    private var currentWindowManager: WindowManager? = null
    private var overlayView: View? = null
    private var beamSlider: FlashlightBeamSliderView? = null
    private var tvStatusInfo: TextView? = null
    private var btnToggle: Button? = null
    private var btnDone: Button? = null
    @Volatile
    private var isDismissing: Boolean = false

    val isShowing: Boolean
        get() = overlayView != null && !isDismissing

    fun show() {
        if (overlayView != null) {
            if (isDismissing) {
                // Cancel pending dismissal and re-create view immediately
                val card = overlayView?.findViewById<View>(R.id.dialog_card)
                card?.animate()?.cancel()
                try {
                    currentWindowManager?.removeViewImmediate(overlayView)
                } catch (e: Exception) {}
                cleanupViews()
                isDismissing = false
            } else {
                Log.d(TAG, "Overlay already displayed, syncing UI state")
                val currentStop = torchManager.getCurrentStop()
                updateUi(currentStop)
                beamSlider?.setStop(currentStop, animate = true)
                return
            }
        }

        val accessibilityService = TorchAccessibilityService.activeService
        val (targetContext, windowType) = if (accessibilityService != null) {
            Log.d(TAG, "Using TorchAccessibilityService with TYPE_ACCESSIBILITY_OVERLAY")
            accessibilityService to WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            Log.d(TAG, "Falling back to TYPE_APPLICATION_OVERLAY")
            if (!Settings.canDrawOverlays(appContext)) {
                Log.w(TAG, "Cannot show overlay: neither Accessibility nor Overlay permission granted")
                return
            }
            appContext to WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }

        val wm = targetContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm == null) {
            Log.e(TAG, "WindowManager not available on context")
            return
        }

        val inflater = LayoutInflater.from(targetContext)
        val view = inflater.inflate(R.layout.dialog_flashlight_strength, null)
        overlayView = view
        currentWindowManager = wm
        isDismissing = false

        val scrim = view.findViewById<View>(R.id.overlay_scrim)
        val card = view.findViewById<View>(R.id.dialog_card)
        beamSlider = view.findViewById(R.id.beam_slider)
        tvStatusInfo = view.findViewById(R.id.tv_status_info)
        btnToggle = view.findViewById(R.id.btn_action_toggle)
        btnDone = view.findViewById(R.id.btn_action_done)

        // Dismiss when tapping outside the card
        scrim.setOnClickListener {
            dismiss()
        }
        card.setOnClickListener {
            // Intercept clicks on the card itself so they don't hit the scrim
        }

        btnDone?.setOnClickListener {
            dismiss()
        }

        // Tapping "Turn off" immediately powers down torch and dismisses dialog
        btnToggle?.setOnClickListener {
            torchManager.turnOff {
                dismiss()
            }
        }

        beamSlider?.listener = object : FlashlightBeamSliderView.OnBeamIntensityChangeListener {
            override fun onIntensityChanged(stop: TorchStop, fromUser: Boolean) {
                if (fromUser) {
                    torchManager.setBrightness(stop.level)
                    updateUi(stop)
                }
            }

            override fun onIntensityConfirmed(stop: TorchStop) {
                torchManager.setBrightness(stop.level)
                updateUi(stop)
            }
        }

        // Back key support
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dismiss()
                true
            } else {
                false
            }
        }

        val currentStop = torchManager.getCurrentStop()
        updateUi(currentStop)
        beamSlider?.setStop(currentStop, animate = false)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        // Smooth subtle entrance animation
        card.alpha = 0f
        card.scaleX = 0.92f
        card.scaleY = 0.92f

        try {
            wm.addView(view, layoutParams)
            torchManager.addListener(this)

            card.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180)
                .start()

            Log.i(TAG, "Flashlight Strength overlay successfully added over Control Center")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay window: ${e.message}", e)
            cleanupViews()
        }
    }

    fun dismiss() {
        if (isDismissing || overlayView == null) return
        isDismissing = true

        val view = overlayView ?: return
        val wm = currentWindowManager ?: appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val card = view.findViewById<View>(R.id.dialog_card)

        card?.animate()
            ?.alpha(0f)
            ?.scaleX(0.92f)
            ?.scaleY(0.92f)
            ?.setDuration(120)
            ?.withEndAction {
                try {
                    wm.removeView(view)
                } catch (e: Exception) {
                    Log.w(TAG, "Error removing overlay view: ${e.message}")
                } finally {
                    cleanupViews()
                    isDismissing = false
                }
            }
            ?.start() ?: run {
            try {
                wm.removeView(view)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing overlay view immediately: ${e.message}")
            } finally {
                cleanupViews()
                isDismissing = false
            }
        }
    }

    private fun cleanupViews() {
        torchManager.removeListener(this)
        overlayView = null
        currentWindowManager = null
        beamSlider = null
        tvStatusInfo = null
        btnToggle = null
        btnDone = null
    }

    override fun onTorchStateChanged(level: Int, stop: TorchStop) {
        if (level == 0) {
            // Flashlight turned off -> dismiss overlay immediately
            dismiss()
            return
        }

        updateUi(stop)
        beamSlider?.let { slider ->
            if (!slider.isTracking && slider.getActiveStop().level != stop.level) {
                slider.setStop(stop, animate = true)
            }
        }
    }

    private fun updateUi(stop: TorchStop) {
        btnToggle?.text = "Turn off"

        tvStatusInfo?.text = if (stop.isOverdrive) {
            "${stop.currentMaFormatted} • Overdrive Boost"
        } else {
            "${stop.currentMaFormatted} • ${stop.title}"
        }
    }

    companion object {
        private const val TAG = "FlashlightStrengthOverlay"

        @Volatile
        private var instance: FlashlightStrengthOverlay? = null

        fun getInstance(context: Context): FlashlightStrengthOverlay {
            return instance ?: synchronized(this) {
                instance ?: FlashlightStrengthOverlay(context.applicationContext).also { instance = it }
            }
        }
    }
}
