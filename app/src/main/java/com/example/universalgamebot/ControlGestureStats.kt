package com.example.universalgamebot

/**
 * สถิติของ gesture ควบคุมต่อเนื่อง แยกตามชนิด (เริ่มเส้นใหม่ / ลากต่อเนื่อง / ปล่อย / มีการกดปุ่มรวม) และช่วงว่างระหว่าง gesture
 * ใช้ 2 อย่าง: (1) แสดงในรายงานว่า gesture แบบไหนถูกระบบยกเลิก (2) เลือกจังหวะกดปุ่มในท่าเดียวกับจอยที่ได้ผลจริงบนเครื่องนั้น
 * (ลองหน่วงการกดปุ่ม 0 / 35 / 70 ms แล้วเลือกแบบที่ไม่ถูกยกเลิกบ่อยที่สุด) ไม่ผูกกับ Android
 */
object ControlGestureStats {
    val TAP_DELAYS = longArrayOf(0, 35, 70)
    private val kinds = LinkedHashMap<String, IntArray>()
    private val variantOk = IntArray(TAP_DELAYS.size)
    private val variantBad = IntArray(TAP_DELAYS.size)
    // คะแนนแบบให้น้ำหนักผลล่าสุดมากกว่า (ค่อย ๆ ลืมของเก่า) เพื่อสลับแบบได้ทันเมื่อเครื่องเริ่มยกเลิกบ่อย
    private val recentOk = DoubleArray(TAP_DELAYS.size)
    private val recentBad = DoubleArray(TAP_DELAYS.size)
    private var gapOkSum = 0L; private var gapOkN = 0
    private var gapBadSum = 0L; private var gapBadN = 0

    @Synchronized fun reset() {
        kinds.clear(); variantOk.fill(0); variantBad.fill(0); recentOk.fill(0.0); recentBad.fill(0.0)
        gapOkSum = 0; gapOkN = 0; gapBadSum = 0; gapBadN = 0
    }

    /** เลือกแบบหน่วงการกดปุ่มที่คะแนนล่าสุด (สำเร็จ+1)/(ทั้งหมด+2) สูงสุด: แบบที่เพิ่งล้มคะแนนตก จึงสลับไปลองแบบอื่นเอง */
    @Synchronized fun pickTapVariant(): Int {
        var best = 0; var bestScore = -1.0
        for (i in TAP_DELAYS.indices) {
            val s = (recentOk[i] + 1.0) / (recentOk[i] + recentBad[i] + 2.0)
            if (s > bestScore + 1e-9) { bestScore = s; best = i }
        }
        return best
    }

    /** variant = -1 ถ้า gesture นี้ไม่มีการกดปุ่ม · gapMs = เวลาตั้งแต่ gesture ก่อนหน้าจบถึงตอนส่งอันนี้ */
    @Synchronized fun record(kind: String, variant: Int, ok: Boolean, gapMs: Long) {
        val c = kinds.getOrPut(kind) { IntArray(2) }
        if (ok) c[0]++ else c[1]++
        if (variant in TAP_DELAYS.indices) {
            if (ok) variantOk[variant]++ else variantBad[variant]++
            recentOk[variant] *= 0.8; recentBad[variant] *= 0.8
            if (ok) recentOk[variant] += 1.0 else recentBad[variant] += 1.0
        }
        if (ok) { gapOkSum += gapMs; gapOkN++ } else { gapBadSum += gapMs; gapBadN++ }
    }

    @Synchronized fun total(): Int = kinds.values.sumOf { it[0] + it[1] }

    @Synchronized fun line(): String {
        if (kinds.isEmpty()) return "-"
        val k = kinds.entries.joinToString(" ") { "${it.key} ok=${it.value[0]}/cancel=${it.value[1]}" }
        val v = TAP_DELAYS.indices.joinToString(" ") { "${TAP_DELAYS[it]}ms:${variantOk[it]}/${variantBad[it]}" }
        val gOk = if (gapOkN == 0) "-" else "${gapOkSum / gapOkN}ms"
        val gBad = if (gapBadN == 0) "-" else "${gapBadSum / gapBadN}ms"
        return "$k | tap delay ok/cancel $v | avg gap before dispatch: ok $gOk, cancelled $gBad"
    }
}
