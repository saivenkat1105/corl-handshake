package com.saivenkat.handshake

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.TextView

/** Workcell palette: graphite floor, safety orange, brushed metal. */
object Ui {
    val BG = 0xFF101215.toInt()
    val PANEL = 0xFF1A1D22.toInt()
    val PANEL2 = 0xFF252A31.toInt()
    val LINE = 0xFF2E343D.toInt()
    val FG = 0xFFECEEF1.toInt()
    val MUTED = 0xFF8D95A1.toInt()
    val ORANGE = 0xFFFF6B1A.toInt()
    val GOOD = 0xFF4CC38A.toInt()
    val METAL = 0xFFC9CED6.toInt()

    val MONO: Typeface = Typeface.MONOSPACE
    val HEAD: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun dp(ctx: Context, v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics).toInt()

    fun rounded(fill: Int, stroke: Int?, radius: Float, strokeWidth: Int = 2): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius
            if (stroke != null) setStroke(strokeWidth, stroke)
        }

    fun label(ctx: Context, text: String, color: Int = ORANGE): TextView = TextView(ctx).apply {
        this.text = text
        setTextColor(color)
        typeface = MONO
        textSize = 11f
        letterSpacing = 0.14f
        isAllCaps = true
    }

    fun button(ctx: Context, text: String, filled: Boolean, onClick: () -> Unit): Button =
        Button(ctx).apply {
            this.text = text
            isAllCaps = false
            textSize = 16f
            typeface = MEDIUM
            stateListAnimator = null
            minHeight = dp(ctx, 52f)
            gravity = Gravity.CENTER
            val r = dp(ctx, 12f).toFloat()
            val base = if (filled) rounded(ORANGE, null, r) else rounded(PANEL, LINE, r, dp(ctx, 1.5f))
            background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), base, null)
            setTextColor(if (filled) BG else FG)
            setOnClickListener { onClick() }
        }
}
