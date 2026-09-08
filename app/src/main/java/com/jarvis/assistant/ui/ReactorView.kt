package com.jarvis.assistant.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.jarvis.assistant.R
import kotlin.math.cos
import kotlin.math.sin

enum class ReactorState { IDLE, LISTENING, THINKING, SPEAKING, ERROR }

/**
 * A JARVIS-style animated "reactor core": rotating arcs, a pulsing glow,
 * and a waveform ring that reacts to the current assistant state.
 *
 * States map to distinct colors + motion so the state is legible from
 * across the room, not just from the text underneath it:
 *   IDLE       - slow cyan rotation, gentle breathing pulse
 *   LISTENING  - green, waveform ring reacts to amplitude (see setAmplitude)
 *   THINKING   - violet, faster rotation, tighter pulse
 *   SPEAKING   - amber, waveform ring driven by TTS output
 *   ERROR      - red, brief sharp flash
 */
class ReactorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var state = ReactorState.IDLE
    private var rotation = 0f
    private var pulse = 0f
    private var amplitude = 0f // 0..1, set from live audio level while listening/speaking

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val loopAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 16
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            val speed = when (state) {
                ReactorState.THINKING -> 6f
                ReactorState.LISTENING, ReactorState.SPEAKING -> 3f
                ReactorState.ERROR -> 10f
                ReactorState.IDLE -> 1.2f
            }
            rotation = (rotation + speed) % 360f
            pulse = ((pulse + 0.02f) % (Math.PI.toFloat() * 2))
            invalidate()
        }
    }

    init {
        loopAnimator.start()
    }

    fun setState(newState: ReactorState) {
        state = newState
        invalidate()
    }

    /** Feed live mic (0..1) or TTS envelope amplitude in for a reactive waveform ring. */
    fun setAmplitude(level: Float) {
        amplitude = level.coerceIn(0f, 1f)
    }

    private var idleAccent: Int = context.getColor(R.color.core_idle_cyan)

    fun setThemeAccent(color: Int) {
        idleAccent = color
        invalidate()
    }

    private fun colorForState(): Int = when (state) {
            ReactorState.IDLE -> idleAccent
            ReactorState.LISTENING -> context.getColor(R.color.core_listening_green)
            ReactorState.THINKING -> context.getColor(R.color.core_thinking_violet)
            ReactorState.SPEAKING -> context.getColor(R.color.core_speaking_amber)
            ReactorState.ERROR -> context.getColor(R.color.core_error_red)
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = (minOf(width, height) / 2f) * 0.55f
        val color = colorForState()

        // Outer glow
        glowPaint.shader = RadialGradient(
            cx, cy, baseRadius * 1.8f,
            intArrayOf(ColorUtilsAlpha(color, 60), ColorUtilsAlpha(color, 0)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, baseRadius * 1.8f, glowPaint)

        // Breathing core disc
        val breathe = 1f + 0.06f * sin(pulse)
        dotPaint.color = ColorUtilsAlpha(color, 220)
        canvas.drawCircle(cx, cy, baseRadius * 0.35f * breathe, dotPaint)

        // Rotating segmented arcs (3 rings, offset rotation each)
        for (i in 0..2) {
            ringPaint.color = ColorUtilsAlpha(color, 200 - i * 40)
            ringPaint.strokeWidth = 5f - i
            val radius = baseRadius * (0.6f + i * 0.18f)
            val startAngle = rotation * (1f + i * 0.4f) * if (i % 2 == 0) 1 else -1
            canvas.drawArc(
                cx - radius, cy - radius, cx + radius, cy + radius,
                startAngle, 100f, false, ringPaint
            )
            canvas.drawArc(
                cx - radius, cy - radius, cx + radius, cy + radius,
                startAngle + 180f, 100f, false, ringPaint
            )
        }

        // Reactive waveform ring — spikes outward with amplitude while listening/speaking
        if (state == ReactorState.LISTENING || state == ReactorState.SPEAKING) {
            val points = 48
            val waveRadius = baseRadius * 0.95f
            val path = Path()
            for (i in 0..points) {
                val angle = (i.toDouble() / points) * 2 * Math.PI
                val jitter = (sin(angle * 6 + pulse * 3) * 0.5 + 0.5) * amplitude * baseRadius * 0.25
                val r = waveRadius + jitter
                val x = cx + (r * cos(angle)).toFloat()
                val y = cy + (r * sin(angle)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            ringPaint.color = ColorUtilsAlpha(color, 160)
            ringPaint.strokeWidth = 3f
            canvas.drawPath(path, ringPaint)
        }

        // Orbiting tick marks around the perimeter (idle detail)
        dotPaint.color = ColorUtilsAlpha(color, 140)
        for (i in 0 until 12) {
            val angle = Math.toRadians((i * 30f + rotation * 0.3f).toDouble())
            val r = baseRadius * 1.15f
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            canvas.drawCircle(x, y, 3f, dotPaint)
        }
    }

    private fun ColorUtilsAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    override fun onDetachedFromWindow() {
        loopAnimator.cancel()
        super.onDetachedFromWindow()
    }
}
