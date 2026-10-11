package com.begoker.torch.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.begoker.torch.BegokerTorchManager
import com.begoker.torch.R
import com.begoker.torch.TorchStop
import com.begoker.torch.service.TorchAccessibilityService

/**
 * Material 3 Quick Settings Flashlight Strength Dialog Activity.
 *
 * Handles ACTION_QS_TILE_PREFERENCES on tile long-press.
 * If TorchAccessibilityService is active, delegates to the in-shade overlay and
 * keeps/re-expands Quick Settings, completely bypassing App Info.
 */
class TorchIntensityPopupActivity : Activity(), BegokerTorchManager.TorchStateListener {

    private lateinit var torchManager: BegokerTorchManager
    private lateinit var beamSlider: FlashlightBeamSliderView
    private lateinit var tvStatusInfo: TextView
    private lateinit var btnToggle: Button
    private lateinit var btnDone: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        torchManager = BegokerTorchManager.getInstance(this)

        // If torch is off, turn it on to saved level when long-clicked
        if (torchManager.getCurrentLevel() == 0) {
            torchManager.setBrightness(torchManager.getLastSavedLevel())
        }

        // If Accessibility Service is running, display directly in the Control Center!
        if (TorchAccessibilityService.isRunning()) {
            Log.d(TAG, "Long click: delegating to in-shade overlay via TorchAccessibilityService")
            TorchAccessibilityService.activeService?.expandQuickSettings()
            FlashlightStrengthOverlay.getInstance(this).show()

            finish()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
            return
        }

        // Fallback: Display the dialog directly inside this transparent activity
        window.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.CENTER)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.5f)
        }
        setFinishOnTouchOutside(true)

        setContentView(R.layout.dialog_flashlight_strength)

        initViews()
        setupListeners()
    }

    override fun onStart() {
        super.onStart()
        torchManager.addListener(this)
        val current = torchManager.getCurrentStop()
        updateUi(current)
        if (::beamSlider.isInitialized) {
            beamSlider.setStop(current, animate = false)
        }
    }

    override fun onStop() {
        super.onStop()
        torchManager.removeListener(this)
    }

    private fun initViews() {
        val scrim = findViewById<View>(R.id.overlay_scrim)
        val card = findViewById<View>(R.id.dialog_card)
        beamSlider = findViewById(R.id.beam_slider)
        tvStatusInfo = findViewById(R.id.tv_status_info)
        btnToggle = findViewById(R.id.btn_action_toggle)
        btnDone = findViewById(R.id.btn_action_done)

        scrim.setOnClickListener {
            finish()
        }
        card.setOnClickListener {
            // Keep clicks on the card
        }
    }

    private fun setupListeners() {
        btnDone.setOnClickListener {
            finish()
        }

        btnToggle.setOnClickListener {
            torchManager.turnOff {
                finish()
            }
        }

        beamSlider.listener = object : FlashlightBeamSliderView.OnBeamIntensityChangeListener {
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
    }

    override fun onTorchStateChanged(level: Int, stop: TorchStop) {
        runOnUiThread {
            if (level == 0) {
                finish()
                return@runOnUiThread
            }
            updateUi(stop)
            if (::beamSlider.isInitialized && !beamSlider.isTracking && beamSlider.getActiveStop().level != stop.level) {
                beamSlider.setStop(stop, animate = true)
            }
        }
    }

    private fun updateUi(stop: TorchStop) {
        btnToggle.text = "Turn off"

        tvStatusInfo.text = if (stop.isOverdrive) {
            "${stop.currentMaFormatted} • Overdrive Boost"
        } else {
            "${stop.currentMaFormatted} • ${stop.title}"
        }
    }

    companion object {
        private const val TAG = "TorchIntensityPopup"
    }
}
