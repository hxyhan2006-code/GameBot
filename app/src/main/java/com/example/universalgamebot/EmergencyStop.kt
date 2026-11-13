package com.example.universalgamebot

/**
 * สวิตช์หยุดฉุกเฉิน: ตั้งแล้วทุกชั้น (Decision Engine, ตัวส่ง gesture, บริการจับภาพ) หยุดทันที
 * ทริกเกอร์ได้จากปุ่มแดงบนจอ ปุ่มลดเสียง และปุ่ม Stop ในแจ้งเตือน
 */
object EmergencyStop {
    @Volatile var triggered = false; private set
    @Volatile var reason = "-"; private set

    fun trigger(why: String) {
        triggered = true
        reason = why
        DebugMonitor.log("EMERGENCY STOP: $why")
    }

    fun reset() { triggered = false; reason = "-" }
}
