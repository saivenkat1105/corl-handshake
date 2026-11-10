package com.saivenkat.handshake

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import java.util.Locale
import kotlin.math.min

/** Scrolling trace of linear-acceleration magnitude with the trigger threshold. */
class ImuTraceView(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d

    private val samples = FloatArray(200)
    private var head = 0
    private var count = 0
    private val maxVal = 3f * 9.81f

    var threshold = 15f
        set(v) { field = v; invalidate() }

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.PANEL }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.LINE; strokeWidth = dp(1f) }
    private val trace = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.FG; style = Paint.Style.STROKE; strokeWidth = dp(1.6f)
        strokeJoin = Paint.Join.ROUND
    }
    private val thr = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.ORANGE; strokeWidth = dp(1.5f); style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(dp(6f), dp(4f)), 0f)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.MUTED; textSize = dp(10.5f); typeface = Ui.MONO; fontFeatureSettings = "tnum"
    }
    private val thrText = Paint(text).apply { color = Ui.ORANGE; textAlign = Paint.Align.RIGHT }
    private val path = Path()

    fun push(v: Float) {
        samples[head] = v
        head = (head + 1) % samples.size
        if (count < samples.size) count++
        invalidate()
    }

    private fun yOf(v: Float, h: Float): Float {
        val pad = dp(10f)
        return h - pad - (min(v, maxVal) / maxVal) * (h - 2 * pad)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawRoundRect(0f, 0f, w, h, dp(12f), dp(12f), bg)

        for (g in 1..2) {
            val y = yOf(g * 9.81f, h)
            c.drawLine(dp(10f), y, w - dp(10f), y, grid)
            c.drawText("$g g", dp(12f), y - dp(4f), text)
        }

        val ty = yOf(threshold, h)
        c.drawLine(dp(10f), ty, w - dp(10f), ty, thr)
        c.drawText(String.format(Locale.US, "trigger %.1f g", threshold / 9.81f), w - dp(12f), ty - dp(5f), thrText)

        if (count > 1) {
            path.reset()
            val n = samples.size
            for (i in 0 until count) {
                val idx = (head - count + i + n) % n
                val x = dp(10f) + (w - dp(20f)) * i / (n - 1)
                val y = yOf(samples[idx], h)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            c.drawPath(path, trace)
            val last = samples[(head - 1 + n) % n]
            c.drawText(String.format(Locale.US, "|a| %.2f g", last / 9.81f), dp(12f), h - dp(14f), text)
        }
    }
}
