package com.example.universalgamebot

import kotlin.math.sqrt

/**
 * ลวดลายเป้าของเกม Bullseye (เขียนเป็นสูตรล้วน ไม่ใช้ Canvas) เพื่อให้
 * "สิ่งที่วาดบนจอ" กับ "แม่แบบที่ Vision ใช้ค้นหา" เป็นพิกเซลเดียวกันเป๊ะ และเทสต์บน JVM ได้
 */
enum class ArtKind { BULLSEYE, SOLID, INVERSE, DOT }

object TargetArt {
    private const val LIGHT = 255
    private const val DARK = 20

    private fun tone(kind: ArtKind, r: Float): Int = when (kind) {
        ArtKind.BULLSEYE -> if (r < 0.33f) LIGHT else if (r < 0.66f) DARK else LIGHT
        ArtKind.SOLID -> LIGHT
        ArtKind.INVERSE -> if (r < 0.33f) DARK else if (r < 0.66f) LIGHT else DARK
        ArtKind.DOT -> if (r < 0.2f) DARK else LIGHT
    }

    /** คืนพิกเซล RGB (size×size) ลายอยู่ในวงกลมรัศมีครึ่งหนึ่งของ size นอกวงกลมเป็นสี [bg] */
    fun render(kind: ArtKind, size: Int, bg: Int): IntArray {
        val out = IntArray(size * size)
        val c = size / 2f
        val bgR = (bg shr 16) and 255; val bgG = (bg shr 8) and 255; val bgB = bg and 255
        for (y in 0 until size) for (x in 0 until size) {
            var tr = 0; var tg = 0; var tb = 0
            for (sy in 0 until 3) for (sx in 0 until 3) {
                val dx = x + (sx + 0.5f) / 3f - c; val dy = y + (sy + 0.5f) / 3f - c
                val r = sqrt(dx * dx + dy * dy) / c
                if (r > 1f) { tr += bgR; tg += bgG; tb += bgB } else { val t = tone(kind, r); tr += t; tg += t; tb += t }
            }
            out[y * size + x] = ((tr / 9) shl 16) or ((tg / 9) shl 8) or (tb / 9)
        }
        return out
    }
}
