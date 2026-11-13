package com.example.universalgamebot

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.TextView

/** สีของแอป: ห้ามใช้สีเขียวสด (g>190, r<95, b<110) นอกจุดเป้าหมาย ไม่งั้นบอทจะเห็นเป็นเป้า */
object Palette {
    val BG = Color.rgb(15, 19, 30)
    val CARD = Color.rgb(26, 33, 48)
    val ARENA = Color.rgb(30, 38, 56)
    val ACCENT = Color.rgb(56, 189, 248)
    val DANGER = Color.rgb(239, 68, 68)
    val OFF = Color.rgb(51, 65, 85)
    val TEXT = Color.WHITE
    val MUTED = Color.rgb(148, 163, 184)
    val TARGET = Color.rgb(0, 255, 0)
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun Context.roundRect(fill: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

fun Context.actionButton(label: String, fill: Int, textColor: Int, onClick: () -> Unit): Button =
    Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(textColor)
        stateListAnimator = null
        minimumHeight = dp(52)
        minHeight = dp(52)
        background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), roundRect(fill, 14), null)
        setOnClickListener { onClick() }
    }

/** ป้ายสถานะเล็ก ๆ เปิด = สีฟ้า ปิด = สีเทา */
class StatusChip(context: Context) : TextView(context) {
    init {
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
    }

    fun show(label: String, on: Boolean) {
        text = label
        setTextColor(if (on) Color.rgb(8, 20, 35) else Palette.MUTED)
        background = context.roundRect(if (on) Palette.ACCENT else Palette.OFF, 20)
    }
}
