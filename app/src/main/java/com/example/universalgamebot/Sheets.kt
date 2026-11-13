package com.example.universalgamebot

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** แผงที่เลื่อนขึ้นจากด้านล่าง (แตะพื้นหลังมืดหรือกดปุ่มกลับเพื่อปิด) ใช้แทนการขยายในหน้าเดิม */
class BottomSheet(private val activity: Activity) {
    private val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
    val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    val isShowing: Boolean get() = dialog.isShowing

    init {
        val r = activity.dp(24).toFloat()
        val sidePad = activity.dp(20)
        val basePad = activity.dp(28)
        content.background = GradientDrawable().apply {
            setColor(Palette.SHEET)
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            setStroke(activity.dp(1), Palette.OFF)
        }
        content.setPadding(sidePad, activity.dp(10), sidePad, basePad)
        content.setOnClickListener { } // แตะในแผงไม่ปิด
        content.setOnApplyWindowInsetsListener { v, insets ->
            val bottom = if (Build.VERSION.SDK_INT >= 30) insets.getInsets(WindowInsets.Type.systemBars()).bottom
            else @Suppress("DEPRECATION") insets.systemWindowInsetBottom
            v.setPadding(sidePad, activity.dp(10), sidePad, basePad + bottom)
            insets
        }
        val frame = FrameLayout(activity).apply {
            setBackgroundColor(0x9E05080E.toInt())
            setOnClickListener { dialog.dismiss() }
            addView(content, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        }
        dialog.setContentView(frame)
        dialog.window?.setLayout(-1, -1)
    }

    /** เริ่มเนื้อหาใหม่: แถบจับ + หัวข้อ + ปุ่ม "เสร็จ" */
    fun reset(title: String) {
        content.removeAllViews()
        content.addView(View(activity).apply { background = activity.roundRect(Palette.OFF, 2) }, LinearLayout.LayoutParams(activity.dp(40), activity.dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = activity.dp(8) })
        val head = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(activity).apply { text = title; textSize = 18f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(Palette.TEXT) }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(TextView(activity).apply {
            text = activity.getString(R.string.btn_done); textSize = 14f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Palette.ACCENT); gravity = Gravity.CENTER
            minWidth = activity.dp(48); minHeight = activity.dp(48)
            setOnClickListener { dialog.dismiss() }
        })
        content.addView(head, LinearLayout.LayoutParams(-1, -2))
    }

    fun show() { if (!dialog.isShowing) dialog.show() }
    fun dismiss() { if (dialog.isShowing) dialog.dismiss() }
}

/** แผง Accessibility: สถานะ + เปิด/ปิดบริการ + ขั้นตอน รวมไว้ที่เดียว ใช้ได้ทั้งหน้าแรกและหน้าเกม */
class AccessibilitySheet(private val activity: Activity, private val onChanged: () -> Unit) {
    private val sheet = BottomSheet(activity)
    private fun d(v: Int) = activity.dp(v)

    fun show() { render(); sheet.show() }
    fun dismiss() = sheet.dismiss()

    private fun render() {
        val on = BotAccessibilityService.instance != null
        sheet.reset("Accessibility")
        val c = sheet.content

        val status = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(d(16), d(16), d(16), d(16))
            background = activity.roundRect(if (on) Palette.OK_TINT else Palette.DANGER_TINT, 16, if (on) Palette.OK else Palette.DANGER)
        }
        status.addView(View(activity).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (on) Palette.OK else Palette.DANGER) } }, LinearLayout.LayoutParams(d(14), d(14)).apply { rightMargin = d(14) })
        val texts = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(activity).apply { text = activity.getString(if (on) R.string.sheet_acc_on else R.string.sheet_acc_off); textSize = 20f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(Palette.TEXT) })
        texts.addView(TextView(activity).apply { text = activity.getString(if (on) R.string.sheet_acc_on_sub else R.string.sheet_acc_off_sub); textSize = 13f; setTextColor(Palette.MUTED) })
        status.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        c.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(4) })

        val warn = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        warn.addView(IconView(activity, Icon.WARN, Palette.WARN), LinearLayout.LayoutParams(d(20), d(20)).apply { rightMargin = d(12); topMargin = d(2) })
        warn.addView(TextView(activity).apply { text = activity.getString(R.string.sheet_acc_warn); textSize = 13f; setTextColor(Palette.SOFT) }, LinearLayout.LayoutParams(0, -2, 1f))
        c.addView(warn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(14); bottomMargin = d(16) })

        val openBtn = { primary: Boolean ->
            if (primary) activity.actionButton(activity.getString(R.string.btn_open_acc_settings), Palette.ACCENT, Palette.ON_ACCENT) { openSettings() }
            else activity.actionButton(activity.getString(R.string.btn_open_acc_settings), Palette.SHEET, Palette.TEXT) { openSettings() }.also { it.background = activity.roundRect(Palette.SHEET, 14, Palette.OFF) }
        }
        if (on) {
            c.addView(activity.actionButton(activity.getString(R.string.btn_turn_off_short), Palette.DANGER, Palette.TEXT) { turnOff() }, LinearLayout.LayoutParams(-1, -2))
            c.addView(openBtn(false), LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(10) })
        } else {
            c.addView(openBtn(true), LinearLayout.LayoutParams(-1, -2))
        }

        c.addView(TextView(activity).apply { text = activity.getString(R.string.sheet_steps_title); textSize = 12f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(Palette.DIM); letterSpacing = 0.08f }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(18) })
        c.addView(TextView(activity).apply { text = activity.getString(R.string.sheet_steps, activity.getString(R.string.service_label)); textSize = 13f; setTextColor(Palette.MUTED); setLineSpacing(0f, 1.3f) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(8) })
    }

    /** ไปหน้าตั้งค่า Accessibility (ระบบ Android ไม่ให้แอปเปิดบริการเอง ต้องแตะเปิดเองหนึ่งครั้ง) */
    private fun openSettings() {
        sheet.dismiss()
        Toast.makeText(activity, R.string.hint_accessibility, Toast.LENGTH_LONG).show()
        activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /** ปิดบริการของแอป (หยุดบอทก่อน) แล้วต้องเปิดใหม่เองในหน้าตั้งค่าเมื่อจะทดสอบอีก */
    private fun turnOff() {
        val svc = BotAccessibilityService.instance
        if (svc == null) { Toast.makeText(activity, R.string.toast_acc_already_off, Toast.LENGTH_LONG).show() }
        else {
            activity.stopService(Intent(activity, CaptureService::class.java))
            svc.turnOff()
            Toast.makeText(activity, R.string.toast_acc_off, Toast.LENGTH_LONG).show()
        }
        render()
        onChanged()
    }
}
