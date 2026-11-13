package com.example.universalgamebot

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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

/**
 * หน้าเกมของโปรไฟล์ที่เลือก (v0.6): arena ใหญ่ + ปุ่มหลักอันเดียว (เริ่ม) · ตอนบอททำงานเปลี่ยนเป็นปุ่มหยุดฉุกเฉินใหญ่ + สถิติสั้น ๆ
 * Debug / รายงาน / ทดสอบ Gesture / กรอบตรวจจับ อยู่ในแถวไอคอน (แสดงเฉพาะตอนบอทหยุด) · ขนาด arena คงที่ทุกสถานะ เพราะบอทอ่านตำแหน่งจากภาพ
 */
class MainActivity : Activity() {
    private lateinit var profile: GameProfile
    private lateinit var surface: GameSurface
    private lateinit var holder: FrameLayout
    private lateinit var overlay: DetectionOverlayView
    private lateinit var scoreText: TextView
    private lateinit var infoRight: TextView
    private lateinit var pill: LinearLayout
    private lateinit var pillDot: View
    private lateinit var pillText: TextView
    private lateinit var idleGroup: LinearLayout
    private lateinit var runGroup: LinearLayout
    private lateinit var boxesTool: Pair<IconView, TextView>
    private lateinit var statFps: TextView
    private lateinit var statGesture: TextView
    private lateinit var statVerified: TextView
    private lateinit var accSheet: AccessibilitySheet
    private var debugSheet: BottomSheet? = null
    private var debugText: TextView? = null
    private var runStartedAt = 0L
    private var lastStatsAt = 0L
    private val ui = Handler(Looper.getMainLooper())
    private val requestCodeCapture = 101
    private var pendingScore = 0

    private val debugTick = object : Runnable {
        override fun run() {
            publishArena()
            overlay.invalidate()
            if (debugSheet?.isShowing == true) debugText?.text = DebugPanel.render(this@MainActivity)
            if (surface.scoreLine != null) updateScore() // นาฬิกาแมตช์เดินต่อเนื่อง
            refreshUi()
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
        accSheet = AccessibilitySheet(this) { refreshUi() }
        setContentView(buildScreen())
        surface.restoreScore(pendingScore)
        GroundTruth.update(surface.score, surface.mistakes)
        updateScore()
        refreshUi()
        holder.post { surface.relayout() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("score", surface.score)
    }

    override fun onStart() {
        super.onStart()
        BotState.onChange = { runOnUiThread { refreshUi() } }
        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        refreshUi() // กลับมาจากหน้าตั้งค่า Accessibility
        holder.post { publishArena() }
        ui.post(debugTick)
    }

    override fun onPause() {
        ui.removeCallbacks(debugTick)
        accSheet.dismiss()
        debugSheet?.dismiss()
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

    /** ปุ่มเครื่องมือในแถวไอคอน: ไอคอน + ชื่อ สูง >= 56dp (ใช้สีไอคอนบอกสถานะเปิด/ปิดได้) */
    private fun toolButton(icon: Icon, label: String, onClick: () -> Unit): Pair<LinearLayout, Pair<IconView, TextView>> {
        val iv = IconView(this, icon)
        val tv = TextView(this).apply { text = label; textSize = 11f; setTextColor(Palette.MUTED); gravity = Gravity.CENTER }
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; minimumHeight = dp(56)
            setOnClickListener { onClick() }
        }
        item.addView(iv, LinearLayout.LayoutParams(dp(22), dp(22)))
        item.addView(tv, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })
        return item to (iv to tv)
    }

    private fun statBox(label: String): Pair<LinearLayout, TextView> {
        val value = TextView(this).apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.TEXT) }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(6), dp(10), dp(6)); background = roundRect(Palette.CARD, 12, Palette.CARD_LINE) }
        box.addView(TextView(this).apply { text = label; textSize = 11f; setTextColor(Palette.DIM) })
        box.addView(value)
        return box to value
    }

    private fun buildScreen(): View {
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

        // หัวแอป: ปุ่มกลับ + ชื่อโปรไฟล์ + ป้ายสถานะ (แตะเพื่อเปิดแผง Accessibility)
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(FrameLayout(this).apply {
            setOnClickListener { finish() }
            addView(IconView(this@MainActivity, Icon.BACK, Palette.TEXT), FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = -dp(12) })
        header.addView(TextView(this).apply { text = getString(profile.nameRes); textSize = 18f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.TEXT) }, LinearLayout.LayoutParams(0, -2, 1f))
        pillDot = View(this)
        pillText = TextView(this).apply { textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.TEXT) }
        pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { if (!BotState.running) accSheet.show() }
        }
        pill.addView(pillDot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { rightMargin = dp(8) })
        pill.addView(pillText)
        header.addView(pill, LinearLayout.LayoutParams(-2, dp(36)))
        root.addView(header, lp())

        // คะแนนจริงของเกม (ซ้าย) + เป้าหมาย/ประตูติดกัน (ขวา)
        scoreText = TextView(this).apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.TEXT) }
        infoRight = TextView(this).apply { textSize = 13f; setTextColor(Palette.MUTED); gravity = Gravity.END }
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = roundRect(Palette.CARD, 14, Palette.CARD_LINE)
        }
        info.addView(scoreText, LinearLayout.LayoutParams(-2, -2))
        info.addView(infoRight, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
        root.addView(info, lp(8))

        // arena = เกมของโปรไฟล์ + ชั้นกรอบ Vision (ขนาดคงที่ทุกสถานะ)
        surface = profile.createSurface(this) { sc, mi -> GroundTruth.update(sc, mi); runOnUiThread { updateScore() } }
        overlay = DetectionOverlayView(this)
        holder = FrameLayout(this)
        holder.addView(surface.view, FrameLayout.LayoutParams(-1, -1))
        holder.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        root.addView(holder, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(10) })

        // โซนควบคุมด้านล่าง: ความสูงคงที่ สลับระหว่าง "ก่อนเริ่ม" กับ "บอททำงาน"
        idleGroup = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        idleGroup.addView(actionButton(getString(R.string.btn_start_bot), Palette.ACCENT, Palette.ON_ACCENT) { debugSheet?.dismiss(); startCapture() }.also { it.minimumHeight = dp(60); it.minHeight = dp(60); it.textSize = 18f }, lp())
        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tTest = toolButton(Icon.TAP, getString(R.string.tool_test_gesture)) { testGesture() }
        val tBoxes = toolButton(Icon.BOXES, getString(R.string.tool_boxes)) { OverlayState.showBoxes = !OverlayState.showBoxes; refreshUi() }
        val tDebug = toolButton(Icon.CODE, "Debug") { showDebug() }
        val tReport = toolButton(Icon.BARS, getString(R.string.tool_report)) { copyReport() }
        boxesTool = tBoxes.second
        val tools5 = if (surface.manualPlay) listOf(tTest, tBoxes, tDebug, tReport, toolButton(Icon.PAD, getString(R.string.tool_play_manual)) { debugSheet?.dismiss(); surface.restart() }) else listOf(tTest, tBoxes, tDebug, tReport)
        for (t in tools5) tools.addView(t.first, LinearLayout.LayoutParams(0, -2, 1f))
        idleGroup.addView(tools, lp(6))

        runGroup = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val s1 = statBox("FPS"); val s2 = statBox(getString(R.string.stat_gesture)); val s3 = statBox(getString(R.string.stat_verified))
        statFps = s1.second; statGesture = s2.second; statVerified = s3.second
        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((i, b) in listOf(s1, s2, s3).withIndex()) stats.addView(b.first, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) leftMargin = dp(8) })
        runGroup.addView(stats, lp())
        runGroup.addView(actionButton(getString(R.string.btn_emergency), Palette.DANGER, Palette.TEXT) { emergencyStop("button") }.also { it.minimumHeight = dp(64); it.minHeight = dp(64); it.textSize = 20f }, lp(8))
        runGroup.addView(actionButton(getString(R.string.btn_stop), Palette.CARD, Palette.TEXT) { stopService(Intent(this, CaptureService::class.java)) }.also { it.minimumHeight = dp(48); it.minHeight = dp(48); it.textSize = 15f; it.background = roundRect(Palette.CARD, 14, Palette.OFF) }, lp(8))

        val bottom = FrameLayout(this)
        bottom.addView(idleGroup, FrameLayout.LayoutParams(-1, -2))
        bottom.addView(runGroup, FrameLayout.LayoutParams(-1, -2))
        root.addView(bottom, LinearLayout.LayoutParams(-1, dp(190)).apply { topMargin = dp(10) })
        return root
    }

    /** แผง Debug ตัวเต็ม (เปิดได้เฉพาะตอนบอทหยุด เพราะแผงบังบางส่วนของ arena) */
    private fun showDebug() {
        val sheet = debugSheet ?: BottomSheet(this).also { debugSheet = it }
        sheet.reset("Debug")
        val t = TextView(this).apply { textSize = 10.5f; typeface = Typeface.MONOSPACE; setTextColor(Palette.MUTED); setPadding(dp(10), dp(8), dp(10), dp(8)); text = DebugPanel.render(this@MainActivity) }
        debugText = t
        sheet.content.addView(ScrollView(this).apply { addView(t); background = roundRect(Palette.CARD, 12) }, LinearLayout.LayoutParams(-1, dp(420)).apply { topMargin = dp(8) })
        sheet.content.addView(actionButton(getString(R.string.chip_copy), Palette.ACCENT, Palette.ON_ACCENT) { copyReport() }, lp(10))
        sheet.show()
    }

    private fun versionLabel(): String {
        val info = packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
        return getString(R.string.version_fmt, info.versionName ?: "?", code)
    }

    private fun updateScore() {
        scoreText.text = surface.scoreLine ?: (getString(R.string.score_fmt, surface.score) + if (surface.mistakes > 0) "   " + getString(R.string.mistakes_fmt, surface.mistakes) else "")
    }

    private fun limitOn() = getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("limit100", false)

    /** อัปเดตทุกอย่างที่ขึ้นกับสถานะ: ป้ายหัว, สลับโซนปุ่ม (ก่อนเริ่ม/ทำงาน), สถิติสั้น ๆ, ข้อความขวาของการ์ดคะแนน */
    private fun refreshUi() {
        if (!::pill.isInitialized) return
        val running = BotState.running
        val acc = BotAccessibilityService.instance != null
        if (running && runStartedAt == 0L) runStartedAt = SystemClock.elapsedRealtime()
        if (!running) runStartedAt = 0L

        val (tint, accent, label) = when {
            running -> {
                val sec = ((SystemClock.elapsedRealtime() - runStartedAt) / 1000).toInt()
                // บอททำงานแต่ยังไม่เคยได้ภาพ = บอกตรง ๆ ว่ารอภาพ (ห้ามขึ้นว่าทำงานปกติ)
                if (DebugMonitor.framesReceived.get() == 0L && sec >= 2) Triple(Palette.DANGER_TINT, Palette.DANGER, getString(R.string.pill_waiting_frames))
                else Triple(Palette.ACCENT_TINT, Palette.ACCENT, getString(R.string.pill_running_fmt, "%02d:%02d".format(sec / 60, sec % 60)))
            }
            DebugMonitor.captureState == "Error" -> Triple(Palette.DANGER_TINT, Palette.DANGER, getString(R.string.pill_capture_error))
            acc -> Triple(Palette.OK_TINT, Palette.OK, getString(R.string.pill_game_ready))
            else -> Triple(Palette.DANGER_TINT, Palette.DANGER, getString(R.string.pill_not_ready))
        }
        pill.background = roundRect(tint, 18, accent)
        pillDot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(accent) }
        pillText.text = label

        idleGroup.visibility = if (running) View.GONE else View.VISIBLE
        runGroup.visibility = if (running) View.VISIBLE else View.GONE
        val boxColor = if (OverlayState.showBoxes) Palette.ACCENT else Palette.MUTED
        boxesTool.first.setTint(boxColor); boxesTool.second.setTextColor(boxColor)

        val goal = profile.config.streakGoal
        infoRight.text = when {
            goal > 0 && running -> getString(R.string.streak_fmt, DebugMonitor.engine?.streak ?: 0, goal)
            goal > 0 -> getString(R.string.goal_streak_fmt, goal)
            limitOn() && !running -> getString(R.string.limit_hint)
            else -> ""
        }

        val now = SystemClock.elapsedRealtime()
        if (running && now - lastStatsAt >= 1000) {
            lastStatsAt = now
            val d = DebugMonitor
            val sent = d.gesturesSent.get()
            statFps.text = "%.0f".format(PerfMonitor.sample(this).fps)
            statGesture.text = if (sent == 0L) "-" else "${d.gesturesOk.get() * 100 / sent}%"
            val st = d.engine?.stats
            statVerified.text = "${st?.verified ?: 0} / ${st?.attempts ?: 0}"
        }
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
            val intent = Intent(this, CaptureService::class.java).apply { putExtra("code", resultCode); putExtra("data", data); putExtra("profile", profile.id); putExtra("limit", if (limitOn()) 100 else 0) }
            startForegroundService(intent)
            surface.onBotStarted()
        }
    }
}
