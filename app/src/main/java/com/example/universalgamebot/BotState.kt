package com.example.universalgamebot

import android.graphics.Rect

/** สถานะบอท ให้หน้าจอหลักรู้ว่าตอนนี้กำลังทำงานอยู่ไหม */
object BotState {
    @Volatile var running = false
        private set
    @Volatile var onChange: (() -> Unit)? = null

    fun set(value: Boolean) {
        running = value
        onChange?.invoke()
    }
}

/** พื้นที่เกมทดสอบบนหน้าจอจริง (พิกัดหน้าจอ) + ขนาดจอตอนวัด */
class ArenaInfo(val rect: Rect, val screenW: Int, val screenH: Int)

/**
 * มาตรการความปลอดภัย: บอทแตะได้เฉพาะเมื่อหน้าจอเกมทดสอบของแอปนี้อยู่หน้าสุด (MainActivity resume อยู่)
 * และแตะได้เฉพาะจุดที่อยู่ในกรอบ arena เท่านั้น ไม่ขึ้นกับ rootInActiveWindow
 */
object ArenaGate {
    @Volatile var info: ArenaInfo? = null
}
