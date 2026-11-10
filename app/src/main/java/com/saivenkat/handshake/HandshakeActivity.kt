package com.saivenkat.handshake

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Full-screen handshake: a gantry gripper delivers the QR card. Shows over the lock screen. */
class HandshakeActivity : Activity() {

    companion object {
        @Volatile var visible = false

        fun launchIntent(ctx: Context, stats: ShakeStats?): Intent =
            Intent(ctx, HandshakeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (stats != null) {
                    putExtra("peaks", stats.peaks)
                    putExtra("g", stats.peakG)
                    putExtra("span", stats.spanMs)
                }
            }

        /** Minimum-jerk profile, the smooth S-curve robot arms use between waypoints. */
        fun mj(t: Float): Float {
            val x = t.coerceIn(0f, 1f)
            return x * x * x * (10f - 15f * x + 6f * x * x)
        }

        fun seg(p: Float, a: Float, b: Float): Float = ((p - a) / (b - a)).coerceIn(0f, 1f)
    }

    private lateinit var stage: StageView
    private lateinit var card: LinearLayout
    private lateinit var footer: LinearLayout
    private lateinit var telemetry: TextView
    private var animator: ValueAnimator? = null
    private var startedAt = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val autoClose = Runnable { finish() }

    private fun dp(v: Float) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.also { it.screenBrightness = 1f }

        val prefs = Prefs(this)
        val root = FrameLayout(this).apply { setBackgroundColor(Ui.BG) }

        stage = StageView(this)
        root.addView(stage, FrameLayout.LayoutParams(MATCH, MATCH))

        val dm = resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels
        val cardW = min(screenW - dp(24f), dp(440f))
        val items = qrItems(prefs)
        val qrSize = qrSizeFor(items.size, cardW, screenH)
        card = buildCard(prefs, items, qrSize)
        root.addView(card, FrameLayout.LayoutParams(cardW, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        card.translationX = screenW * 2f

        footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16f), 0, dp(16f), dp(14f))
        }
        telemetry = TextView(this).apply {
            setTextColor(Ui.MUTED)
            typeface = Ui.MONO
            textSize = 11f
            gravity = Gravity.CENTER
            fontFeatureSettings = "tnum"
        }
        footer.addView(telemetry, LinearLayout.LayoutParams(MATCH, WRAP))
        val done = Ui.button(this, "Done", filled = false) { finish() }.apply { minHeight = dp(44f) }
        footer.addView(done, LinearLayout.LayoutParams(min(cardW, dp(220f)), dp(44f)).apply { topMargin = dp(8f) })
        root.addView(footer, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))

        setContentView(root)
        updateTelemetry()
        root.post { runSequence() }
        handler.postDelayed(autoClose, 120_000)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (SystemClock.uptimeMillis() - startedAt > 3000) {
            updateTelemetry()
            runSequence()
            handler.removeCallbacks(autoClose)
            handler.postDelayed(autoClose, 120_000)
        }
    }

    override fun onResume() {
        super.onResume()
        visible = true
        getSystemService(NotificationManager::class.java).cancel(HandshakeService.TRIGGER_ID)
    }

    override fun onPause() {
        visible = false
        super.onPause()
    }

    override fun onDestroy() {
        animator?.cancel()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun updateTelemetry() {
        val peaks = intent.getIntExtra("peaks", 0)
        telemetry.text = if (peaks > 0) {
            val g = intent.getFloatExtra("g", 0f)
            val span = intent.getLongExtra("span", 0L)
            String.format(Locale.US, "IMU  ·  %d peaks in %d ms  ·  max %.1f g", peaks, span, g)
        } else {
            "MANUAL TRIGGER  ·  no shake recorded"
        }
    }

    private fun runSequence() {
        animator?.cancel()
        startedAt = SystemClock.uptimeMillis()
        val w = stage.width.toFloat()
        val cardW = card.width.toFloat()
        val startX = w + cardW / 2f + dp(24f)
        val endX = w / 2f
        val rodMin = dp(14f).toFloat()
        val topMin = stage.cardTopFor(rodMin)
        val availBottom = footer.top - dp(16f).toFloat()
        val finalTop = max(topMin, (topMin + availBottom - card.height) / 2f)
        val rodMax = rodMin + (finalTop - topMin)

        stage.done = false
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2600
            interpolator = LinearInterpolator()
            addUpdateListener { a ->
                val p = a.animatedValue as Float
                val x = startX + (endX - startX) * mj(seg(p, 0f, 0.36f))
                val rod = if (p < 0.76f) rodMin + (rodMax - rodMin) * mj(seg(p, 0.40f, 0.64f))
                else rodMax + (rodMin - rodMax) * mj(seg(p, 0.76f, 1f))
                val jaw = if (p < 0.76f) mj(seg(p, 0.66f, 0.74f)) else 1f - mj(seg(p, 0.92f, 1f))
                val top = if (p < 0.66f) stage.cardTopFor(rod) else finalTop

                stage.carriageX = x
                stage.rodLen = rod
                stage.jaw = jaw
                stage.cardTop = top
                stage.phase = when {
                    p < 0.38f -> "TRAVERSE"
                    p < 0.65f -> "LOWER"
                    p < 0.76f -> "RELEASE"
                    else -> "RETRACT"
                }
                card.translationX = x - w / 2f
                card.translationY = top
                stage.invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    stage.phase = "HANDSHAKE COMPLETE"
                    stage.done = true
                    stage.invalidate()
                }
            })
            start()
        }
    }

    private class QrItem(val label: String, val caption: String, val url: String)

    private fun qrItems(p: Prefs): List<QrItem> = listOfNotNull(
        p.linkedinUrl()?.let { QrItem("LinkedIn", p.linkedinLabel(), it) },
        p.whatsappUrl()?.let { QrItem("WhatsApp", "+" + p.whatsappDigits(), it) },
    )

    /** Largest QR side (px) that lets every code fit on screen at once, no tapping needed. */
    private fun qrSizeFor(count: Int, cardW: Int, screenH: Int): Int {
        if (count == 0) return 0
        val statusId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (statusId > 0) resources.getDimensionPixelSize(statusId) else dp(24f)
        val topReserve = stage.cardTopFor(dp(14f).toFloat()).toInt()
        val footerReserve = dp(84f)
        val cardOverhead = dp(30f) + dp(58f) + count * dp(38f) + (count - 1) * dp(12f)
        val avail = screenH - statusBar - topReserve - footerReserve - cardOverhead - dp(8f)
        val inner = cardW - dp(32f) - dp(12f)
        return min(inner, avail / count).coerceAtLeast(dp(120f))
    }

    private fun buildCard(p: Prefs, items: List<QrItem>, qrSize: Int): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(Ui.PANEL, Ui.LINE, dp(18f).toFloat(), dp(1f))
            setPadding(dp(16f), dp(16f), dp(16f), dp(14f))
            elevation = dp(8f).toFloat()
        }
        c.addView(TextView(this).apply {
            text = p.name.ifBlank { "Hello" }
            setTextColor(Ui.FG)
            typeface = Ui.HEAD
            textSize = 24f
        })
        if (p.tagline.isNotBlank()) {
            c.addView(TextView(this).apply {
                text = p.tagline
                setTextColor(Ui.MUTED)
                textSize = 14f
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(2f) })
        }

        if (items.isEmpty()) {
            c.addView(TextView(this).apply {
                text = "Open the Handshake app and add your LinkedIn or WhatsApp to show QR codes here."
                setTextColor(Ui.FG)
                textSize = 15f
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16f) })
            return c
        }

        items.forEachIndexed { i, item ->
            c.addView(qrBlock(item, qrSize), LinearLayout.LayoutParams(MATCH, WRAP).apply {
                topMargin = if (i == 0) dp(14f) else dp(12f)
            })
        }
        return c
    }

    private fun qrBlock(item: QrItem, qrSize: Int): View {
        val b = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(Ui.label(this, item.label))
        header.addView(TextView(this).apply {
            text = item.caption
            setTextColor(Ui.MUTED)
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            gravity = Gravity.END
        }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(10f) })
        b.addView(header, LinearLayout.LayoutParams(qrSize + dp(12f), WRAP))

        val frame = FrameLayout(this).apply {
            background = Ui.rounded(0xFFFFFFFF.toInt(), null, dp(10f).toFloat())
            setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
        }
        val img = ImageView(this).apply {
            val bmp: Bitmap = Qr.bitmap(item.url)
            setImageDrawable(BitmapDrawable(resources, bmp).apply { isFilterBitmap = false })
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "${item.label} QR code"
        }
        frame.addView(img, FrameLayout.LayoutParams(qrSize, qrSize))
        b.addView(frame, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(6f) })
        return b
    }
}

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
