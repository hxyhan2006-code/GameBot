package com.example.universalgamebot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class ClickerHomeActivity : Activity() {
    override fun attachBaseContext(base: Context) { super.attachBaseContext(LocaleHelper.wrap(base)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Palette.BG)
            addView(TextView(this@ClickerHomeActivity).apply {
                text = "Auto Clicker"
                textSize = 26f
                setTextColor(Palette.TEXT)
            })
            addView(actionButton(getString(R.string.clicker_open), Palette.ACCENT, Palette.ON_ACCENT) {
                val service = BotAccessibilityService.instance
                if (service == null) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } else if (service.showClicker()) {
                    finish()
                } else {
                    Toast.makeText(this@ClickerHomeActivity, R.string.clicker_busy, Toast.LENGTH_SHORT).show()
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
        })
    }
}
