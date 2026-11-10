package com.saivenkat.handshake

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import kotlin.math.max
import kotlin.math.sqrt

data class ShakeStats(val peaks: Int, val peakG: Float, val spanMs: Long)

/**
 * Gravity-compensated shake detector.
 * A low-pass filter tracks gravity; the residual is linear acceleration.
 * A trigger needs [PEAKS] threshold crossings inside [WINDOW_MS] (two firm back-and-forth shakes).
 */
class ShakeDetector(private val onShake: (ShakeStats) -> Unit) : SensorEventListener {

    @Volatile var thresholdMs2: Float = 15f
    @Volatile var pocketed: Boolean = false

    private val gravity = FloatArray(3)
    private var gravityReady = false
    private val peakTimes = ArrayDeque<Long>()
    private var lastPeakMs = 0L
    private var above = false
    private var maxPeak = 0f
    private var cooldownUntil = 0L

    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val now = e.timestamp / 1_000_000L
        if (!gravityReady) {
            gravity[0] = e.values[0]; gravity[1] = e.values[1]; gravity[2] = e.values[2]
            gravityReady = true
            return
        }
        val a = 0.9f
        gravity[0] = a * gravity[0] + (1 - a) * e.values[0]
        gravity[1] = a * gravity[1] + (1 - a) * e.values[1]
        gravity[2] = a * gravity[2] + (1 - a) * e.values[2]
        val lx = e.values[0] - gravity[0]
        val ly = e.values[1] - gravity[1]
        val lz = e.values[2] - gravity[2]
        val mag = sqrt(lx * lx + ly * ly + lz * lz)

        if (now < cooldownUntil || pocketed) {
            peakTimes.clear(); maxPeak = 0f; above = false
            return
        }

        while (peakTimes.isNotEmpty() && now - peakTimes.first() > WINDOW_MS) peakTimes.removeFirst()
        if (peakTimes.isEmpty()) maxPeak = 0f

        val t = thresholdMs2
        if (mag > t) {
            maxPeak = max(maxPeak, mag)
            if (!above && now - lastPeakMs > DEBOUNCE_MS) {
                above = true
                lastPeakMs = now
                peakTimes.addLast(now)
                if (peakTimes.size >= PEAKS) {
                    val stats = ShakeStats(peakTimes.size, maxPeak / 9.81f, now - peakTimes.first())
                    peakTimes.clear()
                    maxPeak = 0f
                    cooldownUntil = now + COOLDOWN_MS
                    onShake(stats)
                }
            }
        } else if (mag < t * 0.6f) {
            above = false
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        const val PEAKS = 4
        const val WINDOW_MS = 1300L
        const val DEBOUNCE_MS = 90L
        const val COOLDOWN_MS = 4000L
    }
}
