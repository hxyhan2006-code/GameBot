package com.example.universalgamebot

import kotlin.math.max

/**
 * ตัวเลขฝั่งเกม: ปุ่มควบคุมในสนามฟุตบอลได้รับการสัมผัสแบบไหนบ้าง (นับที่ปลายทางจริง ไม่ใช่ที่บอทส่ง)
 * ไว้เทียบกับ ControlTelemetry ของบอท เพื่อพิสูจน์ว่า multi-touch / ควบคุมต่อเนื่อง ทำงานจริงบนเครื่อง
 */
object ControlInputStats {
    @Volatile var stickDowns = 0; private set
    @Volatile var stickReleases = 0; private set
    @Volatile var maxPointers = 0; private set
    @Volatile var directionChanges = 0; private set
    @Volatile var shootPresses = 0; private set
    @Volatile var passPresses = 0; private set
    @Volatile var shootWhileStick = 0; private set
    @Volatile var passWhileStick = 0; private set
    @Volatile var tacklePresses = 0; private set
    @Volatile var tackleWhileStick = 0; private set

    fun reset() {
        stickDowns = 0; stickReleases = 0; maxPointers = 0; directionChanges = 0
        shootPresses = 0; passPresses = 0; shootWhileStick = 0; passWhileStick = 0; tacklePresses = 0; tackleWhileStick = 0
    }

    fun stickDown() { stickDowns++ }
    fun stickUp() { stickReleases++ }
    fun directionChange() { directionChanges++ }
    fun pointers(n: Int) { maxPointers = max(maxPointers, n) }
    fun button(isShoot: Boolean, stickHeld: Boolean) {
        if (isShoot) { shootPresses++; if (stickHeld) shootWhileStick++ } else { passPresses++; if (stickHeld) passWhileStick++ }
    }

    fun tackle(stickHeld: Boolean) { tacklePresses++; if (stickHeld) tackleWhileStick++ }

    val used get() = stickDowns > 0 || shootPresses > 0 || passPresses > 0

    fun line() = "pad touches: stickDown=$stickDowns stickUp=$stickReleases maxPointers=$maxPointers dirChanges=$directionChanges " +
        "shoot=$shootPresses (while stick held $shootWhileStick) pass=$passPresses (while stick held $passWhileStick)" +
        if (tacklePresses > 0) " tackle=$tacklePresses (while stick held $tackleWhileStick)" else ""
}
