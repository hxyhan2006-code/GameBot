package com.example.universalgamebot

/** แม่แบบโทนเทา ขนาดเป็น "เซลล์" ของ SampledFrame (ต้องสุ่มด้วย step เดียวกับเฟรม) */
class GrayTemplate(val width: Int, val height: Int, val gray: IntArray) {
    companion object {
        /** สุ่มจากพิกเซล RGB ขนาด size×size ที่กึ่งกลางเซลล์ เหมือนที่ FrameSampler ทำกับเฟรม */
        fun fromRgb(pixels: IntArray, size: Int, step: Int): GrayTemplate {
            val cells = size / step
            val g = IntArray(cells * cells)
            for (j in 0 until cells) for (i in 0 until cells) {
                val c = pixels[(j * step + step / 2) * size + (i * step + step / 2)]
                g[j * cells + i] = (((c shr 16) and 255) * 77 + ((c shr 8) and 255) * 150 + (c and 255) * 29) shr 8
            }
            return GrayTemplate(cells, cells, g)
        }
    }
}

/**
 * Template Matching แบบ SAD (ผลต่างสัมบูรณ์ของโทนเทา) คะแนน = 1 - SAD/(255·n)
 * ค้นหยาบทีละ [stride] เซลล์ แล้วปรับละเอียดรอบจุดที่ดีที่สุด · คะแนนตกต่ำกว่า [threshold] = ไม่ถือว่าเจอ
 */
class TemplateDetector(
    override val id: String,
    private val label: String,
    private val template: GrayTemplate,
    private val threshold: Float = 0.85f,
    private val maxObjects: Int = 4,
    private val stride: Int = 2
) : Detector {
    private class Cand(val x: Int, val y: Int, val score: Float)

    override fun detect(frame: SampledFrame): List<DetectedObject> {
        frame.ensureGray()
        val fw = frame.width; val fh = frame.height
        val tw = template.width; val th = template.height
        if (fw < tw || fh < th || tw == 0) return emptyList()
        val n = tw * th
        val full = 255f * n
        val coarseMax = ((1f - (threshold - 0.06f)) * full).toLong()
        val cands = ArrayList<Cand>()
        var y = 0
        while (y <= fh - th) {
            var x = 0
            while (x <= fw - tw) {
                val sad = sad(frame.gray, fw, x, y, coarseMax)
                if (sad <= coarseMax && cands.size < 4000) cands.add(Cand(x, y, 1f - sad / full))
                x += stride
            }
            y += stride
        }
        cands.sortByDescending { it.score }
        val picked = ArrayList<Cand>()
        val minDist = tw * 0.7f
        for (c in cands) {
            if (picked.size >= maxObjects) break
            if (picked.none { Math.hypot((it.x - c.x).toDouble(), (it.y - c.y).toDouble()) < minDist }) picked.add(c)
        }
        val out = ArrayList<DetectedObject>()
        for (c in picked) {
            var bx = c.x; var by = c.y; var bs = c.score
            for (yy in maxOf(0, c.y - stride + 1)..minOf(fh - th, c.y + stride - 1)) {
                for (xx in maxOf(0, c.x - stride + 1)..minOf(fw - tw, c.x + stride - 1)) {
                    val s = 1f - sad(frame.gray, fw, xx, yy, Long.MAX_VALUE) / full
                    if (s > bs) { bs = s; bx = xx; by = yy }
                }
            }
            if (bs < threshold) continue
            val s = frame.step
            out += DetectedObject(
                label,
                Box((frame.originX + bx * s).toFloat(), (frame.originY + by * s).toFloat(),
                    (frame.originX + (bx + tw) * s).toFloat(), (frame.originY + (by + th) * s).toFloat()),
                bs, id
            )
        }
        out.sortByDescending { it.confidence }
        return out
    }

    private fun sad(g: IntArray, fw: Int, x0: Int, y0: Int, limit: Long): Long {
        val t = template.gray
        val tw = template.width; val th = template.height
        var sum = 0L
        for (j in 0 until th) {
            var fi = (y0 + j) * fw + x0
            var ti = j * tw
            for (i in 0 until tw) {
                val d = g[fi++] - t[ti++]
                sum += if (d < 0) -d else d
            }
            if (sum > limit) return sum
        }
        return sum
    }
}
