package com.jarvis.assistant.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * A small circular gauge with a percentage in the middle and a label
 * underneath — e.g. "62%" battery, "35°C" temperature. Styled after the
 * classic Iron Man HUD stat rings (colored arc over a dim track).
 */
class GaugeRingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var progress = 0f // 0..1
    private var centerText = "--"
    private var labelText = ""
    private var accentColor = Color.CYAN

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = Color.argb(60, 255, 255, 255)
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.MONOSPACE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.MONOSPACE
        color = Color.argb(160, 255, 255, 255)
    }

    fun setValue(progressFraction: Float, centerLabel: String, subLabel: String, accent: Int) {
        progress = progressFraction.coerceIn(0f, 1f)
        centerText = centerLabel
        labelText = subLabel
        accentColor = accent
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f - 8f
        val radius = (minOf(width, height) / 2f) * 0.7f

        arcPaint.color = accentColor
        textPaint.color = accentColor
        textPaint.textSize = radius * 0.45f
        labelPaint.textSize = radius * 0.28f

        canvas.drawCircle(cx, cy, radius, trackPaint)
        canvas.drawArc(
            cx - radius, cy - radius, cx + radius, cy + radius,
            -90f, 360f * progress, false, arcPaint
        )
        canvas.drawText(centerText, cx, cy + textPaint.textSize / 3f, textPaint)
        canvas.drawText(labelText, cx, cy + radius + labelPaint.textSize + 6f, labelPaint)
    }
}
