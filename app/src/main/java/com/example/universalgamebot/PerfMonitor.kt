package com.example.universalgamebot

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.os.Process
import android.os.SystemClock

class PerfSnapshot(
    val fps: Float, val recvFps: Float, val visionMs: Float,
    val cpuPercent: Float, val pssMb: Int, val heapMb: Int, val nativeMb: Int,
    val batteryPct: Int, val batteryTempC: Float, val batteryStartPct: Int, val runMinutes: Float,
    val currentMa: Int?, val charging: Boolean
)

/** วัด FPS, เวลาประมวลผล, CPU, RAM, แบตเตอรี่ ของแอปนี้ (ไม่ต้อง Root) */
object PerfMonitor {
    private val processed = LongArray(64); private var pIdx = 0; private var pCount = 0
    private val received = LongArray(64); private var rIdx = 0; private var rCount = 0
    @Volatile private var visionMs = 0f
    private var lastCpuMs = 0L; private var lastWallMs = 0L; private var cpu = 0f
    private var startBatteryPct = -1; private var startAt = 0L

    @Synchronized
    fun reset(context: Context) {
        pCount = 0; rCount = 0; visionMs = 0f
        lastCpuMs = Process.getElapsedCpuTime(); lastWallMs = SystemClock.elapsedRealtime(); cpu = 0f
        startAt = SystemClock.elapsedRealtime()
        startBatteryPct = readBattery(context)?.first ?: -1
    }

    @Synchronized
    fun onFrameReceived() { received[rIdx] = SystemClock.elapsedRealtime(); rIdx = (rIdx + 1) % received.size; if (rCount < received.size) rCount++ }

    @Synchronized
    fun onFrameProcessed(ms: Float) {
        processed[pIdx] = SystemClock.elapsedRealtime(); pIdx = (pIdx + 1) % processed.size; if (pCount < processed.size) pCount++
        visionMs = if (visionMs == 0f) ms else visionMs * 0.9f + ms * 0.1f
    }

    private fun rate(buf: LongArray, count: Int): Float {
        val now = SystemClock.elapsedRealtime()
        var n = 0
        for (i in 0 until count) if (now - buf[i] <= 1000) n++
        return n.toFloat()
    }

    private fun readBattery(context: Context): Triple<Int, Float, Boolean>? {
        val i: Intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return Triple(if (level >= 0) level * 100 / scale else -1, i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f, charging)
    }

    /** เรียกจาก UI thread ประมาณวินาทีละครั้ง */
    @Synchronized
    fun sample(context: Context): PerfSnapshot {
        val wall = SystemClock.elapsedRealtime(); val cpuMs = Process.getElapsedCpuTime()
        if (wall - lastWallMs >= 900) {
            cpu = (cpuMs - lastCpuMs) * 100f / (wall - lastWallMs)
            lastCpuMs = cpuMs; lastWallMs = wall
        }
        val mi = Debug.MemoryInfo(); Debug.getMemoryInfo(mi)
        val rt = Runtime.getRuntime()
        val bat = readBattery(context)
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val mins = if (startAt == 0L) 0f else (wall - startAt) / 60000f
        return PerfSnapshot(
            fps = rate(processed, pCount), recvFps = rate(received, rCount), visionMs = visionMs,
            cpuPercent = cpu, pssMb = mi.totalPss / 1024, heapMb = ((rt.totalMemory() - rt.freeMemory()) / 1048576).toInt(),
            nativeMb = (Debug.getNativeHeapAllocatedSize() / 1048576).toInt(),
            batteryPct = bat?.first ?: -1, batteryTempC = bat?.second ?: 0f, batteryStartPct = startBatteryPct, runMinutes = mins,
            currentMa = if (raw == Int.MIN_VALUE || raw == 0) null else Math.abs(raw) / 1000, charging = bat?.third ?: false
        )
    }
}
