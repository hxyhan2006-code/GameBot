package com.example.universalgamebot

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** หน้าเกมของโปรไฟล์ที่เลือก: ปุ่มควบคุม, แผง Debug, ปุ่มหยุดฉุกเฉิน และ arena (เกมทดสอบ + กรอบ Vision) */
class MainActivity : Activity() {
    private lateinit var profile: GameProfile
    private lateinit var surface: GameSurface
    private lateinit var holder: FrameLayout
    private lateinit var overlay: DetectionOverlayView
    private lateinit var scoreText: TextView
    private lateinit var accChip: StatusChip
    private lateinit var botChip: StatusChip
    private lateinit var boxChip: StatusChip
    private lateinit var debugChip: StatusChip
    private lateinit var limitChip: StatusChip
    private var limit100 = false
    private lateinit var debugScroll: ScrollView
    private lateinit var debugText: TextView
    private val ui = Handler(Looper.getMainLooper())
    private val requestCodeCapture = 101
    private var pendingScore = 0

    private val debugTick = object : Runnable {
        override fun run() {
            publishArena()
            overlay.invalidate()
            if (debugScroll.visibility == View.VISIBLE) debugText.text = DebugPanel.render(this@MainActivity)
            refreshChips()
            ui.postDelayed(this, 250)
        }
    }

    override fun attachBaseContext(base: Context) { super.attachBaseContext(LocaleHelper.wrap(base)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ErrorLog.init(this)
        profile = GameProfiles.byId(intent.getStringExtra("profile"))
        OverlayState.showBoxes = profile.config.boxesVisible
        pendingScore = savedInstanceState?.getInt("score") ?: 0
        limit100 = getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("limit100", false)
        setContentView(buildScreen())
        surface.restoreScore(pendingScore)
        GroundTruth.update(surface.score, surface.mistakes)
        updateScore()
        refreshChips()
        holder.post { surface.relayout() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("score", surface.score)
    }

    override fun onStart() {
        super.onStart()
        BotState.onChange = { runOnUiThread { refreshChips() } }
        refreshChips()
    }

    override fun onResume() {
        super.onResume()
        refreshChips() // กลับมาจากหน้าตั้งค่า Accessibility
        holder.post { publishArena() }
        ui.post(debugTick)
    }

    override fun onPause() {
        ui.removeCallbacks(debugTick)
        ArenaGate.info = null // ออกจากหน้าเกม = บอทห้ามแตะ
        super.onPause()
    }

    override fun onStop() {
        BotState.onChange = null
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) stopService(Intent(this, CaptureService::class.java)) // เปลี่ยนโปรไฟล์ = หยุดบอทเดิม
        super.onDestroy()
    }

    /** ปุ่มลดเสียง = หยุดฉุกเฉิน (ใช้ได้ตอนบอทกำลังทำงานเท่านั้น) */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && BotState.running) { emergencyStop("volume down"); return true }
        return super.onKeyDown(keyCode, event)
    }

    private fun emergencyStop(source: String) {
        EmergencyStop.trigger(source)
        stopService(Intent(this, CaptureService::class.java))
        Toast.makeText(this, R.string.toast_emergency, Toast.LENGTH_SHORT).show()
    }

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }

    private fun row(vararg views: View, top: Int = 0): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEachIndexed { i, v -> addView(v, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) leftMargin = dp(8) }) }
    }.also { it.layoutParams = lp(top) }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.BG)
            val pad = dp(14)
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

        // หัวแอป: ปุ่มกลับไปเลือกโปรไฟล์ + ชื่อโปรไฟล์ + ปุ่มสลับภาษา
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = getString(R.string.btn_back)
            textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.TEXT); gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundRect(Palette.CARD, 20, Palette.MUTED)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(10) })
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(TextView(this).apply { text = getString(profile.nameRes); textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.TEXT) })
        titles.addView(TextView(this).apply { text = "${profile.visionMethod} · ${profile.actionTypes}"; textSize = 12f; setTextColor(Palette.MUTED) })
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = getString(R.string.lang_switch)
            textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.TEXT); gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundRect(Palette.CARD, 20, Palette.MUTED)
            setOnClickListener { LocaleHelper.toggle(this@MainActivity); recreate() }
        })
        root.addView(header, lp())

        // ป้ายสถานะ
        accChip = StatusChip(this); botChip = StatusChip(this); boxChip = StatusChip(this); debugChip = StatusChip(this); limitChip = StatusChip(this)
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chips.addView(accChip, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        chips.addView(botChip, LinearLayout.LayoutParams(-2, -2))
        root.addView(chips, lp(10))
        val chips2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        boxChip.setOnClickListener { OverlayState.showBoxes = !OverlayState.showBoxes; refreshChips() }
        debugChip.setOnClickListener { debugScroll.visibility = if (debugScroll.visibility == View.VISIBLE) View.GONE else View.VISIBLE; refreshChips() }
        chips2.addView(boxChip, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        chips2.addView(debugChip, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        chips2.addView(StatusChip(this).also { it.show(getString(R.string.chip_copy), false); it.setOnClickListener { copyReport() } }, LinearLayout.LayoutParams(-2, -2))
        root.addView(chips2, lp(6))
        val chips3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        limitChip.setOnClickListener {
            limit100 = !limit100
            getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("limit100", limit100).apply()
            refreshChips()
        }
        chips3.addView(limitChip, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        chips3.addView(StatusChip(this).also { it.show(getString(R.string.chip_reset_stats), false); it.setOnClickListener { StatsStore.reset(this); Toast.makeText(this, R.string.toast_stats_reset, Toast.LENGTH_SHORT).show() } }, LinearLayout.LayoutParams(-2, -2))
        root.addView(chips3, lp(6))

        // คะแนน
        scoreText = TextView(this).apply {
            textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.TEXT); gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = roundRect(Palette.CARD, 14)
        }
        root.addView(scoreText, lp(8))

        // ปุ่ม
        root.addView(row(
            actionButton(getString(R.string.btn_accessibility), Palette.CARD, Palette.TEXT) {
                Toast.makeText(this, R.string.hint_accessibility, Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            actionButton(getString(R.string.btn_start), Palette.ACCENT, android.graphics.Color.rgb(8, 20, 35)) { startCapture() }
        ), lp(8).also { })
        root.addView(row(
            actionButton(getString(R.string.btn_test_gesture), Palette.CARD, Palette.TEXT) { testGesture() },
            actionButton(getString(R.string.btn_stop), Palette.CARD, Palette.TEXT) { stopService(Intent(this, CaptureService::class.java)) }
        ), lp(6))
        root.addView(actionButton(getString(R.string.btn_emergency), Palette.DANGER, Palette.TEXT) { emergencyStop("button") }, lp(6))

        // แผง Debug
        debugText = TextView(this).apply { textSize = 10.5f; typeface = Typeface.MONOSPACE; setTextColor(Palette.MUTED); setPadding(dp(10), dp(8), dp(10), dp(8)) }
        debugScroll = ScrollView(this).apply { addView(debugText); background = roundRect(Palette.CARD, 12); visibility = if (profile.config.debugVisible) View.VISIBLE else View.GONE }
        root.addView(debugScroll, LinearLayout.LayoutParams(-1, dp(120)).apply { topMargin = dp(8) })

        // arena = เกมของโปรไฟล์ + ชั้นกรอบ Vision
        surface = profile.createSurface(this) { sc, mi -> GroundTruth.update(sc, mi); runOnUiThread { updateScore() } }
        overlay = DetectionOverlayView(this)
        holder = FrameLayout(this)
        holder.addView(surface.view, FrameLayout.LayoutParams(-1, -1))
        holder.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        root.addView(holder, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })

        root.addView(TextView(this).apply { text = versionLabel(); textSize = 11f; setTextColor(Palette.MUTED); gravity = Gravity.CENTER }, lp(6))
        return root
    }

    private fun versionLabel(): String {
        val info = packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
        return getString(R.string.version_fmt, info.versionName ?: "?", code)
    }

    private fun updateScore() {
        scoreText.text = getString(R.string.score_fmt, surface.score) + if (surface.mistakes > 0) "   " + getString(R.string.mistakes_fmt, surface.mistakes) else ""
    }

    private fun refreshChips() {
        if (!::accChip.isInitialized) return
        val acc = BotAccessibilityService.instance != null
        accChip.show(getString(if (acc) R.string.chip_acc_on else R.string.chip_acc_off), acc)
        botChip.show(getString(if (BotState.running) R.string.chip_bot_on else R.string.chip_bot_off), BotState.running)
        boxChip.show(getString(if (OverlayState.showBoxes) R.string.chip_boxes_on else R.string.chip_boxes_off), OverlayState.showBoxes)
        debugChip.show(getString(if (debugScroll.visibility == View.VISIBLE) R.string.chip_debug_on else R.string.chip_debug_off), debugScroll.visibility == View.VISIBLE)
        limitChip.show(getString(if (limit100) R.string.chip_limit_on else R.string.chip_limit_off), limit100)
    }

    /** บอกบอทว่า arena อยู่ตรงไหนบนหน้าจอจริง (พิกัดหน้าจอ) เพื่อใช้ทั้งสแกนสีและจำกัดการแตะ */
    private fun publishArena() {
        if (!::holder.isInitialized || holder.width == 0) return
        val loc = IntArray(2)
        holder.getLocationOnScreen(loc)
        val m = DisplayMetrics()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
        ArenaGate.info = ArenaInfo(Rect(loc[0], loc[1], loc[0] + holder.width, loc[1] + holder.height), m.widthPixels, m.heightPixels)
    }

    /** ปุ่มทดสอบ: ส่งคำสั่งที่ถูกต้องตามตำแหน่งจริงของเป้าผ่าน Accessibility โดยไม่ผ่าน Vision ถ้าคะแนนขึ้น = Accessibility ใช้ได้ */
    private fun testGesture() {
        val svc = BotAccessibilityService.instance
        if (svc == null) { Toast.makeText(this, R.string.toast_need_accessibility, Toast.LENGTH_LONG).show(); return }
        publishArena()
        val plan = surface.controlTestPlan()
        if (plan != null) { runControlTest(plan); return }
        val action = surface.groundTruthAction() ?: return
        val before = surface.score
        DebugMonitor.log("TEST ${action.describe()} score before=$before")
        when (action) {
            is BotAction.Tap -> svc.tap(action.x, action.y, "test")
            is BotAction.Swipe -> svc.swipe(action.x1, action.y1, action.x2, action.y2, action.durationMs, "test")
            else -> {}
        }
        ui.postDelayed({ DebugMonitor.log("TEST result: score now=${surface.score} (before $before), gesture=${DebugMonitor.gestureState}") }, 900)
    }

    private var controlTest: ControlTestRunner? = null

    /** ทดสอบการควบคุมต่อเนื่อง (จอย + ปุ่มพร้อมกัน) ผ่าน Accessibility โดยไม่ผ่าน Vision แล้วเทียบกับตัวเลขที่เกมได้รับจริง */
    private fun runControlTest(plan: List<ControlStep>) {
        if (controlTest?.isRunning == true) return
        EmergencyStop.reset()
        ControlInputStats.reset()
        ControlGestureStats.reset()
        DebugMonitor.log("TEST control: stick right -> up (no lift) -> shoot with stick -> pass with stick -> release")
        val runner = ControlTestRunner(ContinuousGestureExecutor(packageName) { ui.post(it) }, ui) { msg ->
            DebugMonitor.log("TEST control $msg: ${ControlInputStats.line()} | ${ControlGestureStats.line()}")
        }
        controlTest = runner
        runner.start(plan)
    }

    private fun copyReport() {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("GameBot debug report", DebugPanel.fullReport(this)))
        Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show()
    }

    private fun startCapture() {
        if (BotAccessibilityService.instance == null) {
            Toast.makeText(this, R.string.toast_need_accessibility, Toast.LENGTH_LONG).show()
            return
        }
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), requestCodeCapture)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestCodeCapture && resultCode == RESULT_OK && data != null) {
            GroundTruth.update(surface.score, surface.mistakes)
            GroundTruth.begin()
            ControlInputStats.reset()
            val intent = Intent(this, CaptureService::class.java).apply { putExtra("code", resultCode); putExtra("data", data); putExtra("profile", profile.id); putExtra("limit", if (limit100) 100 else 0) }
            startForegroundService(intent)
        }
    }
}
