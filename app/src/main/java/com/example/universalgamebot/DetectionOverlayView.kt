package com.example.universalgamebot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View

/**
 * วาดกรอบวัตถุที่ Vision ตรวจเจอทับบน arena (พิกัดหน้าจอ → พิกัดในวิว)
 * วาดห่างจากขอบวัตถุ 5dp และใช้สีม่วง/ขาว ที่ไม่ตรงกับตัวตรวจใด ๆ เพื่อไม่ให้ Vision เห็นกรอบเป็นเป้า
 * ไม่รับการสัมผัส (touch ทะลุไปที่เกมด้านล่าง)
 */
class DetectionOverlayView(context: Context) : View(context) {
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = context.dp(2).toFloat(); color = Palette.BOX }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xCC000000.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = context.dp(11).toFloat() }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        if (!OverlayState.showBoxes) return
        val arena = ArenaGate.info ?: return
        if (SystemClock.elapsedRealtime() - OverlayState.updatedAt > 1000) return
        val pad = context.dp(5).toFloat()
        for (o in OverlayState.objects) {
            val b = o.box
            rect.set(b.left - arena.rect.left - pad, b.top - arena.rect.top - pad, b.right - arena.rect.left + pad, b.bottom - arena.rect.top + pad)
            canvas.drawRect(rect, boxPaint)
            val label = "${o.label} ${(o.confidence * 100).toInt()}%"
            val w = textPaint.measureText(label) + pad * 2
            val h = textPaint.textSize + pad
            val ty = if (rect.top - h >= 0) rect.top - h else rect.bottom
            canvas.drawRect(rect.left, ty, rect.left + w, ty + h, pillPaint)
            canvas.drawText(label, rect.left + pad, ty + h - pad * 0.8f, textPaint)
        }
    }
}
