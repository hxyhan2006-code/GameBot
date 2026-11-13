package com.example.universalgamebot

/** กล่องสี่เหลี่ยม (ไม่ผูกกับ Android เพื่อให้เทสต์บน JVM ได้) */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f
    fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom
    fun scaled(sx: Float, sy: Float) = Box(left * sx, top * sy, right * sx, bottom * sy)
}

/** วัตถุที่ Vision ตรวจเจอ: label = ชื่อบทบาท (เช่น "target"), source = ตัวตรวจที่เจอ */
data class DetectedObject(val label: String, val box: Box, val confidence: Float, val source: String) {
    val cx get() = box.centerX
    val cy get() = box.centerY
}

/** ภาพรวมของหนึ่งเฟรมหลัง Vision (พิกัดเป็นพิกัดหน้าจอจริง) bounds = กรอบ arena */
class Observation(val seq: Long, val timeMs: Long, val objects: List<DetectedObject>, val bounds: Box) {
    fun best(label: String): DetectedObject? = objects.filter { it.label == label }.maxByOrNull { it.confidence }
    fun all(label: String): List<DetectedObject> = objects.filter { it.label == label }
}

/** คำสั่งที่ Decision Engine สั่งได้ */
sealed class BotAction {
    abstract val label: String

    data class Tap(val x: Float, val y: Float, override val label: String = "tap", val confidence: Float = 0f) : BotAction()
    data class Swipe(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float,
        val durationMs: Long = 300, override val label: String = "swipe", val confidence: Float = 0f
    ) : BotAction()
    data class Wait(val ms: Long, val reason: String) : BotAction() { override val label get() = "wait" }
    data class Stop(val reason: String) : BotAction() { override val label get() = "stop" }

    /** จุดอ้างอิงของคำสั่ง (ใช้เช็กการกดซ้ำ) */
    fun anchor(): Pair<Float, Float>? = when (this) {
        is Tap -> x to y
        is Swipe -> x1 to y1
        else -> null
    }

    fun describe(): String = when (this) {
        is Tap -> "Tap $label (${x.toInt()},${y.toInt()})"
        is Swipe -> "Swipe $label (${x1.toInt()},${y1.toInt()})->(${x2.toInt()},${y2.toInt()})"
        is Wait -> "Wait ${ms}ms: $reason"
        is Stop -> "Stop: $reason"
    }
}
