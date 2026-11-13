package com.example.universalgamebot

/** ติดตามตำแหน่งตามเวลา เพื่อประมาณความเร็ว (กำลังสองน้อยสุดบนตัวอย่างล่าสุด ~300 ms) */
class Track {
    private val t = LongArray(12); private val x = FloatArray(12); private val y = FloatArray(12)
    private var n = 0
    var lastAt = 0L; private set

    fun reset() { n = 0; lastAt = 0L }

    fun add(time: Long, px: Float, py: Float) {
        if (n > 0 && time <= t[n - 1]) return
        if (n == t.size) { for (i in 1 until n) { t[i - 1] = t[i]; x[i - 1] = x[i]; y[i - 1] = y[i] }; n-- }
        t[n] = time; x[n] = px; y[n] = py; n++; lastAt = time
    }

    /** ระยะที่เปลี่ยนไปจากตัวอย่างที่เก่ากว่าตัวล่าสุดอย่างน้อย minSpanMs (ใช้ดู "ทิศ" ล่าสุดโดยไม่ถูกบดบังด้วยการเด้งกลับในหน้าต่างยาว) */
    fun recentDelta(minSpanMs: Long = 80): Float {
        if (n < 2) return 0f
        var i = n - 2
        while (i > 0 && t[n - 1] - t[i] < minSpanMs) i--
        return if (t[n - 1] - t[i] < minSpanMs / 2) 0f else y[n - 1] - y[i]
    }

    /** ความเร็ว px/s (0 ถ้าข้อมูลน้อยเกินไป) */
    fun velocity(windowMs: Long = 300): Pair<Float, Float> {
        if (n < 3) return 0f to 0f
        var first = n - 1
        while (first > 0 && t[n - 1] - t[first - 1] <= windowMs) first--
        val m = n - first
        if (m < 3 || t[n - 1] - t[first] < 90) return 0f to 0f
        val t0 = t[first].toDouble()
        var st = 0.0; var sx = 0.0; var sy = 0.0
        for (i in first until n) { st += t[i] - t0; sx += x[i]; sy += y[i] }
        val mt = st / m; val mx = sx / m; val my = sy / m
        var num = 0.0; var nx = 0.0; var ny = 0.0
        for (i in first until n) { val dt = t[i] - t0 - mt; num += dt * dt; nx += dt * (x[i] - mx); ny += dt * (y[i] - my) }
        if (num <= 0.0) return 0f to 0f
        return (nx / num * 1000.0).toFloat() to (ny / num * 1000.0).toFloat()
    }
}
