package com.example.universalgamebot

fun interface ColorRule { fun matches(r: Int, g: Int, b: Int): Boolean }

/**
 * ตรวจจับตามสี: ทำหน้ากากสีแล้วแยกก้อน (connected components) ได้หลายวัตถุพร้อมกรอบ
 * confidence = ขนาดก้อนเทียบกับขั้นต่ำ × ความเต็มของกรอบ (วงกลมเต็ม ~0.78, สี่เหลี่ยม 1.0)
 */
class ColorDetector(
    override val id: String,
    private val label: String,
    private val rule: ColorRule,
    private val minCells: Int = 24,
    private val maxObjects: Int = 6
) : Detector {
    private var marks = ByteArray(0)
    private var stack = IntArray(0)
    private val unvisited: Byte = 1
    private val visited: Byte = 2

    override fun detect(frame: SampledFrame): List<DetectedObject> {
        val w = frame.width; val h = frame.height; val n = w * h
        if (n == 0) return emptyList()
        if (marks.size < n) { marks = ByteArray(n); stack = IntArray(n) }
        java.util.Arrays.fill(marks, 0, n, 0)
        val rgb = frame.rgb
        for (i in 0 until n) {
            val c = rgb[i]
            if (rule.matches((c shr 16) and 255, (c shr 8) and 255, c and 255)) marks[i] = unvisited
        }
        val out = ArrayList<DetectedObject>()
        for (start in 0 until n) {
            if (marks[start] != unvisited) continue
            var sp = 0
            stack[sp++] = start; marks[start] = visited
            var cnt = 0; var minX = w; var maxX = -1; var minY = h; var maxY = -1
            while (sp > 0) {
                val p = stack[--sp]
                val x = p % w; val y = p / w
                cnt++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                if (x > 0 && marks[p - 1] == unvisited) { marks[p - 1] = visited; stack[sp++] = p - 1 }
                if (x < w - 1 && marks[p + 1] == unvisited) { marks[p + 1] = visited; stack[sp++] = p + 1 }
                if (y > 0 && marks[p - w] == unvisited) { marks[p - w] = visited; stack[sp++] = p - w }
                if (y < h - 1 && marks[p + w] == unvisited) { marks[p + w] = visited; stack[sp++] = p + w }
            }
            if (cnt < minCells) continue
            val fill = cnt.toFloat() / ((maxX - minX + 1) * (maxY - minY + 1))
            val conf = minOf(1f, cnt / (minCells * 3f)) * minOf(1f, fill / 0.5f)
            val s = frame.step
            val box = Box(
                (frame.originX + minX * s).toFloat(), (frame.originY + minY * s).toFloat(),
                (frame.originX + (maxX + 1) * s).toFloat(), (frame.originY + (maxY + 1) * s).toFloat()
            )
            out += DetectedObject(label, box, conf, id)
        }
        out.sortByDescending { it.confidence * 1000f + it.box.width }
        return if (out.size > maxObjects) out.subList(0, maxObjects).toList() else out
    }
}
