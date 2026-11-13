package com.example.universalgamebot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.random.Random

class MainActivity : Activity() {
    private lateinit var arena: FrameLayout
    private lateinit var target: View
    private lateinit var scoreText: TextView
    private lateinit var accChip: StatusChip
    private lateinit var botChip: StatusChip
    private var score = 0
    private val requestCodeCapture = 101

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        score = savedInstanceState?.getInt("score") ?: 0
        setContentView(buildScreen())
        refreshChips()
        arena.post { moveTarget() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("score", score)
    }

    override fun onStart() {
        super.onStart()
        BotState.onChange = { runOnUiThread { refreshChips() } }
        refreshChips()
    }

    override fun onResume() {
        super.onResume()
        refreshChips() // กลับมาจากหน้าตั้งค่า Accessibility
    }

    override fun onStop() {
        BotState.onChange = null
        super.onStop()
    }

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }

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

        // หัวแอป + ปุ่มสลับภาษา
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(TextView(this).apply { text = getString(R.string.app_name); textSize = 22f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.TEXT) })
        titles.addView(TextView(this).apply { text = getString(R.string.subtitle); textSize = 13f; setTextColor(Palette.MUTED) })
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = getString(R.string.lang_switch)
            textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.TEXT); gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = roundRect(Palette.CARD, 20, Palette.MUTED)
            setOnClickListener { LocaleHelper.toggle(this@MainActivity); recreate() }
        })
        root.addView(header, lp())

        // ป้ายสถานะ
        accChip = StatusChip(this)
        botChip = StatusChip(this)
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chips.addView(accChip, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
        chips.addView(botChip, LinearLayout.LayoutParams(-2, -2))
        root.addView(chips, lp(12))

        // คะแนน
        scoreText = TextView(this).apply {
            textSize = 24f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Palette.TEXT); gravity = Gravity.CENTER
            setPadding(dp(8), dp(14), dp(8), dp(14))
            background = roundRect(Palette.CARD, 14)
        }
        updateScore()
        root.addView(scoreText, lp(12))

        // ปุ่ม
        root.addView(actionButton(getString(R.string.btn_accessibility), Palette.CARD, Palette.TEXT) {
            Toast.makeText(this, R.string.hint_accessibility, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, lp(12))
        root.addView(actionButton(getString(R.string.btn_start), Palette.ACCENT, android.graphics.Color.rgb(8, 20, 35)) { startCapture() }, lp(8))
        root.addView(actionButton(getString(R.string.btn_stop), Palette.DANGER, Palette.TEXT) {
            stopService(Intent(this, CaptureService::class.java))
        }, lp(8))

        // พื้นที่เกม
        arena = FrameLayout(this).apply {
            background = roundRect(Palette.ARENA, 18)
            clipToOutline = true
        }
        arena.addView(TextView(this).apply { text = getString(R.string.arena_hint); textSize = 13f; setTextColor(Palette.MUTED); setPadding(dp(12), dp(10), dp(12), dp(10)) })
        target = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Palette.TARGET) }
            setOnClickListener { score++; updateScore(); moveTarget() }
        }
        arena.addView(target, FrameLayout.LayoutParams(dp(80), dp(80)))
        root.addView(arena, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })

        // เวอร์ชัน
        root.addView(TextView(this).apply { text = versionLabel(); textSize = 12f; setTextColor(Palette.MUTED); gravity = Gravity.CENTER }, lp(8))
        return root
    }

    private fun versionLabel(): String {
        val info = packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
        return getString(R.string.version_fmt, info.versionName ?: "?", code)
    }

    private fun updateScore() {
        scoreText.text = getString(R.string.score_fmt, score)
    }

    private fun refreshChips() {
        if (!::accChip.isInitialized) return
        val acc = BotAccessibilityService.instance != null
        accChip.show(getString(if (acc) R.string.chip_acc_on else R.string.chip_acc_off), acc)
        botChip.show(getString(if (BotState.running) R.string.chip_bot_on else R.string.chip_bot_off), BotState.running)
    }

    private fun startCapture() {
        if (BotAccessibilityService.instance == null) {
            Toast.makeText(this, R.string.toast_need_accessibility, Toast.LENGTH_LONG).show()
            return
        }
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), requestCodeCapture)
    }

    private fun moveTarget() {
        val maxX = (arena.width - target.width).coerceAtLeast(1)
        val maxY = (arena.height - target.height).coerceAtLeast(1)
        target.x = Random.nextInt(maxX).toFloat()
        target.y = Random.nextInt(maxY).toFloat()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestCodeCapture && resultCode == RESULT_OK && data != null) {
            val intent = Intent(this, CaptureService::class.java).apply { putExtra("code", resultCode); putExtra("data", data) }
            startForegroundService(intent)
        }
    }
}
