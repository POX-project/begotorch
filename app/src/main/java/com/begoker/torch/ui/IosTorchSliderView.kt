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
import androidx.core.content.ContextCompat
import com.begoker.torch.R
import com.begoker.torch.TorchStop

/**
 * Custom iOS Control Center-style vertical capsule pill flashlight slider.
 *
 * Replicates the authentic iOS 17/18 flashlight experience:
 * - 4 distinct iOS segment dividers or 10 fine kernel stops.
 * - Smooth vertical scrub gesture with tactile haptics on stop crossing.
 * - Dynamic color temperature (warm soft yellow to intense daylight white).
 * - Internal flashlight glyph with inverted color blending.
 */
class IosTorchSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnIntensityChangeListener {
        fun onIntensityChanged(stop: TorchStop, fromUser: Boolean)
        fun onIntensityConfirmed(stop: TorchStop)
    }

    var listener: OnIntensityChangeListener? = null

    // Pill geometry
    private val pillBounds = RectF()
    private val fillBounds = RectF()
    private val clipPath = Path()
    private var cornerRadius = 0f

    // Paints
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#28282A")
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55000000")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = Color.parseColor("#40FFD54F")
    }

    // Flashlight icon drawable
    private val torchIcon = ContextCompat.getDrawable(context, R.drawable.ic_torch_ios)

    // Current state
    private var currentFraction = 0f // 0f to 1f
    private var targetFraction = 0f
    private var lastHapticLevel = -1
    private var activeStop: TorchStop = TorchStop.STOPS[0]

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
        val padding = 12f
        pillBounds.set(padding, padding, w - padding, h - padding)
        cornerRadius = (pillBounds.width().coerceAtMost(pillBounds.height())) * 0.32f

        clipPath.reset()
        clipPath.addRoundRect(pillBounds, cornerRadius, cornerRadius, Path.Direction.CW)

        updateShader()
    }

    private fun updateShader() {
        if (pillBounds.height() <= 0) return

        // Gradient shifting from warm incandescent yellow at lower levels to brilliant white at peak
        val topColor = if (activeStop.isOverdrive) {
            Color.parseColor("#FFFFFF")
        } else {
            Color.parseColor("#FFF3B0")
        }
        val bottomColor = Color.parseColor("#FFD23F")

        fillPaint.shader = LinearGradient(
            pillBounds.left,
            pillBounds.top,
            pillBounds.left,
            pillBounds.bottom,
            topColor,
            bottomColor,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Outer glow when in overdrive
        if (activeStop.isOverdrive && currentFraction > 0.8f) {
            canvas.drawRoundRect(pillBounds, cornerRadius, cornerRadius, glowPaint)
        }

        // Draw pill base container
        canvas.drawRoundRect(pillBounds, cornerRadius, cornerRadius, backgroundPaint)

        // Clip to pill shape for fill and dividers
        canvas.save()
        canvas.clipPath(clipPath)

        // Draw animated fill from bottom
        if (currentFraction > 0.001f) {
            val fillHeight = pillBounds.height() * currentFraction
            fillBounds.set(
                pillBounds.left,
                pillBounds.bottom - fillHeight,
                pillBounds.right,
                pillBounds.bottom
            )
            canvas.drawRect(fillBounds, fillPaint)
        }

        // Draw iOS-style 4 segment dividers
        val segmentCount = 4
        val segmentHeight = pillBounds.height() / segmentCount
        for (i in 1 until segmentCount) {
            val y = pillBounds.bottom - (segmentHeight * i)
            canvas.drawLine(pillBounds.left, y, pillBounds.right, y, dividerPaint)
        }

        // Draw centered torch glyph
        torchIcon?.let { icon ->
            val iconSize = (pillBounds.width() * 0.38f).toInt()
            val cx = pillBounds.centerX().toInt()
            val cy = pillBounds.centerY().toInt()
            icon.setBounds(cx - iconSize / 2, cy - iconSize / 2, cx + iconSize / 2, cy + iconSize / 2)

            // Dynamic color tint for icon depending on whether it is covered by fill
            val fillY = pillBounds.bottom - (pillBounds.height() * currentFraction)
            val iconY = cy.toFloat()

            if (fillY < iconY) {
                // Covered by fill: dark high-contrast glyph
                icon.setTint(Color.parseColor("#1C1C1E"))
            } else {
                // Not covered: semi-translucent crisp white
                icon.setTint(Color.parseColor("#E5FFFFFF"))
            }
            icon.draw(canvas)
        }

        canvas.restore()

        // Draw subtle glass border
        canvas.drawRoundRect(pillBounds, cornerRadius, cornerRadius, borderPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent.requestDisallowInterceptTouchEvent(true)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handleTouchPosition(event.y, isFinal = false)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                handleTouchPosition(event.y, isFinal = false)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handleTouchPosition(event.y, isFinal = true)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTouchPosition(y: Float, isFinal: Boolean) {
        val clampedY = y.coerceIn(pillBounds.top, pillBounds.bottom)
        val rawFraction = (pillBounds.bottom - clampedY) / pillBounds.height()

        if (!isFinal) {
            // Dragging: map to closest stop
            val stop = TorchStop.fromFraction(rawFraction)
            setStopInternal(stop, animate = false, fromUser = true)

            // Trigger haptic tick on level change
            if (stop.level != lastHapticLevel) {
                triggerHapticTick()
                lastHapticLevel = stop.level
            }
        } else {
            // Touch released: snap to stop
            val finalStop = TorchStop.fromFraction(rawFraction)
            setStop(finalStop, animate = true, fromUser = true)
            triggerSnapHaptic()
            listener?.onIntensityConfirmed(finalStop)
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
        if (animate) {
            animator?.cancel()
            animator = ValueAnimator.ofFloat(currentFraction, target).apply {
                duration = 220
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

        activeStop = stop
        updateShader()
        if (fromUser) {
            listener?.onIntensityChanged(stop, fromUser = true)
        }
    }

    private fun setStopInternal(stop: TorchStop, animate: Boolean, fromUser: Boolean) {
        activeStop = stop
        currentFraction = if (stop.level == 0) 0f else (stop.level.toFloat() / 10f)
        updateShader()
        invalidate()
        if (fromUser) {
            listener?.onIntensityChanged(stop, fromUser = true)
        }
    }

    fun getActiveStop(): TorchStop = activeStop
}
