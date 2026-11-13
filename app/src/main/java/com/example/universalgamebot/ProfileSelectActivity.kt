package com.example.universalgamebot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** หน้าแรก: เลือกโปรไฟล์เกม (แต่ละโปรไฟล์ = เกมทดสอบ + วิธีตรวจจับ + วิธีตัดสินใจ ของตัวเอง) */
class ProfileSelectActivity : Activity() {
    private lateinit var accBar: LinearLayout
    private lateinit var accDot: View
    private lateinit var accTitle: TextView
    private lateinit var accSub: TextView
    private lateinit var accArrow: TextView
    private lateinit var accPanel: LinearLayout
    private lateinit var accOpenBtn: Button
    private lateinit var accOffBtn: Button
    private val green = Color.rgb(34, 197, 94)

    override fun attachBaseContext(base: Context) { super.attachBaseContext(LocaleHelper.wrap(base)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ErrorLog.init(this)
        setContentView(build())
        refreshAcc()
    }

    override fun onResume() {
        super.onResume()
        refreshAcc() // กลับมาจากหน้าตั้งค่า Accessibility
    }

    /** แถบสถานะด้านบน: เปิด = เขียว, ปิด = แดง (ต้องเปิดก่อนบอทถึงจะแตะได้) · ปุ่มที่ไม่เกี่ยวกับสถานะตอนนี้จางลง */
    private fun refreshAcc() {
        if (!::accBar.isInitialized) return
        val on = BotAccessibilityService.instance != null
        val accent = if (on) green else Palette.DANGER
        accBar.background = roundRect(if (on) Color.rgb(16, 48, 34) else Color.rgb(58, 24, 30), 14, accent)
        accDot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(accent) }
        accTitle.text = getString(if (on) R.string.acc_bar_on else R.string.acc_bar_off)
        accSub.text = getString(R.string.acc_bar_tap)
        accArrow.text = if (accPanel.visibility == View.VISIBLE) "\u25B2" else "\u25BC"
        accOpenBtn.alpha = if (on) 0.45f else 1f
        accOffBtn.alpha = if (on) 1f else 0.45f
    }

    private fun toggleAccPanel() {
        accPanel.visibility = if (accPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        refreshAcc()
    }

    /** ไปหน้าตั้งค่า Accessibility (ระบบ Android ไม่ให้แอปเปิดบริการเอง ต้องแตะเปิดเองหนึ่งครั้ง) */
    private fun openAccessibilitySettings() {
        Toast.makeText(this, R.string.hint_accessibility, Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /** ปิดบริการ Accessibility ของแอป (หยุดบอทก่อน) แล้วต้องเปิดใหม่เองในหน้าตั้งค่าเมื่อจะทดสอบอีก */
    private fun turnOffAccessibility() {
        val svc = BotAccessibilityService.instance
        if (svc == null) { Toast.makeText(this, R.string.toast_acc_already_off, Toast.LENGTH_LONG).show(); refreshAcc(); return }
        stopService(Intent(this, CaptureService::class.java))
        svc.turnOff()
        Toast.makeText(this, R.string.toast_acc_off, Toast.LENGTH_LONG).show()
        refreshAcc()
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

        // แถบ Accessibility ด้านบน: สีบอกสถานะ แตะแล้วขยายปุ่มเปิด/ปิด
        accDot = View(this)
        accTitle = TextView(this).apply { textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.TEXT) }
        accSub = TextView(this).apply { textSize = 11f; setTextColor(Palette.MUTED) }
        accArrow = TextView(this).apply { textSize = 12f; setTextColor(Palette.MUTED); setPadding(dp(8), 0, 0, 0) }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(accTitle); texts.addView(accSub)
        accBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener { toggleAccPanel() }
        }
        accBar.addView(accDot, LinearLayout.LayoutParams(dp(12), dp(12)).apply { rightMargin = dp(12) })
        accBar.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        accBar.addView(accArrow)
        root.addView(accBar, lp(10))

        accPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundRect(Palette.CARD, 14, Palette.OFF)
            visibility = View.GONE
        }
        accPanel.addView(TextView(this).apply { text = getString(R.string.acc_panel_hint); textSize = 12f; setTextColor(Palette.MUTED) })
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        accOpenBtn = actionButton(getString(R.string.btn_open_acc), Palette.ACCENT, Color.rgb(8, 20, 35)) { openAccessibilitySettings() }
        accOffBtn = actionButton(getString(R.string.btn_turn_off_short), Palette.DANGER, Palette.TEXT) { turnOffAccessibility() }
        btnRow.addView(accOpenBtn, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(8) })
        btnRow.addView(accOffBtn, LinearLayout.LayoutParams(0, -2, 1f))
        accPanel.addView(btnRow, lp(10))
        root.addView(accPanel, lp(6))

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
