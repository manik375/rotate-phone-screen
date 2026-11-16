package com.pivot.orientation

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.LinearGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.Surface
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.floor
import kotlin.math.min

/**
 * The hero of the screen: a glowing phone that physically turns to show the chosen orientation.
 * The bright dot on the outer ring always sits at the phone's top edge.
 */
class PhoneView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val accent = context.getColor(R.color.accent)
    private val accent2 = context.getColor(R.color.accent2)
    private val density = resources.displayMetrics.density

    private var mode: Mode = Mode.AUTO
    private var active = false
    private var showLock = false

    private var angle = 0f
    private var glow = 0f
    private var pulse = 1f

    private var turnAnim: ValueAnimator? = null
    private var glowAnim: ValueAnimator? = null
    private var autoAnim: ValueAnimator? = null
    private var pulseAnim: ValueAnimator? = null

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = 0x22FFFFFF
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent2 }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent2 }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF101018.toInt() }
    private val edgeOff = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        color = 0xFF34344A.toInt()
    }
    private val edgeOn = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
    }
    private val screenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color_WHITE }
    private val chevronPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3.5f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color_WHITE
    }

    private val phoneRect = RectF()
    private val screenRect = RectF()
    private val arcRect = RectF()
    private val bodyRect = RectF()
    private val chevron = Path()
    private var cx = 0f
    private var cy = 0f
    private var size = 0f

    private fun dp(v: Float) = v * density

    // ---- Public state ----------------------------------------------------------------------

    fun setState(newMode: Mode, newActive: Boolean, animate: Boolean) {
        mode = newMode
        active = newActive
        applyState(animate)
    }

    private fun applyState(animate: Boolean) {
        showLock = active && mode == Mode.LOCK
        glowTo(if (active) 1f else 0f, animate)
        if (active && mode == Mode.AUTO) {
            startAuto()
        } else {
            stopAuto()
            turnTo(targetAngle(), animate)
        }
        if (active) startPulse() else stopPulse()
        invalidate()
    }

    private fun targetAngle(): Float {
        if (!active) return 0f
        return when (mode) {
            Mode.AUTO, Mode.PORTRAIT -> 0f
            Mode.LANDSCAPE -> -90f
            Mode.PORTRAIT_FLIP -> 180f
            Mode.LANDSCAPE_FLIP -> 90f
            Mode.LOCK -> when (display?.rotation) {
                Surface.ROTATION_90 -> -90f
                Surface.ROTATION_180 -> 180f
                Surface.ROTATION_270 -> 90f
                else -> 0f
            }
        }
    }

    // ---- Animation -------------------------------------------------------------------------

    private fun turnTo(target: Float, animate: Boolean) {
        turnAnim?.cancel()
        if (!animate || !isAttachedToWindow || !isLaidOut) {
            angle = target
            return
        }
        val from = angle
        val delta = ((target - from) % 360f + 540f) % 360f - 180f
        turnAnim = ValueAnimator.ofFloat(from, from + delta).apply {
            duration = 600
            interpolator = OvershootInterpolator(1.3f)
            addUpdateListener {
                angle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun glowTo(target: Float, animate: Boolean) {
        glowAnim?.cancel()
        if (!animate || !isAttachedToWindow) {
            glow = target
            return
        }
        glowAnim = ValueAnimator.ofFloat(glow, target).apply {
            duration = 400
            addUpdateListener {
                glow = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /** Auto: the phone steps through all four directions, pausing at each. */
    private fun startAuto() {
        if (!isAttachedToWindow || autoAnim?.isRunning == true) return
        turnAnim?.cancel()
        val start = angle
        autoAnim = ValueAnimator.ofFloat(start, start - 360f).apply {
            duration = 7200
            repeatCount = ValueAnimator.INFINITE
            interpolator = STEPS
            addUpdateListener {
                angle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopAuto() {
        autoAnim?.cancel()
        autoAnim = null
    }

    private fun startPulse() {
        if (!isAttachedToWindow || pulseAnim?.isRunning == true) return
        pulseAnim = ValueAnimator.ofFloat(0.93f, 1f).apply {
            duration = 2200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                pulse = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopPulse() {
        pulseAnim?.cancel()
        pulseAnim = null
        pulse = 1f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyState(false)
    }

    override fun onDetachedFromWindow() {
        turnAnim?.cancel()
        glowAnim?.cancel()
        autoAnim?.cancel()
        autoAnim = null
        pulseAnim?.cancel()
        pulseAnim = null
        super.onDetachedFromWindow()
    }

    // ---- Layout and drawing ----------------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else {
            min(w, dp(320f).toInt())
        }
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        size = min(w, h).toFloat()
        cx = w / 2f
        cy = h / 2f

        val pw = size * 0.30f
        val ph = size * 0.60f
        phoneRect.set(cx - pw / 2, cy - ph / 2, cx + pw / 2, cy + ph / 2)
        val inset = dp(7f)
        screenRect.set(
            phoneRect.left + inset, phoneRect.top + inset,
            phoneRect.right - inset, phoneRect.bottom - inset
        )

        glowPaint.shader = RadialGradient(
            cx, cy, size * 0.62f,
            intArrayOf(withAlpha(accent, 120), withAlpha(accent2, 46), 0),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        edgeOn.shader = LinearGradient(
            phoneRect.left, phoneRect.top, phoneRect.right, phoneRect.bottom,
            accent, accent2, Shader.TileMode.CLAMP
        )
        screenPaint.shader = LinearGradient(
            0f, screenRect.top, 0f, screenRect.bottom,
            withAlpha(accent, 80), 0, Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (size <= 0f) return

        // Glow behind everything, gently breathing while active.
        if (glow > 0.01f) {
            glowPaint.alpha = (255 * glow).toInt()
            canvas.save()
            canvas.scale(pulse, pulse, cx, cy)
            canvas.drawCircle(cx, cy, size * 0.62f, glowPaint)
            canvas.restore()
        }

        // Orbit rings with four fixed direction ticks.
        val outer = size * 0.46f
        canvas.drawCircle(cx, cy, outer, ringPaint)
        canvas.drawCircle(cx, cy, size * 0.35f, ringPaint)
        val tick = dp(2.5f)
        canvas.drawCircle(cx, cy - outer, tick, tickPaint)
        canvas.drawCircle(cx, cy + outer, tick, tickPaint)
        canvas.drawCircle(cx - outer, cy, tick, tickPaint)
        canvas.drawCircle(cx + outer, cy, tick, tickPaint)

        canvas.save()
        canvas.rotate(angle, cx, cy)

        // Marker at the phone's top edge.
        if (glow > 0.01f) {
            haloPaint.alpha = (80 * glow).toInt()
            markerPaint.alpha = (255 * glow).toInt()
            canvas.drawCircle(cx, cy - outer, dp(10f), haloPaint)
            canvas.drawCircle(cx, cy - outer, dp(5f), markerPaint)
        }

        // Phone body.
        val radius = phoneRect.width() * 0.2f
        canvas.drawRoundRect(phoneRect, radius, radius, bodyPaint)
        if (glow > 0.01f) {
            screenPaint.alpha = (255 * glow).toInt()
            canvas.drawRoundRect(screenRect, radius * 0.7f, radius * 0.7f, screenPaint)
        }
        canvas.drawRoundRect(phoneRect, radius, radius, edgeOff)
        if (glow > 0.01f) {
            edgeOn.alpha = (255 * glow).toInt()
            canvas.drawRoundRect(phoneRect, radius, radius, edgeOn)
        }

        // Notch and home bar make "up" unmistakable.
        pillPaint.alpha = 90
        val pw = phoneRect.width()
        canvas.drawRoundRect(
            cx - pw * 0.14f, phoneRect.top + dp(11f),
            cx + pw * 0.14f, phoneRect.top + dp(16f),
            dp(3f), dp(3f), pillPaint
        )
        pillPaint.alpha = 70
        canvas.drawRoundRect(
            cx - pw * 0.17f, phoneRect.bottom - dp(14f),
            cx + pw * 0.17f, phoneRect.bottom - dp(10.5f),
            dp(2f), dp(2f), pillPaint
        )

        chevronPaint.alpha = (90 + 165 * glow).toInt()
        if (showLock) drawLock(canvas, pw) else drawChevron(canvas, pw)

        canvas.restore()
    }

    private fun drawChevron(canvas: Canvas, pw: Float) {
        val d = pw * 0.17f
        chevron.reset()
        chevron.moveTo(cx - d, cy + d * 0.45f)
        chevron.lineTo(cx, cy - d * 0.65f)
        chevron.lineTo(cx + d, cy + d * 0.45f)
        canvas.drawPath(chevron, chevronPaint)
    }

    private fun drawLock(canvas: Canvas, pw: Float) {
        val lw = pw * 0.36f
        val lh = pw * 0.28f
        val top = cy - lh * 0.35f
        bodyRect.set(cx - lw / 2, top, cx + lw / 2, top + lh)
        val shackle = lw * 0.30f
        arcRect.set(cx - shackle, top - lh * 0.60f, cx + shackle, top + lh * 0.20f)
        canvas.drawArc(arcRect, 180f, 180f, false, chevronPaint)
        canvas.drawLine(cx - shackle, top - lh * 0.20f, cx - shackle, top, chevronPaint)
        canvas.drawLine(cx + shackle, top - lh * 0.20f, cx + shackle, top, chevronPaint)
        canvas.drawRoundRect(bodyRect, dp(4f), dp(4f), chevronPaint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha shl 24)

    private companion object {
        const val Color_WHITE = 0xFFFFFFFF.toInt()

        /** Four quarter-turns with a pause before each one. */
        val STEPS = Interpolator { f ->
            val x = f * 4f
            val k = floor(x)
            val fr = x - k
            val e = if (fr < 0.55f) 0f else {
                val t = (fr - 0.55f) / 0.45f
                t * t * (3f - 2f * t)
            }
            (k + e) / 4f
        }
    }
}
