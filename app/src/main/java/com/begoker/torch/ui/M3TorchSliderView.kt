package com.begoker.torch.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
 * Material 3 Expressive Flashlight Strength Slider.
 *
 * Implements the Android 16 / Material 3 Expressive design:
 * - Wide rounded capsule track with discrete stop ticks (0..10).
 * - Tonal active fill with smooth color shifting based on power.
 * - Pill-shaped thumb showing current stop number.
 * - Haptic feedback on every stop boundary.
 */
class M3TorchSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnSliderChangeListener {
        fun onProgressChanged(stop: TorchStop, fromUser: Boolean)
        fun onProgressConfirmed(stop: TorchStop)
    }

    var listener: OnSliderChangeListener? = null

    private val trackBounds = RectF()
    private val activeBounds = RectF()
    private val thumbBounds = RectF()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#36343B") // M3 surfaceContainerHighest
        style = Paint.Style.FILL
    }

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD23F") // Torch gold / M3 Primary
        style = Paint.Style.FILL
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55FFFFFF")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1C1B1F")
        textSize = 34f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private var currentFraction = 0.4f // 0f to 1f
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
        val padH = 16f
        val padV = 8f
        trackBounds.set(padH, padV, w - padH, h - padV)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cornerRadius = trackBounds.height() / 2f

        // Draw background inactive track
        canvas.drawRoundRect(trackBounds, cornerRadius, cornerRadius, trackPaint)

        // Draw active filled track
        if (currentFraction > 0.005f) {
            val fillWidth = trackBounds.width() * currentFraction
            activeBounds.set(trackBounds.left, trackBounds.top, trackBounds.left + fillWidth, trackBounds.bottom)

            // Dynamic color shift: warm yellow to brilliant white/gold in overdrive
            activePaint.color = if (activeStop.isOverdrive) {
                Color.parseColor("#FFF8E7")
            } else {
                Color.parseColor("#FFD23F")
            }
            canvas.drawRoundRect(activeBounds, cornerRadius, cornerRadius, activePaint)
        }

        // Draw stop tick notches (levels 1 through 9)
        val stopsCount = 10
        val stepWidth = trackBounds.width() / stopsCount
        for (i in 1 until stopsCount) {
            val x = trackBounds.left + (stepWidth * i)
            val y1 = trackBounds.centerY() - 10f
            val y2 = trackBounds.centerY() + 10f
            canvas.drawLine(x, y1, x, y2, tickPaint)
        }

        // Draw M3 thumb indicator
        val thumbX = (trackBounds.left + (trackBounds.width() * currentFraction)).coerceIn(
            trackBounds.left + cornerRadius,
            trackBounds.right - cornerRadius
        )
        val thumbWidth = 56f
        thumbBounds.set(
            thumbX - thumbWidth / 2f,
            trackBounds.top + 6f,
            thumbX + thumbWidth / 2f,
            trackBounds.bottom - 6f
        )

        // Draw thumb stop number or icon
        val label = if (activeStop.level == 0) "OFF" else "${activeStop.level}"
        val textY = trackBounds.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f)

        textPaint.color = if (currentFraction > 0.15f) Color.parseColor("#1D1B20") else Color.WHITE
        textPaint.textSize = if (activeStop.level == 0) 24f else 30f
        canvas.drawText(label, thumbX, textY, textPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent.requestDisallowInterceptTouchEvent(true)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                handleTouch(event.x, isFinal = false)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handleTouch(event.x, isFinal = true)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTouch(x: Float, isFinal: Boolean) {
        val clampedX = x.coerceIn(trackBounds.left, trackBounds.right)
        val rawFraction = (clampedX - trackBounds.left) / trackBounds.width()
        val stop = TorchStop.fromFraction(rawFraction)

        if (!isFinal) {
            currentFraction = if (stop.level == 0) 0f else (stop.level.toFloat() / 10f)
            activeStop = stop
            invalidate()

            if (stop.level != lastHapticLevel) {
                triggerHapticTick()
                lastHapticLevel = stop.level
                listener?.onProgressChanged(stop, fromUser = true)
            }
        } else {
            setStop(stop, animate = true, fromUser = true)
            triggerSnapHaptic()
            listener?.onProgressConfirmed(stop)
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
        val target = if (stop.level == 0) 0f else (stop.level.toFloat() / 10f)
        activeStop = stop

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
            listener?.onProgressChanged(stop, fromUser = true)
        }
    }

    fun getActiveStop(): TorchStop = activeStop
}
