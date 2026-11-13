package com.example.universalgamebot

/** ผล Vision ล่าสุด (พิกัดหน้าจอ) ให้ชั้นวาดกรอบบนจออ่านไปแสดง */
object OverlayState {
    @Volatile var objects: List<DetectedObject> = emptyList(); private set
    @Volatile var updatedAt = 0L; private set
    @Volatile var showBoxes = true

    fun publish(list: List<DetectedObject>, timeMs: Long) { objects = list; updatedAt = timeMs }
    fun clear() { objects = emptyList(); updatedAt = 0L }
}
