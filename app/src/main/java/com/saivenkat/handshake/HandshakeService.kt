package com.saivenkat.handshake

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

/** Foreground service that keeps the accelerometer running, screen on or off. */
class HandshakeService : Service() {

    companion object {
        const val ACTION_STOP = "com.saivenkat.handshake.STOP"
        const val CH_ARMED = "armed"
        const val CH_TRIGGER = "trigger"
        const val NOTIF_ID = 7
        const val TRIGGER_ID = 8

        @Volatile var running = false

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, HandshakeService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, HandshakeService::class.java))
        }
    }

    private var sensing = false
    private var sm: SensorManager? = null
    private var thread: HandlerThread? = null
    private var sensorHandler: Handler? = null
    private val main = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var prefs: Prefs
    private lateinit var detector: ShakeDetector
    private var proximity: SensorEventListener? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "sens" -> detector.thresholdMs2 = prefs.threshold()
            "pocket" -> if (prefs.pocketGuard) registerProximity() else unregisterProximity()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        detector = ShakeDetector { stats -> main.post { onShake(stats) } }
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            prefs.armed = false
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = armedNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        if (!prefs.armed) prefs.armed = true
        if (!sensing) startSensing()
        running = true
        return START_STICKY
    }

    private fun startSensing() {
        val manager = getSystemService(SENSOR_SERVICE) as SensorManager
        sm = manager
        val t = HandlerThread("imu").apply { start() }
        thread = t
        val h = Handler(t.looper)
        sensorHandler = h
        detector.thresholdMs2 = prefs.threshold()
        val accel = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        manager.registerListener(detector, accel, SensorManager.SENSOR_DELAY_GAME, 0, h)
        if (prefs.pocketGuard) registerProximity()
        prefs.sp.registerOnSharedPreferenceChangeListener(prefListener)

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "handshake:imu").apply {
            setReferenceCounted(false)
            acquire()
        }
        sensing = true
    }

    private fun registerProximity() {
        val manager = sm ?: return
        if (proximity != null) return
        val prox = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY) ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                detector.pocketed = e.values[0] < e.sensor.maximumRange
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        proximity = listener
        manager.registerListener(listener, prox, SensorManager.SENSOR_DELAY_NORMAL, sensorHandler)
    }

    private fun unregisterProximity() {
        proximity?.let { sm?.unregisterListener(it) }
        proximity = null
        detector.pocketed = false
    }

    private fun onShake(stats: ShakeStats) {
        if (HandshakeActivity.visible) return
        buzz()
        wakeScreen()
        val intent = HandshakeActivity.launchIntent(this, stats)
        if (Settings.canDrawOverlays(this)) {
            try {
                startActivity(intent)
            } catch (e: Exception) {
            }
        }
        // Backup path: a full-screen alert opens the same screen when the phone is locked.
        val pi = PendingIntent.getActivity(
            this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CH_TRIGGER)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Handshake")
            .setContentText("Tap to show your QR codes")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setTimeoutAfter(30_000)
            .build()
        getSystemService(NotificationManager::class.java).notify(TRIGGER_ID, n)
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isInteractive) return
        val wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "handshake:wake"
        )
        wl.acquire(4000)
    }

    private fun buzz() {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 70, 30, 70, 110), -1))
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ARMED, "Armed indicator", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while the app is listening for a shake"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_TRIGGER, "Handshake alert", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Wakes the screen with your QR codes after a shake"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    private fun armedNotification(): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val show = PendingIntent.getActivity(this, 3, HandshakeActivity.launchIntent(this, null), flags)
        val stop = PendingIntent.getService(
            this, 2, Intent(this, HandshakeService::class.java).setAction(ACTION_STOP), flags
        )
        val icon = Icon.createWithResource(this, R.drawable.ic_stat)
        return Notification.Builder(this, CH_ARMED)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Handshake armed")
            .setContentText("Shake firmly twice to show your QR codes")
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(icon, "Show now", show).build())
            .addAction(Notification.Action.Builder(icon, "Disarm", stop).build())
            .build()
    }

    override fun onDestroy() {
        running = false
        try { prefs.sp.unregisterOnSharedPreferenceChangeListener(prefListener) } catch (e: Exception) {}
        sm?.unregisterListener(detector)
        unregisterProximity()
        thread?.quitSafely()
        wakeLock?.let { if (it.isHeld) it.release() }
        sensing = false
        super.onDestroy()
    }
}
