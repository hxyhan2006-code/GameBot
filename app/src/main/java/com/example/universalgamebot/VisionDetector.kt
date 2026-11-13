package com.example.universalgamebot

import java.nio.ByteBuffer

class Detection(val count: Int, val cx: Float, val cy: Float, val avgBrightness: Int)

/** หาจุดสีเขียวสดใน buffer RGBA_8888 เฉพาะในกรอบ (พิกัดภาพ) และคืนค่ากึ่งกลาง (พิกัดภาพ) */
object VisionDetector {
    const val MIN_PIXELS = 12
    private const val STEP = 4

    fun detect(buffer: ByteBuffer, rowStride: Int, pixelStride: Int, imgW: Int, imgH: Int,
               left: Int, top: Int, right: Int, bottom: Int): Detection {
        val x0 = left.coerceIn(0, imgW); val x1 = right.coerceIn(0, imgW)
        val y0 = top.coerceIn(0, imgH); val y1 = bottom.coerceIn(0, imgH)
        var sx = 0L; var sy = 0L; var count = 0
        var lum = 0L; var samples = 0
        val limit = buffer.limit()
        var y = y0
        while (y < y1) {
            var x = x0
            while (x < x1) {
                val i = y * rowStride + x * pixelStride
                if (i + 2 < limit) {
                    val r = buffer.get(i).toInt() and 255
                    val g = buffer.get(i + 1).toInt() and 255
                    val b = buffer.get(i + 2).toInt() and 255
                    lum += (r + g + b) / 3; samples++
                    if (g > 190 && r < 95 && b < 110) { sx += x; sy += y; count++ }
                }
                x += STEP
            }
            y += STEP
        }
        val avg = if (samples > 0) (lum / samples).toInt() else 0
        return if (count > 0) Detection(count, sx.toFloat() / count, sy.toFloat() / count, avg)
        else Detection(0, Float.NaN, Float.NaN, avg)
    }
}
