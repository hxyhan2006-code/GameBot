package com.example.universalgamebot

class MatchBrainConfig(
    val leadMs: Long = 250,            // ภาพเก่า + หน่วงของ gesture: ทำนายตำแหน่งล่วงหน้าเท่านี้
    val aimMs: Long = 150,             // ต้องชี้จอยไปทางเป้านิ่ง ๆ อย่างน้อยเท่านี้ก่อนยิง/ส่ง (ให้จอยพร้อมก่อนกดปุ่ม)
    val shootRangeDp: Float = 105f,    // ยิงเมื่อห่างเส้นประตูไม่เกินนี้
    val passPressureDp: Float = 60f,   // คู่แข่งเข้าใกล้ขนาดนี้ = ถูกกดดัน
    val followMs: Long = 650,          // หลังยิง/ส่ง ปล่อยจอยรอดูผลเท่านี้
    val cornerFrac: Float = 0.5f,      // จุดเล็งอยู่ห่างกลางประตู = สัดส่วนนี้ของครึ่งปากประตู
    val passCooldownMs: Long = 2200,   // ส่งแบบ "ดันบอลขึ้น" ได้ไม่ถี่กว่านี้
    // Screen pixels, matching the supplied Xiaomi logs; disable or recalibrate on other layouts.
    val restrictShotY: Boolean = true,
    val shotMinY: Float = 1250f,
    val shotMaxY: Float = 1310f,
    val passLaneMarginDp: Float = 8f,
    val passLookAheadMs: Long = 250,
    val maxMatchMs: Long = 360_000     // เพดานความปลอดภัย: ไม่เห็นแถบหมดเวลาก็หยุดเอง
)
