package com.example.universalgamebot

import java.nio.ByteBuffer

/**
 * ภาพที่สุ่มจุดจากเฉพาะกรอบ arena ทุก [step] พิกเซล (ลดงานให้ Vision)
 * เซลล์ (i,j) = พิกเซลภาพที่ (originX + i*step + step/2, originY + j*step + step/2)
 * ใช้ซ้ำ object เดิมทุกเฟรมเพื่อไม่ให้เกิดขยะ
 */
class SampledFrame {
    var width = 0; private set
    var height = 0; private set
    var step = 1; private set
    var originX = 0; private set
    var originY = 0; private set
    var rgb = IntArray(0); private set
    var gray = IntArray(0); private set
    var avgBrightness = 0
    private var grayReady = false

    fun prepare(w: Int, h: Int, step: Int, originX: Int, originY: Int) {
        width = w; height = h; this.step = step; this.originX = originX; this.originY = originY
        if (rgb.size < w * h) { rgb = IntArray(w * h); gray = IntArray(w * h) }
        grayReady = false
    }

    fun ensureGray() {
        if (grayReady) return
        for (i in 0 until width * height) {
            val c = rgb[i]
            gray[i] = (((c shr 16) and 255) * 77 + ((c shr 8) and 255) * 150 + (c and 255) * 29) shr 8
        }
        grayReady = true
    }

    companion object {
        /** สร้างจากพิกเซล RGB เต็ม (ใช้ในเทสต์) */
        fun fromRgb(w: Int, h: Int, pixels: IntArray, step: Int, left: Int = 0, top: Int = 0, right: Int = w, bottom: Int = h): SampledFrame {
            val f = SampledFrame()
            sampleInto(f, step, left, top, right, bottom, w, h) { x, y -> pixels[y * w + x] }
            return f
        }

        /** สร้างจากฟังก์ชันสีของพิกเซล (ใช้ในเทสต์จำลองฉากโดยไม่ต้องวาดทั้งภาพ) */
        fun fromFunction(w: Int, h: Int, step: Int, pixel: (Int, Int) -> Int): SampledFrame {
            val f = SampledFrame()
            sampleInto(f, step, 0, 0, w, h, w, h, pixel)
            return f
        }

        /** อ่านจาก buffer RGBA_8888 ของ ImageReader (คืน false ถ้ากรอบว่าง) */
        fun sample(buf: ByteBuffer, rowStride: Int, pixelStride: Int, imgW: Int, imgH: Int,
                   left: Int, top: Int, right: Int, bottom: Int, step: Int, out: SampledFrame): Boolean {
            val limit = buf.limit()
            return sampleInto(out, step, left, top, right, bottom, imgW, imgH) { x, y ->
                val i = y * rowStride + x * pixelStride
                if (i + 2 < limit) ((buf.get(i).toInt() and 255) shl 16) or ((buf.get(i + 1).toInt() and 255) shl 8) or (buf.get(i + 2).toInt() and 255) else 0
            }
        }

        private inline fun sampleInto(out: SampledFrame, step: Int, left: Int, top: Int, right: Int, bottom: Int,
                                      imgW: Int, imgH: Int, pixel: (Int, Int) -> Int): Boolean {
            val x0 = left.coerceIn(0, imgW); val x1 = right.coerceIn(0, imgW)
            val y0 = top.coerceIn(0, imgH); val y1 = bottom.coerceIn(0, imgH)
            val w = (x1 - x0) / step; val h = (y1 - y0) / step
            if (w <= 0 || h <= 0) return false
            out.prepare(w, h, step, x0, y0)
            var lum = 0L
            for (j in 0 until h) {
                val y = minOf(y0 + j * step + step / 2, imgH - 1)
                for (i in 0 until w) {
                    val x = minOf(x0 + i * step + step / 2, imgW - 1)
                    val c = pixel(x, y)
                    out.rgb[j * w + i] = c
                    lum += (((c shr 16) and 255) + ((c shr 8) and 255) + (c and 255)) / 3
                }
            }
            out.avgBrightness = (lum / (w * h)).toInt()
            return true
        }
    }
}
