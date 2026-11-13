package com.example.universalgamebot

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import kotlin.random.Random

class MainActivity : Activity() {
    private lateinit var arena: FrameLayout
    private lateinit var target: TextView
    private lateinit var status: TextView
    private var score = 0
    private val requestCodeCapture = 101
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(19, 24, 37)) }
        fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; setOnClickListener { action() }; root.addView(this) }
        status = TextView(this).apply { text = "Score: 0 | Green target = tap"; textSize = 18f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(8, 20, 8, 20) }
        root.addView(status)
        button("1. Enable Accessibility") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        button("2. Start screen capture + bot") {
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mgr.createScreenCaptureIntent(), requestCodeCapture)
        }
        button("STOP BOT") { stopService(Intent(this, CaptureService::class.java)) }
        arena = FrameLayout(this).apply { setBackgroundColor(Color.rgb(35, 42, 57)) }
        root.addView(arena, LinearLayout.LayoutParams(-1, 0, 1f))
        target = TextView(this).apply {
            text = "●"; textSize = 66f; gravity = Gravity.CENTER; setTextColor(Color.rgb(0, 255, 0)); typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { score++; status.text = "Score: $score | Bot active when capture started"; moveTarget() }
        }
        arena.addView(target, FrameLayout.LayoutParams(110.dp, 110.dp))
        setContentView(root)
        arena.post { moveTarget() }
    }
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
    private fun moveTarget() {
        val maxX = (arena.width - target.width).coerceAtLeast(1)
        val maxY = (arena.height - target.height).coerceAtLeast(1)
        target.x = Random.nextInt(maxX).toFloat()
        target.y = Random.nextInt(maxY).toFloat()
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestCodeCapture && resultCode == RESULT_OK && data != null) {
            val intent = Intent(this, CaptureService::class.java).apply { putExtra("code", resultCode); putExtra("data", data) }
            startForegroundService(intent)
        }
    }
}
