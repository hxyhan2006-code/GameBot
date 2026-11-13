package com.example.universalgamebot

/**
 * ประเภทความล้มเหลวของ action (ไม่ผูก Android)
 * GESTURE_FAILED = ระบบไม่ทำ/ยกเลิก gesture · REJECTED = ด่านความปลอดภัยไม่ให้ทำ (ไม่นับเป็นความพยายาม)
 * MISSED_TARGET  = มีเฟรมใหม่หลังทำ แต่เป้ายังอยู่ที่เดิม (แตะ/ปัดไม่โดนหรือเกมไม่ตอบสนอง)
 * TIMEOUT        = ไม่มีเฟรมใหม่เลยหลังทำ (หน้าจอไม่เปลี่ยน)
 * STALE_FRAME    = มีเฟรมเข้ามาแต่ท่อ Vision ไม่ส่งผลทันเวลา (ล้าหลัง/ทิ้งเฟรม)
 * DETECTION_ERROR = เห็นเฟรมใหม่แต่ตรวจเป้าไม่เจอเลยตลอดช่วงตรวจผล หรือ Vision/Strategy พัง
 */
enum class FailureKind { GESTURE_FAILED, REJECTED, MISSED_TARGET, TIMEOUT, STALE_FRAME, DETECTION_ERROR }

/** สถิติ action ของโปรไฟล์หนึ่ง (สะสมได้ข้าม session ด้วย plus/serialize) */
class ActionStats {
    var attempts = 0; private set          // action ที่ลงมือจริง (ไม่นับ REJECTED)
    var verified = 0; private set          // ตรวจผลแล้วว่าสำเร็จ
    var suppressed = 0; private set        // กันกดซ้ำ
    var staleSkips = 0; private set        // ข้ามการตัดสินใจเพราะเฟรมล้าหลัง
    private val fails = IntArray(FailureKind.values().size)
    private var totalMsSum = 0L; private var gestureMsSum = 0L; private var verifyMsSum = 0L
    var maxLatencyMs = 0L; private set

    fun failures(kind: FailureKind) = fails[kind.ordinal]
    val failedCount get() = FailureKind.values().filter { it != FailureKind.REJECTED }.sumOf { fails[it.ordinal] }

    fun recordAttempt() { attempts++ }
    fun undoAttempt() { if (attempts > 0) attempts-- }
    fun recordVerified(totalMs: Long, gestureMs: Long, verifyMs: Long) {
        verified++; totalMsSum += totalMs; gestureMsSum += gestureMs; verifyMsSum += verifyMs
        if (totalMs > maxLatencyMs) maxLatencyMs = totalMs
    }
    fun recordFailure(kind: FailureKind) { fails[kind.ordinal]++ }
    fun recordSuppressed() { suppressed++ }
    fun recordStaleSkip() { staleSkips++ }

    /** อัตราสำเร็จที่ตรวจผลแล้ว (verified / attempts) คืน 0 ถ้ายังไม่มี action */
    fun successRate(): Float = if (attempts == 0) 0f else verified.toFloat() / attempts
    fun avgLatencyMs(): Long = if (verified == 0) 0 else totalMsSum / verified
    fun avgGestureMs(): Long = if (verified == 0) 0 else gestureMsSum / verified
    fun avgVerifyMs(): Long = if (verified == 0) 0 else verifyMsSum / verified

    fun copy(): ActionStats = ActionStats().also { it.plusAssign(this) }

    fun plusAssign(o: ActionStats) {
        attempts += o.attempts; verified += o.verified; suppressed += o.suppressed; staleSkips += o.staleSkips
        for (i in fails.indices) fails[i] += o.fails[i]
        totalMsSum += o.totalMsSum; gestureMsSum += o.gestureMsSum; verifyMsSum += o.verifyMsSum
        if (o.maxLatencyMs > maxLatencyMs) maxLatencyMs = o.maxLatencyMs
    }

    fun failureLine(): String = FailureKind.values().joinToString(" ") { "${it.name.lowercase()}=${fails[it.ordinal]}" }

    /** เป้าหมายคุณภาพ: อย่างน้อย 100 action และ verified >= 95% */
    fun goalLine(minActions: Int = 100, minRate: Float = 0.95f): String = when {
        attempts < minActions -> "NOT ENOUGH DATA ($attempts/$minActions actions)"
        successRate() >= minRate -> "PASS"
        else -> "FAIL"
    }

    fun summary(): String =
        "$attempts actions, ${"%.1f".format(successRate() * 100)}% verified, avg ${avgLatencyMs()} ms (gesture ${avgGestureMs()} + verify ${avgVerifyMs()}), max $maxLatencyMs ms"

    fun serialize(): String =
        listOf(attempts, verified, suppressed, staleSkips, totalMsSum, gestureMsSum, verifyMsSum, maxLatencyMs).joinToString(",") + "|" + fails.joinToString(",")

    companion object {
        fun parse(s: String?): ActionStats {
            val r = ActionStats()
            try {
                val parts = s?.split("|") ?: return r
                val a = parts[0].split(",").map { it.toLong() }
                r.attempts = a[0].toInt(); r.verified = a[1].toInt(); r.suppressed = a[2].toInt(); r.staleSkips = a[3].toInt()
                r.totalMsSum = a[4]; r.gestureMsSum = a[5]; r.verifyMsSum = a[6]; r.maxLatencyMs = a[7]
                parts[1].split(",").forEachIndexed { i, v -> if (i < r.fails.size) r.fails[i] = v.toInt() }
            } catch (_: Exception) { return ActionStats() }
            return r
        }
    }
}
