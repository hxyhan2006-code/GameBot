package com.example.universalgamebot

/**
 * สถิติของการรันหนึ่งครั้ง (กด Start ถึงหยุด) เก็บไว้ดูหลังหยุดด้วย: FPS เฉลี่ย, เฟรมที่ถูกทิ้ง และสาเหตุ
 * เขียนจาก vision thread อ่านจาก UI: ใช้ @Synchronized (เบามาก)
 */
class SessionStats(val profileId: String, val startMs: Long, private val visionIntervalMs: Long) {
    var endMs = 0L; private set
    var received = 0L; private set        // เฟรมที่ได้รับ (acquire สำเร็จ)
    var throttled = 0L; private set       // ทิ้งโดยตั้งใจ (เร็วกว่า visionInterval)
    var superseded = 0L; private set      // เฟรมเก่าที่ถูกแทนด้วยเฟรมใหม่ เพราะเรายังประมวลผลไม่เสร็จ = backpressure
    var acquireErrors = 0L; private set
    var processed = 0L; private set
    private var fpsSamples = 0; private var fpsSum = 0.0
    var minFps = Float.MAX_VALUE; private set
    var maxFps = 0f; private set
    private var procMsSum = 0.0; var procMsMax = 0f; private set

    @Synchronized fun onReceived() { received++ }
    @Synchronized fun onThrottled() { throttled++ }
    @Synchronized fun onSuperseded() { superseded++ }
    @Synchronized fun onAcquireError() { acquireErrors++ }
    @Synchronized fun onProcessed(ms: Float) { processed++; procMsSum += ms; if (ms > procMsMax) procMsMax = ms }
    @Synchronized fun sampleFps(fps: Float) {
        fpsSamples++; fpsSum += fps
        if (fps < minFps) minFps = fps
        if (fps > maxFps) maxFps = fps
    }
    @Synchronized fun end(nowMs: Long) { if (endMs == 0L) endMs = nowMs }

    val isRunning get() = endMs == 0L
    fun durationSec(nowMs: Long): Float = ((if (endMs > 0) endMs else nowMs) - startMs).coerceAtLeast(1) / 1000f

    /** FPS เฉลี่ยทั้งรอบ = เฟรมที่ประมวลผล / วินาทีที่รัน (ใช้ได้หลังหยุดด้วย) */
    @Synchronized fun avgFps(nowMs: Long): Float = processed / durationSec(nowMs)
    @Synchronized fun avgProcMs(): Float = if (processed == 0L) 0f else (procMsSum / processed).toFloat()

    /**
     * วิเคราะห์เฟรมที่หาย: "ทิ้งโดยตั้งใจ" (throttle) แยกจาก "ถูกแทนที่" (superseded = ประมวลผลไม่ทัน)
     * ถ้า superseded เกิน 5% ของเฟรมที่โผล่มา ถือว่ามี backpressure
     */
    @Synchronized fun dropAnalysis(): String {
        val seen = received + superseded
        if (seen == 0L) return "no frames yet"
        val supPct = superseded * 100f / seen
        val thrPct = if (received == 0L) 0f else throttled * 100f / received
        val verdict = if (supPct > 5f) "BACKPRESSURE (vision cannot keep up)" else "no backpressure"
        return "received $received, throttled on purpose $throttled (${"%.0f".format(thrPct)}%, interval $visionIntervalMs ms), " +
            "superseded $superseded (${"%.1f".format(supPct)}%), acquire errors $acquireErrors -> $verdict"
    }

    @Synchronized fun fpsLine(nowMs: Long): String =
        "avg ${"%.1f".format(avgFps(nowMs))} fps (min ${if (fpsSamples == 0) "-" else "%.0f".format(minFps)}, max ${"%.0f".format(maxFps)}), " +
            "vision avg ${"%.1f".format(avgProcMs())} ms (max ${"%.1f".format(procMsMax)} ms), ran ${"%.0f".format(durationSec(nowMs))} s"
}
