package com.example.universalgamebot

import android.util.Log
import java.util.concurrent.atomic.AtomicLong

/**
 * ตัวเก็บข้อมูล Debug แบบ real-time ของวงจร Capture → Vision → Decision → Gesture
 * เขียนจากหลาย thread (vision / accessibility) อ่านจากหน้าจอหลัก
 */
object DebugMonitor {
    const val TAG = "GameBot"

    @Volatile var profileName = "-"
    @Volatile var captureState = "Idle"          // Idle / Starting / Active / Error
    @Volatile var captureSize = ""               // ขนาด buffer ที่จับได้ เช่น 1220x2712
    @Volatile var lastFrameAt = 0L
    @Volatile var avgBrightness = -1             // 0-255 ถ้าใกล้ 0 ตลอด = ภาพดำ (จับภาพไม่ได้จริง)
    @Volatile var objectCount = 0                // จำนวนวัตถุที่ Vision เจอในเฟรมล่าสุด
    @Volatile var bestConfidence = 0f            // ความมั่นใจสูงสุดของเฟรมล่าสุด
    @Volatile var bestLabel = "-"
    @Volatile var imageX = Float.NaN
    @Volatile var imageY = Float.NaN
    @Volatile var screenX = Float.NaN
    @Volatile var screenY = Float.NaN
    @Volatile var gestureState = "None"          // None / Dispatched / Success / Failed / Rejected
    @Volatile var lastError = "-"
    @Volatile var lastSkipReason = "-"
    @Volatile var accessibilityRootPkg = "?"     // package ที่ rootInActiveWindow เห็น (null = ไม่ได้สิทธิ์อ่านหน้าต่าง)
    @Volatile var engine: EngineSnapshot? = null // สถานะ State Machine ล่าสุด

    val framesReceived = AtomicLong()
    val framesProcessed = AtomicLong()
    val framesSkipped = AtomicLong()
    val framesThrottled = AtomicLong()
    val gesturesSent = AtomicLong()
    val gesturesOk = AtomicLong()
    val gesturesFailed = AtomicLong()

    private val skipCounts = HashMap<String, Long>()
    private val events = ArrayDeque<String>()

    fun resetCounters() {
        framesReceived.set(0); framesProcessed.set(0); framesSkipped.set(0); framesThrottled.set(0)
        gesturesSent.set(0); gesturesOk.set(0); gesturesFailed.set(0)
        synchronized(this) { skipCounts.clear() }
        objectCount = 0; bestConfidence = 0f; bestLabel = "-"
        imageX = Float.NaN; imageY = Float.NaN; screenX = Float.NaN; screenY = Float.NaN
        gestureState = "None"; lastSkipReason = "-"; avgBrightness = -1; lastFrameAt = 0L; engine = null
    }

    fun skip(reason: String) {
        framesSkipped.incrementAndGet()
        lastSkipReason = reason
        synchronized(this) { skipCounts[reason] = (skipCounts[reason] ?: 0L) + 1 }
    }

    fun skipSummary(): String = synchronized(this) {
        if (skipCounts.isEmpty()) "-" else skipCounts.entries.sortedByDescending { it.value }.take(5).joinToString("\n") { "  ${it.key} x${it.value}" }
    }

    fun error(msg: String, t: Throwable? = null) {
        lastError = msg
        Log.e(TAG, msg, t)
        ErrorLog.write("ERROR", msg, t)
        addEvent("ERROR $msg")
    }

    fun log(msg: String) {
        Log.i(TAG, msg)
        addEvent(msg)
    }

    private fun addEvent(msg: String) = synchronized(this) {
        events.addLast(msg)
        while (events.size > 6) events.removeFirst()
    }

    fun recentEvents(): String = synchronized(this) { events.joinToString("\n") }
}
