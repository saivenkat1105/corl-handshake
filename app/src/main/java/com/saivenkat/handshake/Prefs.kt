package com.saivenkat.handshake

import android.content.Context
import android.content.SharedPreferences
import java.net.URLEncoder

class Prefs(ctx: Context) {
    val sp: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("handshake", Context.MODE_PRIVATE)

    var name: String
        get() = sp.getString("name", "Sai Venkat") ?: ""
        set(v) { sp.edit().putString("name", v).apply() }

    var tagline: String
        get() = sp.getString("tagline", "Roboticist · Controls · RL") ?: ""
        set(v) { sp.edit().putString("tagline", v).apply() }

    var linkedin: String
        get() = sp.getString("linkedin", "") ?: ""
        set(v) { sp.edit().putString("linkedin", v).apply() }

    var whatsapp: String
        get() = sp.getString("whatsapp", "") ?: ""
        set(v) { sp.edit().putString("whatsapp", v).apply() }

    var greeting: String
        get() = sp.getString("greeting", "Hi Sai, great meeting you at CoRL!") ?: ""
        set(v) { sp.edit().putString("greeting", v).apply() }

    /** 0 = needs a hard shake, 100 = light shake. */
    var sensitivity: Int
        get() = sp.getInt("sens", 50)
        set(v) { sp.edit().putInt("sens", v).apply() }

    var pocketGuard: Boolean
        get() = sp.getBoolean("pocket", true)
        set(v) { sp.edit().putBoolean("pocket", v).apply() }

    var armed: Boolean
        get() = sp.getBoolean("armed", false)
        set(v) { sp.edit().putBoolean("armed", v).apply() }

    /** Linear-acceleration peak (m/s²) a shake must cross. */
    fun threshold(): Float = thresholdFor(sensitivity)

    fun linkedinUrl(): String? {
        val raw = linkedin.trim()
        if (raw.isEmpty()) return null
        return when {
            raw.startsWith("http://", true) || raw.startsWith("https://", true) -> raw
            raw.contains("linkedin.com", true) -> "https://$raw"
            else -> "https://www.linkedin.com/in/" + raw.removePrefix("@").trim('/')
        }
    }

    fun linkedinLabel(): String =
        linkedinUrl()?.substringAfter("linkedin.com", "")?.trim('/')?.ifEmpty { null } ?: "linkedin"

    fun whatsappDigits(): String = whatsapp.filter { it.isDigit() }

    fun whatsappUrl(): String? {
        val d = whatsappDigits()
        if (d.length < 7) return null
        val msg = greeting.trim()
        return if (msg.isEmpty()) "https://wa.me/$d"
        else "https://wa.me/$d?text=" + URLEncoder.encode(msg, "UTF-8").replace("+", "%20")
    }

    companion object {
        fun thresholdFor(sensitivity: Int): Float = 22f - sensitivity.coerceIn(0, 100) * 0.14f
    }
}
