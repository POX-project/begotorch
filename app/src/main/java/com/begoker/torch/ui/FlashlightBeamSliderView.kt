package com.begoker.torch.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.begoker.torch.TorchStop

/**
 * Material 3 Flashlight Strength Beam Slider.
 *
 * Faithfully reproduces the Android 16 / Pixel Flashlight Strength control:
 * - Vertical flashlight body at the bottom.
 * - Conical light beam radiating upward.
 * - Horizontal draggable slider bar that regulates beam intensity.
 * - Tactile haptic ticks on stop boundaries.
 */
class FlashlightBeamSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnBeamIntensityChangeListener {
        fun onIntensityChanged(stop: TorchStop, fromUser: Boolean)
        fun onIntensityConfirmed(stop: TorchStop)
    }

    var listener: OnBeamIntensityChangeListener? = null

    // Theme Colors (Pixel Material 3 Terracotta/Copper)
    private val primaryColor = Color.parseColor("#854625")
    private val beamBaseColor = Color.parseColor("#40854625")
    private val beamGlowColor = Color.parseColor("#B0854625")

    // Paints
    private val flashlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        style = Paint.Style.FILL
    }

    private val inactiveBeamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val activeBeamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val sliderBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        style = Paint.Style.FILL
    }

    private val sliderBarBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF8F5")
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
    }

    // Geometry
    private val beamPath = Path()
    private val activeBeamPath = Path()
    private val sliderBarRect = RectF()

    private var beamTopY = 0f
    private var beamBottomY = 0f
    private var beamTopWidth = 0f
    private var beamBottomWidth = 0f
    private var centerX = 0f

    // Current State
    var isTracking: Boolean = false
        private set

    private var currentFraction = 0.333f // 0f to 1.0f (maps to levels 1..10)
    private var lastHapticLevel = -1
    private var activeStop: TorchStop = TorchStop.STOPS[4]
    private var animator: ValueAnimator? = null

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f

        // Flashlight icon is at the bottom: ~48dp tall
        val torchHeight = 120f
        val torchBottom = h - 20f
        beamBottomY = torchBottom - torchHeight + 10f
        beamTopY = 40f

        beamBottomWidth = 46f
        beamTopWidth = w * 0.76f

        // Recreate conical beam path
        beamPath.reset()
        beamPath.moveTo(centerX - beamBottomWidth / 2f, beamBottomY)
        beamPath.lineTo(centerX - beamTopWidth / 2f, beamTopY)
        beamPath.quadTo(centerX, beamTopY - 14f, centerX + beamTopWidth / 2f, beamTopY)
        beamPath.lineTo(centerX + beamBottomWidth / 2f, beamBottomY)
        beamPath.close()

        // Inactive beam soft gradient (translucent at bottom, fades softly at top)
        inactiveBeamPaint.shader = LinearGradient(
            centerX, beamBottomY,
            centerX, beamTopY,
            Color.parseColor("#44854625"),
            Color.parseColor("#08854625"),
            Shader.TileMode.CLAMP
        )

        // Active beam vibrant gradient
        activeBeamPaint.shader = LinearGradient(
            centerX, beamBottomY,
            centerX, beamTopY,
            Color.parseColor("#D9854625"),
            Color.parseColor("#80854625"),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw inactive conical beam background
        canvas.drawPath(beamPath, inactiveBeamPaint)

        // Calculate slider Y position: from beamBottomY (fraction 0) to beamTopY (fraction 1)
        val beamHeight = beamBottomY - beamTopY
        val barY = beamBottomY - (beamHeight * currentFraction)

        // 2. Draw active beam fill up to the slider bar
        if (currentFraction > 0.005f) {
            val progressWidth = beamBottomWidth + (beamTopWidth - beamBottomWidth) * currentFraction
            activeBeamPath.reset()
            activeBeamPath.moveTo(centerX - beamBottomWidth / 2f, beamBottomY)
            activeBeamPath.lineTo(centerX - progressWidth / 2f, barY)
            activeBeamPath.lineTo(centerX + progressWidth / 2f, barY)
            activeBeamPath.lineTo(centerX + beamBottomWidth / 2f, beamBottomY)
            activeBeamPath.close()

            canvas.drawPath(activeBeamPath, activeBeamPaint)
        }

        // 3. Draw horizontal slider bar / handle
        val barWidth = (beamTopWidth * 0.95f).coerceAtLeast(180f)
        val barHeight = 22f
        val barRadius = barHeight / 2f

        sliderBarRect.set(
            centerX - barWidth / 2f,
            barY - barHeight / 2f,
            centerX + barWidth / 2f,
            barY + barHeight / 2f
        )

        canvas.drawRoundRect(sliderBarRect, barRadius, barRadius, sliderBarPaint)
        canvas.drawRoundRect(sliderBarRect, barRadius, barRadius, sliderBarBorderPaint)

        // 4. Draw Flashlight Body Icon at bottom
        drawFlashlightIcon(canvas)
    }

    private fun drawFlashlightIcon(canvas: Canvas) {
        val bezelWidth = beamBottomWidth + 10f
        val bezelHeight = 16f
        val bodyWidth = 36f
        val bodyHeight = 70f
        val bottomY = beamBottomY + bezelHeight + bodyHeight

        // Bezel / Head of torch
        val bezelRect = RectF(
            centerX - bezelWidth / 2f,
            beamBottomY,
            centerX + bezelWidth / 2f,
            beamBottomY + bezelHeight
        )
        canvas.drawRoundRect(bezelRect, 6f, 6f, flashlightPaint)

        // Neck
        val neckWidth = bodyWidth + 4f
        val neckRect = RectF(
            centerX - neckWidth / 2f,
            beamBottomY + bezelHeight - 2f,
            centerX + neckWidth / 2f,
            beamBottomY + bezelHeight + 8f
        )
        canvas.drawRect(neckRect, flashlightPaint)

        // Torch main barrel/body
        val bodyRect = RectF(
            centerX - bodyWidth / 2f,
            beamBottomY + bezelHeight + 6f,
            centerX + bodyWidth / 2f,
            bottomY
        )
        canvas.drawRoundRect(bodyRect, 10f, 10f, flashlightPaint)

        // Power switch dot in middle of barrel
        val switchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFF8F5")
            style = Paint.Style.FILL
        }
        val switchY = beamBottomY + bezelHeight + 24f
        canvas.drawCircle(centerX, switchY, 5f, switchPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isTracking = true
                handleTouchY(event.y, isFinal = false)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                isTracking = true
                handleTouchY(event.y, isFinal = false)
                return true
            }
            MotionEvent.ACTION_UP -> {
                isTracking = false
                handleTouchY(event.y, isFinal = true)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isTracking = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTouchY(y: Float, isFinal: Boolean) {
        val clampedY = y.coerceIn(beamTopY, beamBottomY)
        val beamHeight = beamBottomY - beamTopY
        val rawFraction = (beamBottomY - clampedY) / beamHeight

        val stop = fractionToStop(rawFraction)

        if (!isFinal) {
            currentFraction = stopToFraction(stop.level)
            activeStop = stop
            invalidate()

            if (stop.level != lastHapticLevel) {
                triggerHapticTick()
                lastHapticLevel = stop.level
                listener?.onIntensityChanged(stop, fromUser = true)
            }
        } else {
            setStop(stop, animate = true, fromUser = true)
            triggerSnapHaptic()
            listener?.onIntensityConfirmed(stop)
        }
    }

    private fun triggerHapticTick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    private fun triggerSnapHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    fun setStop(stop: TorchStop, animate: Boolean = true, fromUser: Boolean = false) {
        val target = if (stop.level == 0) stopToFraction(1) else stopToFraction(stop.level)
        activeStop = if (stop.level == 0) TorchStop.STOPS[1] else stop

        if (animate) {
            animator?.cancel()
            animator = ValueAnimator.ofFloat(currentFraction, target).apply {
                duration = 180
                interpolator = DecelerateInterpolator()
                addUpdateListener { va ->
                    currentFraction = va.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            currentFraction = target
            invalidate()
        }

        if (fromUser) {
            listener?.onIntensityChanged(activeStop, fromUser = true)
        }
    }

    fun getActiveStop(): TorchStop = activeStop

    companion object {
        fun stopToFraction(level: Int): Float {
            val clamped = level.coerceIn(1, 10)
            return (clamped - 1) / 9f
        }

        fun fractionToStop(fraction: Float): TorchStop {
            val clamped = fraction.coerceIn(0f, 1f)
            val index = Math.round(clamped * 9).coerceIn(0, 9)
            return TorchStop.STOPS[index + 1] // Stops 1..10
        }
    }
}
