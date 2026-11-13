package com.example.universalgamebot

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * หน้าแรก (v0.6): หัวแอป + ป้ายสถานะบอท (แตะแล้วเปิดแผง Accessibility) + แถบเมนูล่าง 4 ปุ่ม
 * โปรไฟล์ = เลือกเกมทดสอบ · การเข้าถึง = เปิดแผง Accessibility · รายงาน = ข้อมูล Debug · ตั้งค่า = ภาษา/หยุดอัตโนมัติ/ล้างสถิติ
 */
class ProfileSelectActivity : Activity() {
    private enum class Tab { PROFILES, REPORT, SETTINGS }

    private lateinit var pill: LinearLayout
    private lateinit var pillDot: View
    private lateinit var pillText: TextView
    private lateinit var content: FrameLayout
    private lateinit var navWrap: LinearLayout
    private val navItems = HashMap<Int, Pair<IconView, TextView>>()
    private lateinit var accSheet: AccessibilitySheet
    private var tab = Tab.PROFILES
    private var resetArmed = false
    private var reportText: TextView? = null
    private val ui = Handler(Looper.getMainLooper())

    private val reportTick = object : Runnable {
        override fun run() {
            reportText?.text = DebugPanel.render(this@ProfileSelectActivity)
            if (reportText != null) ui.postDelayed(this, 1000)
        }
    }

    override fun attachBaseContext(base: Context) { super.attachBaseContext(LocaleHelper.wrap(base)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ErrorLog.init(this)
        accSheet = AccessibilitySheet(this) { refreshPill() }
        setContentView(build())
        showTab(Tab.PROFILES)
        refreshPill()
    }

    override fun onResume() {
        super.onResume()
        refreshPill() // กลับมาจากหน้าตั้งค่า Accessibility
        showTab(tab)  // อัปเดตผลทดสอบล่าสุดของแต่ละโปรไฟล์
    }

    override fun onPause() {
        ui.removeCallbacks(reportTick)
        accSheet.dismiss()
        super.onPause()
    }

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }

    // ---------- โครงหน้า ----------

    private fun build(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.BG)
            setOnApplyWindowInsetsListener { v, insets ->
                val l: Int; val t: Int; val r: Int; val b: Int
                if (Build.VERSION.SDK_INT >= 30) {
                    val i = insets.getInsets(WindowInsets.Type.systemBars()); l = i.left; t = i.top; r = i.right; b = i.bottom
                } else {
                    @Suppress("DEPRECATION") run { l = insets.systemWindowInsetLeft; t = insets.systemWindowInsetTop; r = insets.systemWindowInsetRight; b = insets.systemWindowInsetBottom }
                }
                v.setPadding(l, t, r, 0)
                navWrap.setPadding(dp(8), 0, dp(8), b)
                insets
            }
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(8)) }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(TextView(this).apply { text = "Game Bot"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.TEXT) })
        titles.addView(TextView(this).apply { text = getString(R.string.select_subtitle); textSize = 13f; setTextColor(Palette.MUTED) })
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        pillDot = View(this)
        pillText = TextView(this).apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.TEXT) }
        pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0); minimumHeight = dp(40)
            setOnClickListener { accSheet.show() }
        }
        pill.addView(pillDot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { rightMargin = dp(8) })
        pill.addView(pillText)
        header.addView(pill, LinearLayout.LayoutParams(-2, dp(40)).apply { rightMargin = dp(8) })
        header.addView(TextView(this).apply {
            text = getString(R.string.lang_switch)
            textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.TEXT); gravity = Gravity.CENTER
            background = roundRect(Palette.CARD, 20, Palette.OFF)
            setOnClickListener { LocaleHelper.toggle(this@ProfileSelectActivity); recreate() }
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        root.addView(header, lp())

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        navWrap = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), 0, dp(8), 0) }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        nav.addView(View(this).apply { setBackgroundColor(Palette.CARD) }, LinearLayout.LayoutParams(-1, dp(1)))
        root.addView(nav, lp())
        addNav(R.string.nav_profiles, Icon.GRID) { showTab(Tab.PROFILES) }.also { navItems[0] = it }
        addNav(R.string.nav_access, Icon.SHIELD) { accSheet.show() }.also { navItems[1] = it }
        addNav(R.string.nav_report, Icon.BARS) { showTab(Tab.REPORT) }.also { navItems[2] = it }
        addNav(R.string.nav_settings, Icon.SLIDERS) { showTab(Tab.SETTINGS) }.also { navItems[3] = it }
        nav.addView(navWrap, LinearLayout.LayoutParams(-1, -2))
        return root
    }

    private fun addNav(label: Int, icon: Icon, onClick: () -> Unit): Pair<IconView, TextView> {
        val iv = IconView(this, icon)
        val tv = TextView(this).apply { text = getString(label); textSize = 11f; setTextColor(Palette.MUTED); gravity = Gravity.CENTER }
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            minimumHeight = dp(64)
            setOnClickListener { onClick() }
        }
        item.addView(iv, LinearLayout.LayoutParams(dp(22), dp(22)))
        item.addView(tv, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })
        navWrap.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
        return iv to tv
    }

    private fun showTab(t: Tab) {
        tab = t
        ui.removeCallbacks(reportTick)
        reportText = null
        resetArmed = false
        content.removeAllViews()
        content.addView(when (t) { Tab.PROFILES -> profilesPage(); Tab.REPORT -> reportPage(); Tab.SETTINGS -> settingsPage() })
        val selected = when (t) { Tab.PROFILES -> 0; Tab.REPORT -> 2; Tab.SETTINGS -> 3 }
        for ((i, p) in navItems) { val c = if (i == selected) Palette.ACCENT else Palette.MUTED; p.first.setTint(c); p.second.setTextColor(c) }
        if (reportText != null) ui.post(reportTick)
    }

    /** ป้ายสถานะ: เขียว "บอทพร้อม" เมื่อ Accessibility เปิด · แดง "ยังไม่พร้อม" เมื่อปิด (มีตัวอักษรกำกับ ไม่ใช้สีอย่างเดียว) */
    private fun refreshPill() {
        if (!::pill.isInitialized) return
        val on = BotAccessibilityService.instance != null
        val accent = if (on) Palette.OK else Palette.DANGER
        pill.background = roundRect(if (on) Palette.OK_TINT else Palette.DANGER_TINT, 20, accent)
        pillDot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(accent) }
        pillText.text = getString(if (on) R.string.pill_ready else R.string.pill_not_ready)
    }

    // ---------- หน้าโปรไฟล์ ----------

    private fun profileIcon(id: String) = when (id) {
        "green_dot" -> Icon.DOT
        "bullseye" -> Icon.RING
        "swipe_ball" -> Icon.SWIPE
        "football" -> Icon.PAD
        else -> Icon.GRID
    }

    /** ผลทดสอบสะสมของโปรไฟล์ (จาก StatsStore) · ผ่าน = ตามเป้าเดียวกับแผง Debug: streak ครบเป้า หรือ verified >= 95% จาก >= 100 action */
    private fun resultOf(p: GameProfile): Pair<String, Boolean> {
        val s = StatsStore.load(this, p.id)
        val goal = p.config.streakGoal
        return when {
            goal > 0 && s.bestStreak >= goal -> getString(R.string.result_pass_streak_fmt, s.bestStreak) to true
            goal > 0 && s.bestStreak > 0 -> getString(R.string.result_streak_fmt, s.bestStreak) to false
            goal == 0 && s.attempts >= 100 && s.successRate() >= 0.95f -> getString(R.string.result_pass_verified_fmt, s.verified, s.attempts) to true
            goal == 0 && s.attempts > 0 -> getString(R.string.result_verified_fmt, s.verified, s.attempts) to false
            else -> getString(R.string.result_none) to false
        }
    }

    private fun profilesPage(): View {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val label = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(16), dp(12), dp(16), dp(8)) }
        label.addView(TextView(this).apply { text = getString(R.string.section_profiles); textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.MUTED); letterSpacing = 0.08f }, LinearLayout.LayoutParams(0, -2, 1f))
        label.addView(TextView(this).apply { text = getString(R.string.profiles_count_fmt, GameProfiles.all.size); textSize = 12f; setTextColor(Palette.MUTED) })
        page.addView(label, lp())

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(16)) }
        for (p in GameProfiles.all) {
            val (result, pass) = resultOf(p)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = roundRect(Palette.CARD, 16, Palette.CARD_LINE)
                setOnClickListener { startActivity(Intent(this@ProfileSelectActivity, MainActivity::class.java).putExtra("profile", p.id)) }
            }
            val tile = FrameLayout(this).apply { background = roundRect(Palette.ACCENT_TINT, 12) }
            tile.addView(IconView(this, profileIcon(p.id), Palette.ACCENT), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            card.addView(tile, LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(14) })
            val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(TextView(this).apply { text = getString(p.nameRes); textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.TEXT) })
            texts.addView(TextView(this).apply { text = getString(p.descRes); textSize = 13f; setTextColor(Palette.MUTED) }, lp(2))
            texts.addView(TextView(this).apply { text = "${p.visionMethod} · ${p.actionTypes}"; textSize = 11f; setTextColor(Palette.DIM) }, lp(6))
            texts.addView(TextView(this).apply { text = result; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(if (pass) Palette.OK else Palette.MUTED) }, lp(2))
            card.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(IconView(this, Icon.CHEVRON, Palette.DIM), LinearLayout.LayoutParams(dp(18), dp(18)).apply { leftMargin = dp(8) })
            list.addView(card, lp(if (p === GameProfiles.all.first()) 0 else 10))
        }
        page.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        return page
    }

    // ---------- หน้ารายงาน ----------

    private fun reportPage(): View {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(12)) }
        val text = TextView(this).apply { textSize = 10.5f; typeface = Typeface.MONOSPACE; setTextColor(Palette.MUTED); setPadding(dp(12), dp(10), dp(12), dp(10)) }
        reportText = text
        page.addView(ScrollView(this).apply { addView(text); background = roundRect(Palette.CARD, 14) }, LinearLayout.LayoutParams(-1, 0, 1f))
        page.addView(actionButton(getString(R.string.chip_copy), Palette.ACCENT, Palette.ON_ACCENT) { copyReport() }, lp(10))
        return page
    }

    private fun copyReport() {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("GameBot debug report", DebugPanel.fullReport(this)))
        Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show()
    }

    // ---------- หน้าตั้งค่า ----------

    private fun settingRow(title: String, sub: String?, value: String?, subColor: Int = Palette.MUTED, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64); setPadding(dp(16), dp(10), dp(16), dp(10))
            background = roundRect(Palette.CARD, 16, Palette.CARD_LINE)
            setOnClickListener { onClick() }
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply { text = title; textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.TEXT) })
        if (sub != null) texts.addView(TextView(this).apply { text = sub; textSize = 12f; setTextColor(subColor) }, lp(2))
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        if (value != null) row.addView(TextView(this).apply { text = value; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.ACCENT) })
        return row
    }

    private fun settingsPage(): View {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val limitOn = prefs.getBoolean("limit100", false)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(16)) }
        col.addView(settingRow(getString(R.string.settings_language), null, getString(R.string.lang_switch)) { LocaleHelper.toggle(this); recreate() }, lp())
        col.addView(settingRow(getString(R.string.settings_limit), getString(R.string.settings_limit_sub), getString(if (limitOn) R.string.settings_on else R.string.settings_off)) {
            prefs.edit().putBoolean("limit100", !limitOn).apply(); showTab(Tab.SETTINGS)
        }, lp(10))
        col.addView(settingRow(getString(R.string.chip_reset_stats), getString(if (resetArmed) R.string.settings_reset_confirm else R.string.settings_reset_sub), null, if (resetArmed) Palette.DANGER else Palette.MUTED) {
            if (!resetArmed) { resetArmed = true; refreshSettings() }
            else { StatsStore.reset(this); resetArmed = false; Toast.makeText(this, R.string.toast_stats_reset, Toast.LENGTH_SHORT).show(); refreshSettings() }
        }, lp(10))
        col.addView(TextView(this).apply { text = getString(R.string.cloud_status); textSize = 12f; setTextColor(Palette.DIM); gravity = Gravity.CENTER }, lp(24))
        col.addView(TextView(this).apply {
            val info = packageManager.getPackageInfo(packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
            text = getString(R.string.version_fmt, info.versionName ?: "?", code)
            textSize = 11f; setTextColor(Palette.DIM); gravity = Gravity.CENTER
        }, lp(4))
        return ScrollView(this).apply { addView(col) }
    }

    /** วาดหน้าตั้งค่าใหม่โดยไม่รีเซ็ตสถานะ "แตะอีกครั้งเพื่อยืนยัน" */
    private fun refreshSettings() {
        content.removeAllViews()
        content.addView(settingsPage())
    }
}
