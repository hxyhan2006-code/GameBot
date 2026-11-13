package com.example.universalgamebot

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** เก็บ/ใช้ภาษาของแอป (th / en) ค่าเริ่มต้น = ตามภาษาเครื่อง ถ้าไม่ใช่ไทยใช้อังกฤษ */
object LocaleHelper {
    private const val PREFS = "settings"
    private const val KEY = "lang"

    fun current(base: Context): String {
        val saved = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        if (saved == "th" || saved == "en") return saved
        return if (base.resources.configuration.locales[0].language == "th") "th" else "en"
    }

    fun toggle(base: Context) {
        val next = if (current(base) == "th") "en" else "th"
        base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, next).apply()
    }

    fun wrap(base: Context): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale(current(base)))
        return base.createConfigurationContext(config)
    }
}
