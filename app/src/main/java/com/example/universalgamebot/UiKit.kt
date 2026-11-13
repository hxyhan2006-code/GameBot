package com.example.universalgamebot

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.widget.Button

/** สีของแอป: ห้ามใช้สีเขียวสด (g>190, r<95, b<110), เหลือง (r>200, g 170-240, b<90), ฟ้า (r<40, g 140-200, b>220) ใน arena นอกจากเป้า ไม่งั้นบอทเห็นเป็นเป้า */
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
    val BALL = Color.rgb(255, 215, 0)
    val GOAL = Color.rgb(0, 170, 255)
    val BOX = Color.rgb(255, 0, 255) // กรอบ overlay ใช้สีที่ไม่ตรงกับตัวตรวจใด ๆ
    // สีของ UI นอก arena (ไม่ปะปนกับสีที่ตัวตรวจใช้ เพราะ Vision สแกนเฉพาะในกรอบ arena)
    val CARD_LINE = Color.rgb(36, 48, 73)
    val OK = Color.rgb(34, 197, 94)
    val OK_TINT = Color.rgb(15, 42, 29)
    val DANGER_TINT = Color.rgb(58, 24, 30)
    val ACCENT_TINT = Color.rgb(18, 50, 74)
    val SHEET = Color.rgb(22, 29, 43)
    val ON_ACCENT = Color.rgb(8, 32, 51)
    val WARN = Color.rgb(251, 191, 36)
    val SOFT = Color.rgb(203, 213, 225)
    val DIM = Color.rgb(100, 116, 139)
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

/** ไอคอนเส้นที่วาดเอง (ไม่ใช้อีโมจิ/รูปภาพ) · วาดบนตาราง 24x24 แล้วขยายตามขนาดวิว */
enum class Icon { GRID, SHIELD, BARS, SLIDERS, BACK, CHEVRON, DOT, RING, SWIPE, PAD, TAP, BOXES, CODE, WARN }

class IconView(context: Context, private val icon: Icon, tint: Int = Palette.MUTED) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = 2f; color = tint
    }

    fun setTint(color: Int) { paint.color = color; invalidate() }

    private fun poly(c: Canvas, vararg p: Float) {
        val path = Path().apply { moveTo(p[0], p[1]); var i = 2; while (i < p.size) { lineTo(p[i], p[i + 1]); i += 2 } }
        c.drawPath(path, paint)
    }

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height) / 24f
        c.save()
        c.translate((width - 24 * s) / 2f, (height - 24 * s) / 2f)
        c.scale(s, s)
        paint.style = Paint.Style.STROKE
        when (icon) {
            Icon.GRID -> for (x in floatArrayOf(4f, 13f)) for (y in floatArrayOf(4f, 13f)) c.drawRoundRect(x, y, x + 7f, y + 7f, 1.5f, 1.5f, paint)
            Icon.SHIELD -> c.drawPath(Path().apply {
                moveTo(12f, 3f); lineTo(20f, 6f); lineTo(20f, 12f); cubicTo(20f, 17f, 16.5f, 20f, 12f, 21f)
                cubicTo(7.5f, 20f, 4f, 17f, 4f, 12f); lineTo(4f, 6f); close()
            }, paint)
            Icon.BARS -> { poly(c, 5f, 20f, 5f, 10f); poly(c, 12f, 20f, 12f, 4f); poly(c, 19f, 20f, 19f, 13f) }
            Icon.SLIDERS -> {
                poly(c, 4f, 7f, 14f, 7f); poly(c, 18f, 7f, 20f, 7f); c.drawCircle(16f, 7f, 2f, paint)
                poly(c, 4f, 17f, 6f, 17f); poly(c, 10f, 17f, 20f, 17f); c.drawCircle(8f, 17f, 2f, paint)
            }
            Icon.BACK -> poly(c, 15f, 6f, 9f, 12f, 15f, 18f)
            Icon.CHEVRON -> poly(c, 9f, 6f, 15f, 12f, 9f, 18f)
            Icon.DOT -> { paint.style = Paint.Style.FILL; c.drawCircle(12f, 12f, 6f, paint) }
            Icon.RING -> { c.drawCircle(12f, 12f, 9f, paint); c.drawCircle(12f, 12f, 4f, paint) }
            Icon.SWIPE -> { poly(c, 12f, 20f, 12f, 6f); poly(c, 6f, 11f, 12f, 5f, 18f, 11f) }
            Icon.PAD -> { c.drawCircle(8f, 14f, 5f, paint); c.drawCircle(18f, 9f, 3f, paint) }
            Icon.TAP -> { c.drawCircle(12f, 12f, 8f, paint); paint.style = Paint.Style.FILL; c.drawCircle(12f, 12f, 2.5f, paint) }
            Icon.BOXES -> { poly(c, 4f, 8f, 4f, 4f, 8f, 4f); poly(c, 16f, 4f, 20f, 4f, 20f, 8f); poly(c, 20f, 16f, 20f, 20f, 16f, 20f); poly(c, 8f, 20f, 4f, 20f, 4f, 16f) }
            Icon.CODE -> { poly(c, 8f, 6f, 3f, 12f, 8f, 18f); poly(c, 16f, 6f, 21f, 12f, 16f, 18f) }
            Icon.WARN -> { poly(c, 12f, 3f, 21f, 19f, 3f, 19f, 12f, 3f); poly(c, 12f, 10f, 12f, 14f); poly(c, 12f, 17f, 12.01f, 17f) }
        }
        c.restore()
    }
}
