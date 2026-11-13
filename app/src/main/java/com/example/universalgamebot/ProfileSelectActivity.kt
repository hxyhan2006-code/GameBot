package com.example.universalgamebot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** หน้าแรก: เลือกโปรไฟล์เกม (แต่ละโปรไฟล์ = เกมทดสอบ + วิธีตรวจจับ + วิธีตัดสินใจ ของตัวเอง) */
class ProfileSelectActivity : Activity() {
    private lateinit var accChip: StatusChip

    override fun attachBaseContext(base: Context) { super.attachBaseContext(LocaleHelper.wrap(base)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ErrorLog.init(this)
        setContentView(build())
        refreshAcc()
    }

    override fun onResume() {
        super.onResume()
        refreshAcc()
    }

    private fun refreshAcc() {
        if (!::accChip.isInitialized) return
        val on = BotAccessibilityService.instance != null
        accChip.show(getString(if (on) R.string.chip_acc_on else R.string.chip_acc_off), on)
    }

    /** ปิดบริการ Accessibility ของแอป (หยุดบอทก่อน) แล้วต้องเปิดใหม่เองในหน้าตั้งค่าเมื่อจะทดสอบอีก */
    private fun turnOffAccessibility() {
        val svc = BotAccessibilityService.instance
        if (svc == null) { Toast.makeText(this, R.string.toast_acc_already_off, Toast.LENGTH_LONG).show(); refreshAcc(); return }
        stopService(Intent(this, CaptureService::class.java))
        svc.turnOff()
        accChip.show(getString(R.string.chip_acc_off), false)
        Toast.makeText(this, R.string.toast_acc_off, Toast.LENGTH_LONG).show()
    }

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }

    private fun build(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.BG)
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)
            setOnApplyWindowInsetsListener { v, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val b = insets.getInsets(WindowInsets.Type.systemBars())
                    v.setPadding(b.left + pad, b.top + pad, b.right + pad, b.bottom + pad)
                } else {
                    @Suppress("DEPRECATION")
                    v.setPadding(insets.systemWindowInsetLeft + pad, insets.systemWindowInsetTop + pad, insets.systemWindowInsetRight + pad, insets.systemWindowInsetBottom + pad)
                }
                insets
            }
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(TextView(this).apply { text = getString(R.string.app_name); textSize = 22f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.TEXT) })
        titles.addView(TextView(this).apply { text = getString(R.string.select_subtitle); textSize = 13f; setTextColor(Palette.MUTED) })
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = getString(R.string.lang_switch)
            textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.TEXT); gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = roundRect(Palette.CARD, 20, Palette.MUTED)
            setOnClickListener { LocaleHelper.toggle(this@ProfileSelectActivity); recreate() }
        })
        root.addView(header, lp())

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (p in GameProfiles.all) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = roundRect(Palette.CARD, 16, Palette.OFF)
                setOnClickListener { startActivity(Intent(this@ProfileSelectActivity, MainActivity::class.java).putExtra("profile", p.id)) }
            }
            card.addView(TextView(this).apply { text = getString(p.nameRes); textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.TEXT) })
            card.addView(TextView(this).apply { text = getString(p.descRes); textSize = 13f; setTextColor(Palette.MUTED) }, lp(4))
            val tags = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            tags.addView(StatusChip(this).also { it.show("Vision: ${p.visionMethod}", true) }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
            tags.addView(StatusChip(this).also { it.show("Action: ${p.actionTypes}", false) }, LinearLayout.LayoutParams(-2, -2))
            card.addView(tags, lp(10))
            list.addView(card, lp(12))
        }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(4) })

        accChip = StatusChip(this)
        val accRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        accRow.addView(accChip, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(10) })
        accRow.addView(TextView(this).apply { text = getString(R.string.acc_hint); textSize = 12f; setTextColor(Palette.MUTED) }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(accRow, lp(8))
        root.addView(actionButton(getString(R.string.btn_disable_acc), Palette.CARD, Palette.TEXT) { turnOffAccessibility() }, lp(8))
        root.addView(TextView(this).apply {
            text = getString(R.string.cloud_status)
            textSize = 12f; setTextColor(Palette.MUTED); gravity = Gravity.CENTER
        }, lp(8))
        root.addView(TextView(this).apply {
            val info = packageManager.getPackageInfo(packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
            text = getString(R.string.version_fmt, info.versionName ?: "?", code)
            textSize = 11f; setTextColor(Palette.MUTED); gravity = Gravity.CENTER
        }, lp(4))
        return root
    }
}
