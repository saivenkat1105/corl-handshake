package com.saivenkat.handshake

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import java.util.Locale
import kotlin.math.sqrt

/** Setup screen: arm switch, live IMU trace, card details, and the phone permissions checklist. */
class MainActivity : Activity(), SensorEventListener {

    private lateinit var prefs: Prefs
    private lateinit var armSwitch: Switch
    private lateinit var armStatus: TextView
    private lateinit var meter: ImuTraceView
    private lateinit var sensLabel: TextView
    private val permRows = mutableListOf<PermRow>()
    private var sm: SensorManager? = null
    private val gravity = FloatArray(3)
    private var gravityReady = false
    private var notifAsked = false

    private class PermRow(val check: () -> Boolean, val status: TextView, val fix: View)

    private fun dp(v: Float) = Ui.dp(this, v)

    private val armListener = CompoundButton.OnCheckedChangeListener { _, on -> setArmed(on) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        armSwitch.setOnCheckedChangeListener(null)
        if (prefs.armed && !HandshakeService.running) HandshakeService.start(this)
        armSwitch.isChecked = prefs.armed
        armSwitch.setOnCheckedChangeListener(armListener)
        updateArmStatus()
        refreshPerms()
        val manager = getSystemService(SENSOR_SERVICE) as SensorManager
        sm = manager
        manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        sm?.unregisterListener(this)
        gravityReady = false
        super.onPause()
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (!gravityReady) {
            gravity[0] = e.values[0]; gravity[1] = e.values[1]; gravity[2] = e.values[2]
            gravityReady = true
            return
        }
        val a = 0.9f
        for (i in 0..2) gravity[i] = a * gravity[i] + (1 - a) * e.values[i]
        val x = e.values[0] - gravity[0]
        val y = e.values[1] - gravity[1]
        val z = e.values[2] - gravity[2]
        meter.push(sqrt(x * x + y * y + z * z))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshPerms()
    }

    // ---- actions ----

    private fun setArmed(on: Boolean) {
        prefs.armed = on
        if (on) {
            if (!hasNotif()) requestNotif()
            HandshakeService.start(this)
        } else {
            HandshakeService.stop(this)
        }
        updateArmStatus()
    }

    private fun updateArmStatus() {
        val noCard = prefs.linkedinUrl() == null && prefs.whatsappUrl() == null
        when {
            noCard -> {
                armStatus.text = "Add your LinkedIn or WhatsApp below first."
                armStatus.setTextColor(Ui.ORANGE)
            }
            prefs.armed -> {
                armStatus.text = "Listening for a shake. Works with the screen off."
                armStatus.setTextColor(Ui.GOOD)
            }
            else -> {
                armStatus.text = "Off. Switch on before you walk into the poster session."
                armStatus.setTextColor(Ui.MUTED)
            }
        }
    }

    private fun preview() {
        startActivity(HandshakeActivity.launchIntent(this, null))
    }

    private fun hasNotif(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun canOverlay(): Boolean = Settings.canDrawOverlays(this)

    private fun canFsi(): Boolean =
        if (Build.VERSION.SDK_INT >= 34) getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        else true

    private fun batteryOk(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun requestNotif() {
        if (Build.VERSION.SDK_INT < 33) return
        if (!notifAsked) {
            notifAsked = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        } else {
            open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun openForPackage(action: String) {
        open(Intent(action, Uri.parse("package:$packageName")))
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun refreshPerms() {
        for (row in permRows) {
            val ok = row.check()
            row.status.text = if (ok) "READY" else "NEEDS ACTION"
            row.status.setTextColor(if (ok) Ui.GOOD else Ui.ORANGE)
            row.fix.visibility = if (ok) View.GONE else View.VISIBLE
        }
    }

    // ---- UI ----

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            isFillViewport = true
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(28f), dp(20f), dp(48f))
        }
        scroll.addView(col, ViewGroup.LayoutParams(MATCH, WRAP))

        col.addView(Ui.label(this, "CoRL · IMU handshake"))
        col.addView(TextView(this).apply {
            text = "Shake to connect"
            setTextColor(Ui.FG)
            typeface = Ui.HEAD
            textSize = 32f
        }, lp(top = 6f))
        col.addView(body("Arm it, then give your phone two firm shakes. The screen wakes and a gripper hands over your LinkedIn and WhatsApp QR codes, even from the lock screen."), lp(top = 6f))

        // Arm card
        val armCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(Ui.PANEL, Ui.LINE, dp(14f).toFloat(), dp(1f))
            setPadding(dp(16f), dp(14f), dp(12f), dp(14f))
        }
        val armTexts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        armTexts.addView(TextView(this).apply {
            text = "Handshake armed"
            setTextColor(Ui.FG)
            typeface = Ui.MEDIUM
            textSize = 18f
        })
        armStatus = TextView(this).apply { textSize = 13f }
        armTexts.addView(armStatus, lp(top = 2f))
        armCard.addView(armTexts, LinearLayout.LayoutParams(0, WRAP, 1f))
        armSwitch = Switch(this).apply { isChecked = prefs.armed }
        armCard.addView(armSwitch)
        col.addView(armCard, lp(top = 22f))

        col.addView(Ui.button(this, "Preview the handshake", filled = true) { preview() }, lp(top = 12f))

        // Live IMU
        col.addView(section("Live IMU"), lp(top = 30f))
        meter = ImuTraceView(this).apply { threshold = prefs.threshold() }
        col.addView(meter, LinearLayout.LayoutParams(MATCH, dp(120f)).apply { topMargin = dp(10f) })
        col.addView(caption("Linear acceleration with gravity removed. A trigger needs ${ShakeDetector.PEAKS} crossings of the orange line within ${ShakeDetector.WINDOW_MS / 100 / 10.0} s."), lp(top = 8f))

        sensLabel = TextView(this).apply { setTextColor(Ui.FG); textSize = 14f }
        col.addView(sensLabel, lp(top = 16f))
        updateSensLabel(prefs.sensitivity)
        col.addView(SeekBar(this).apply {
            max = 100
            progress = prefs.sensitivity
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, value: Int, fromUser: Boolean) {
                    prefs.sensitivity = value
                    meter.threshold = Prefs.thresholdFor(value)
                    updateSensLabel(value)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }, lp(top = 4f))

        col.addView(CheckBox(this).apply {
            text = "Ignore shakes while the phone is in a pocket"
            setTextColor(Ui.FG)
            textSize = 14f
            isChecked = prefs.pocketGuard
            setOnCheckedChangeListener { _, on -> prefs.pocketGuard = on }
        }, lp(top = 6f))

        // Card details
        col.addView(section("Your card"), lp(top = 30f))
        col.addView(field("Name", prefs.name, "Sai Venkat",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS) { prefs.name = it }, lp(top = 12f))
        col.addView(field("Tagline", prefs.tagline, "Roboticist · Controls · RL",
            InputType.TYPE_CLASS_TEXT) { prefs.tagline = it }, lp(top = 12f))
        col.addView(field("LinkedIn profile URL or handle", prefs.linkedin, "linkedin.com/in/your-handle",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI) {
            prefs.linkedin = it; updateArmStatus()
        }, lp(top = 12f))
        col.addView(field("WhatsApp number with country code", prefs.whatsapp, "+1 312 555 0142",
            InputType.TYPE_CLASS_PHONE) {
            prefs.whatsapp = it; updateArmStatus()
        }, lp(top = 12f))
        col.addView(field("Message they send when they scan WhatsApp", prefs.greeting, "Hi Sai, great meeting you at CoRL!",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) { prefs.greeting = it }, lp(top = 12f))

        // Permissions
        col.addView(section("Phone setup"), lp(top = 30f))
        col.addView(caption("Your Nothing Phone needs these so a shake can wake the screen and show the card over the lock screen."), lp(top = 6f))
        permRow(col, "Notifications", "Shows the armed indicator and the wake-up alert", ::hasNotif) { requestNotif() }
        permRow(col, "Display over other apps", "Lets a shake open the handshake screen directly", ::canOverlay) {
            openForPackage(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            permRow(col, "Full-screen alerts", "Backup way to wake the screen on a locked phone", ::canFsi) {
                openForPackage(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            }
        }
        permRow(col, "Unrestricted battery", "Stops Android from pausing the motion sensor", ::batteryOk) {
            openForPackage(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        }

        return scroll
    }

    private fun updateSensLabel(value: Int) {
        val word = when {
            value < 34 -> "Firm"
            value < 67 -> "Normal"
            else -> "Light"
        }
        sensLabel.text = String.format(Locale.US, "Sensitivity: %s (trigger at %.1f g)", word, Prefs.thresholdFor(value) / 9.81f)
    }

    private fun permRow(col: LinearLayout, title: String, desc: String, check: () -> Boolean, fix: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(Ui.PANEL, Ui.LINE, dp(12f).toFloat(), dp(1f))
            setPadding(dp(14f), dp(12f), dp(10f), dp(12f))
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val status = Ui.label(this, "")
        texts.addView(status)
        texts.addView(TextView(this).apply {
            text = title
            setTextColor(Ui.FG)
            typeface = Ui.MEDIUM
            textSize = 15f
        }, lp(top = 2f))
        texts.addView(TextView(this).apply {
            text = desc
            setTextColor(Ui.MUTED)
            textSize = 12.5f
        }, lp(top = 1f))
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
        val button = Ui.button(this, "Allow", filled = true) { fix() }.apply {
            minHeight = dp(40f)
            minimumHeight = dp(40f)
            textSize = 14f
            setPadding(dp(16f), 0, dp(16f), 0)
        }
        row.addView(button, LinearLayout.LayoutParams(WRAP, dp(40f)).apply { marginStart = dp(10f) })
        col.addView(row, lp(top = 10f))
        permRows.add(PermRow(check, status, button))
    }

    private fun field(label: String, value: String, hint: String, type: Int, onChange: (String) -> Unit): View {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(Ui.label(this, label, Ui.MUTED))
        val et = EditText(this).apply {
            id = View.generateViewId()
            setText(value)
            this.hint = hint
            inputType = type
            setTextColor(Ui.FG)
            setHintTextColor(0xFF5C636E.toInt())
            textSize = 16f
            background = Ui.rounded(Ui.PANEL, Ui.LINE, dp(10f).toFloat(), dp(1f))
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) { onChange(s?.toString() ?: "") }
            })
        }
        wrap.addView(et, lp(top = 6f))
        return wrap
    }

    private fun section(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Ui.FG)
        typeface = Ui.HEAD
        textSize = 20f
    }

    private fun body(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Ui.MUTED)
        textSize = 15f
        setLineSpacing(0f, 1.25f)
    }

    private fun caption(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Ui.MUTED)
        textSize = 12.5f
        setLineSpacing(0f, 1.2f)
    }

    private fun lp(top: Float): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(top) }
}

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
