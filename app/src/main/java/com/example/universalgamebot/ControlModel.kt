package com.example.universalgamebot

import kotlin.math.hypot

/** จุดกดปุ่ม (พิกัดหน้าจอ) */
data class TapPoint(val x: Float, val y: Float, val label: String)

/**
 * คำสั่งควบคุมต่อเนื่อง 1 รอบ (ไม่ผูกกับ Android):
 * stickFrom = จุดวางนิ้วแรกของจอย (กึ่งกลางฐานจอย) · stickTo = จุดที่นิ้วจอยต้องไปอยู่ (null = ปล่อยจอย)
 * taps = ปุ่มที่กดในรอบนี้ พร้อมกับการลากจอย (multi-touch)
 */
data class ControlCommand(
    val stickFrom: Pair<Float, Float>? = null,
    val stickTo: Pair<Float, Float>? = null,
    val taps: List<TapPoint> = emptyList()
) {
    fun describe(): String = buildString {
        if (stickTo != null) append("stick->(${stickTo.first.toInt()},${stickTo.second.toInt()})") else append("stick released")
        taps.forEach { append(" + tap ${it.label}") }
    }
}

class ControlStep(val ms: Long, val cmd: ControlCommand)

sealed class ControlDecision {
    class Move(val cmd: ControlCommand, val note: String) : ControlDecision()
    class Stop(val reason: String) : ControlDecision()
}

enum class ShotOutcome { PENDING, GOAL, MISSED, LOST }

/** สมองของเกมแบบเรียลไทม์ (ควบคุมต่อเนื่อง) ไม่ผูก Android */
interface ControlStrategy {
    /** เรียกทุกเฟรมที่ Vision ประมวลผล (ถี่กว่า decide) ไว้ติดตามความเร็วของวัตถุ */
    fun onObservation(obs: Observation) {}
    fun decide(obs: Observation, nowMs: Long): ControlDecision
    /** ตัดสินผลการยิงจากภาพ (Vision) ไม่ใช้คะแนนจริงของเกม */
    fun shotOutcome(obs: Observation, nowMs: Long, shotAtMs: Long): ShotOutcome
}

/** ผู้ลงมือควบคุมต่อเนื่อง (Accessibility) คืน null = รับคำสั่ง (ผลมาทาง onDone) หรือเหตุผลที่ปฏิเสธ */
interface ControlExecutor {
    fun send(cmd: ControlCommand, onDone: (ok: Boolean, detail: String) -> Unit): String?
    /** ปล่อยนิ้วจอยที่ค้างอยู่ทันที (ตอนหยุด/ฉุกเฉิน) */
    fun releaseAll()
}

/** สิ่งที่ CaptureService ต้องการจากเครื่องยนต์ตัดสินใจ ไม่ว่าจะเป็นแบบแยกคำสั่ง (DecisionEngine) หรือต่อเนื่อง (RealtimeEngine) */
interface BotEngine {
    val isFinished: Boolean
    fun onFrameReceived(timeMs: Long)
    fun onObservation(o: Observation)
    fun step()
    fun emergencyStop()
    fun statsCopy(): ActionStats
    fun release() {}
}

/** ตัวเลขของการควบคุมต่อเนื่อง (ฝั่งบอท) ไว้เทียบกับตัวเลขฝั่งเกม (ControlInputStats) */
data class ControlTelemetry(
    val commands: Int = 0, val moves: Int = 0, val releases: Int = 0, val taps: Int = 0,
    val multiTouchTaps: Int = 0, val directionChanges: Int = 0, val cancelled: Int = 0, val rejected: Int = 0,
    val sendMsSum: Long = 0, val maxSendMs: Long = 0
) {
    val avgSendMs get() = if (commands == 0) 0L else sendMsSum / commands
    fun line() = "cmds=$commands moves=$moves releases=$releases taps=$taps multiTouch=$multiTouchTaps dirChanges=$directionChanges cancelled=$cancelled rejected=$rejected send avg ${avgSendMs}ms max ${maxSendMs}ms"
}
