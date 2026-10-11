package com.begoker.torch.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.begoker.torch.BegokerTorchManager
import com.begoker.torch.R
import com.begoker.torch.TorchStop

/**
 * Material 3 AOSP Quick Settings Flashlight Strength Dialog Controller.
 *
 * Provides:
 * - Directly shown in Quick Settings shade via TileService.showDialog()
 * - M3 Expressive slider with hardware stops
 * - Stop Tiles (like AOSP tile buttons) for single-tap intensity selection
 * - Real-time telemetry: per LED current, dual total current, and MT6360 hardware selector
 */
class Material3TorchDialogHelper(
    private val context: Context,
    private val torchManager: BegokerTorchManager,
    private val onDismissRequested: () -> Unit
) : BegokerTorchManager.TorchStateListener {

    private lateinit var rootView: View
    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvTelemetry: TextView
    private lateinit var switchPower: Switch
    private lateinit var slider: M3TorchSliderView
    private lateinit var tilesContainer: LinearLayout
    private lateinit var btnDone: Button
    private lateinit var ivHeaderTorch: ImageView

    private val tileViews = mutableListOf<Pair<TorchStop, View>>()

    fun createDialogView(inflater: LayoutInflater, container: ViewGroup? = null): View {
        rootView = inflater.inflate(R.layout.dialog_m3_torch, container, false)

        tvTitle = rootView.findViewById(R.id.tv_dialog_title)
        tvStatus = rootView.findViewById(R.id.tv_dialog_status)
        tvTelemetry = rootView.findViewById(R.id.tv_dialog_telemetry)
        switchPower = rootView.findViewById(R.id.switch_torch_power)
        slider = rootView.findViewById(R.id.m3_torch_slider)
        tilesContainer = rootView.findViewById(R.id.m3_stop_tiles_container)
        btnDone = rootView.findViewById(R.id.btn_dialog_done)
        ivHeaderTorch = rootView.findViewById(R.id.iv_header_torch)

        setupListeners()
        populateM3StopTiles()

        val currentStop = torchManager.getCurrentStop()
        updateUi(currentStop)
        slider.setStop(currentStop, animate = false)

        return rootView
    }

    private fun setupListeners() {
        switchPower.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !torchManager.isTorchOn()) {
                torchManager.setBrightness(torchManager.getLastSavedLevel())
            } else if (!isChecked && torchManager.isTorchOn()) {
                torchManager.setBrightness(0)
            }
        }

        slider.listener = object : M3TorchSliderView.OnSliderChangeListener {
            override fun onProgressChanged(stop: TorchStop, fromUser: Boolean) {
                if (fromUser) {
                    torchManager.setBrightness(stop.level)
                    updateUi(stop)
                }
            }

            override fun onProgressConfirmed(stop: TorchStop) {
                torchManager.setBrightness(stop.level)
                updateUi(stop)
            }
        }

        btnDone.setOnClickListener {
            onDismissRequested()
        }
    }

    private fun populateM3StopTiles() {
        tilesContainer.removeAllViews()
        tileViews.clear()

        // Key stops: 0 (Off), 1 (25mA Bedside), 2 (50mA), 4 (100mA Stock Limit), 6 (175mA), 8 (250mA), 10 (325mA Overdrive)
        val stopsToDisplay = listOf(
            TorchStop.STOPS[0],
            TorchStop.STOPS[1],
            TorchStop.STOPS[2],
            TorchStop.STOPS[4],
            TorchStop.STOPS[6],
            TorchStop.STOPS[8],
            TorchStop.STOPS[10]
        )

        for (stop in stopsToDisplay) {
            val tileView = createTileCard(stop)
            tilesContainer.addView(tileView)
            tileViews.add(stop to tileView)
        }
    }

    private fun createTileCard(stop: TorchStop): View {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 20, 32, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 16
            }
            isClickable = true
            isFocusable = true
        }

        val tvLevel = TextView(context).apply {
            text = if (stop.level == 0) "OFF" else stop.currentMaFormatted
            textSize = 14f
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            tag = "tv_level"
        }

        val tvDesc = TextView(context).apply {
            text = if (stop.level == 0) "Shutoff" else if (stop.isOverdrive) "Peak Overdrive" else stop.title
            textSize = 10f
            gravity = Gravity.CENTER
            tag = "tv_desc"
        }

        layout.addView(tvLevel)
        layout.addView(tvDesc)

        layout.setOnClickListener {
            torchManager.setBrightness(stop.level)
            slider.setStop(stop, animate = true)
            updateUi(stop)
        }

        return layout
    }

    fun updateUi(stop: TorchStop) {
        val isOn = stop.level > 0

        tvStatus.text = if (isOn) "${stop.title} • ${stop.subtitle}" else context.getString(R.string.torch_off)
        switchPower.isChecked = isOn

        if (isOn) {
            tvTelemetry.visibility = View.VISIBLE
            tvTelemetry.text = context.getString(
                R.string.telemetry_format,
                stop.currentMaFormatted,
                stop.dualMaFormatted,
                stop.selector
            )
            ivHeaderTorch.setColorFilter(Color.parseColor("#FFD23F"))
        } else {
            tvTelemetry.visibility = View.GONE
            ivHeaderTorch.setColorFilter(Color.parseColor("#88FFFFFF"))
        }

        // Highlight selected stop tile
        for ((tileStop, view) in tileViews) {
            val isSelected = tileStop.level == stop.level
            view.background = ContextCompat.getDrawable(
                context,
                if (isSelected) R.drawable.bg_m3_tile_active else R.drawable.bg_m3_tile_inactive
            )

            val tvLevel = view.findViewWithTag<TextView>("tv_level")
            val tvDesc = view.findViewWithTag<TextView>("tv_desc")

            if (isSelected) {
                tvLevel?.setTextColor(Color.parseColor("#1D1B20"))
                tvDesc?.setTextColor(Color.parseColor("#3C3844"))
            } else {
                tvLevel?.setTextColor(Color.WHITE)
                tvDesc?.setTextColor(Color.parseColor("#938F99"))
            }
        }
    }

    override fun onTorchStateChanged(level: Int, stop: TorchStop) {
        updateUi(stop)
        if (slider.getActiveStop().level != stop.level) {
            slider.setStop(stop, animate = true)
        }
    }

    companion object {
        fun buildDialog(
            context: Context,
            torchManager: BegokerTorchManager,
            onDismiss: () -> Unit = {}
        ): Dialog {
            val dialog = Dialog(context, R.style.Theme_Begotorch_M3Dialog)
            val helper = Material3TorchDialogHelper(context, torchManager) {
                dialog.dismiss()
                onDismiss()
            }

            val view = helper.createDialogView(LayoutInflater.from(context))
            dialog.setContentView(view)

            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setGravity(Gravity.CENTER)
            }

            dialog.setOnShowListener {
                torchManager.addListener(helper)
            }
            dialog.setOnDismissListener {
                torchManager.removeListener(helper)
                onDismiss()
            }

            return dialog
        }
    }
}
