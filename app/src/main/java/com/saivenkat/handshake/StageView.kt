package com.saivenkat.handshake

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import java.util.Locale

/**
 * Draws the gantry workcell: dotted floor, linear rail with a mm scale,
 * carriage, Z rod, parallel-jaw gripper and the card's grip tab, plus live axis readouts.
 * The card itself is a real View positioned by HandshakeActivity.
 */
class StageView(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d

    var railY = dp(46f)
    var carriageX = -10_000f
    var rodLen = dp(14f)
    var jaw = 0f
    var cardTop = 0f
    var phase = "HOMING"
    var done = false

    val rodTopOffset get() = dp(14f)
    val gripperSpan get() = dp(34f)

    fun cardTopFor(rod: Float): Float = railY + rodTopOffset + rod + gripperSpan

    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.LINE }
    private val rail = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A414B.toInt() }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.LINE; strokeWidth = dp(1f) }
    private val metal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.METAL }
    private val orange = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.ORANGE }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.PANEL2 }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.LINE; style = Paint.Style.STROKE; strokeWidth = dp(1f)
    }
    private val tab = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.PANEL }
    private val led = Paint(Paint.ANTI_ALIAS_FLAG)
    private val phasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Ui.MONO; textSize = dp(11f); letterSpacing = 0.12f
    }
    private val readout = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Ui.MONO; textSize = dp(11f); color = Ui.MUTED; textAlign = Paint.Align.RIGHT
        fontFeatureSettings = "tnum"
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Workcell floor
        val step = dp(24f)
        var y = railY + step
        while (y < h) {
            var x = step / 2
            while (x < w) { c.drawCircle(x, y, dp(1f), dot); x += step }
            y += step
        }

        // Rail and its scale (one tick per 10 mm, long tick per 50 mm)
        val left = dp(16f)
        val right = w - dp(16f)
        c.drawRoundRect(left, railY - dp(4f), right, railY + dp(4f), dp(2f), dp(2f), rail)
        var tx = left
        var i = 0
        while (tx <= right) {
            val len = if (i % 5 == 0) dp(7f) else dp(3.5f)
            c.drawLine(tx, railY - dp(7f) - len, tx, railY - dp(7f), tick)
            tx += dp(10f); i++
        }

        val cx = carriageX
        val rodTop = railY + rodTopOffset
        val palmTop = rodTop + rodLen
        val palmBottom = palmTop + dp(12f)

        // Grip tab on the card (card View covers its lower edge)
        if (cardTop > 0f) {
            c.drawRoundRect(cx - dp(15f), cardTop - dp(20f), cx + dp(15f), cardTop + dp(8f), dp(4f), dp(4f), tab)
            c.drawRoundRect(cx - dp(15f), cardTop - dp(20f), cx + dp(15f), cardTop + dp(8f), dp(4f), dp(4f), outline)
        }

        // Z rod
        c.drawRect(cx - dp(3.5f), rodTop - dp(4f), cx + dp(3.5f), palmTop, metal)

        // Parallel jaws
        val off = dp(19f) + jaw * dp(12f)
        for (s in intArrayOf(-1, 1)) {
            val fx = cx + s * off
            c.drawRoundRect(fx - dp(4f), palmBottom - dp(2f), fx + dp(4f), palmBottom + dp(20f), dp(3f), dp(3f), metal)
        }

        // Palm
        c.drawRoundRect(cx - dp(29f), palmTop, cx + dp(29f), palmBottom, dp(3f), dp(3f), orange)

        // Carriage
        c.drawRoundRect(cx - dp(32f), railY - dp(11f), cx + dp(32f), railY + dp(14f), dp(5f), dp(5f), body)
        c.drawRoundRect(cx - dp(32f), railY - dp(11f), cx + dp(32f), railY + dp(14f), dp(5f), dp(5f), outline)
        c.drawRect(cx - dp(24f), railY - dp(11f), cx + dp(24f), railY - dp(8f), orange)
        led.color = if (done) Ui.GOOD else Ui.ORANGE
        c.drawCircle(cx + dp(22f), railY + dp(4f), dp(3f), led)

        // Readouts
        val baseline = railY - dp(24f)
        phasePaint.color = if (done) Ui.GOOD else Ui.ORANGE
        c.drawText(phase, left, baseline, phasePaint)
        val xmm = (carriageX - w / 2) / d
        val zmm = rodLen / d
        val grip = if (jaw > 0.5f) "OPEN" else "SHUT"
        val text = String.format(Locale.US, "X%+7.1f  Z%6.1f  %s", xmm.coerceIn(-9999f, 9999f), zmm, grip)
        c.drawText(text, right, baseline, readout)
    }
}
