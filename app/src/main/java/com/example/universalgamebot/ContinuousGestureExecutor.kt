package com.example.universalgamebot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import kotlin.math.hypot

/**
 * ผู้ลงมือควบคุมต่อเนื่อง: จอย = เส้นลากต่อเนื่องที่ "ไม่ยกนิ้ว" (StrokeDescription.continueStroke ข้าม gesture)
 * ปุ่ม = แตะใน gesture เดียวกัน (multi-touch) · ใช้ด่านความปลอดภัยเดียวกับ GestureExecutor
 * (ไม่ได้กดหยุดฉุกเฉิน, หน้าเกมของแอปนี้อยู่หน้าสุด, ทุกจุดอยู่ในกรอบ arena, หน้าต่าง active ต้องเป็นแอปนี้)
 * ส่งทีละ gesture: รอ onCompleted/onCancelled ก่อนส่งอันถัดไป ถ้าถูกยกเลิก นิ้วจอยที่ค้างถือว่าหลุด
 */
class ContinuousGestureExecutor(private val appPackage: String, private val post: (Runnable) -> Unit) : ControlExecutor {
    private companion object { const val SEG_MS = 120L; const val TAP_MS = 50L }

    private var held: GestureDescription.StrokeDescription? = null
    private var lastX = 0f
    private var lastY = 0f
    @Volatile private var inFlight = false
    @Volatile private var closing = false

    override fun send(cmd: ControlCommand, onDone: (Boolean, String) -> Unit): String? {
        if (EmergencyStop.triggered) return "emergency stop active"
        closing = false
        val arena = ArenaGate.info ?: run { held = null; return "game screen not in foreground" }
        val svc = BotAccessibilityService.instance ?: return "accessibility service not connected"
        val pkg = svc.activePackage()
        DebugMonitor.accessibilityRootPkg = pkg ?: "null (no window content access)"
        if (pkg != null && pkg != appPackage) return "foreground is another app: $pkg"
        fun inside(x: Float, y: Float) = arena.rect.contains(x.toInt(), y.toInt())
        val to = cmd.stickTo; val from = cmd.stickFrom
        if (to != null && !inside(to.first, to.second)) return "stick target outside arena"
        if (from != null && !inside(from.first, from.second)) return "stick origin outside arena"
        for (t in cmd.taps) if (!inside(t.x, t.y)) return "tap ${t.label} outside arena"
        if (inFlight) return "gesture busy"

        val builder = GestureDescription.Builder()
        val prev = held
        var next: GestureDescription.StrokeDescription? = null
        var endX = lastX; var endY = lastY
        var strokes = 0
        if (to != null && from != null) {
            val sx = if (prev != null) lastX else from.first
            val sy = if (prev != null) lastY else from.second
            val tiny = hypot(to.first - sx, to.second - sy) < 1f
            endX = if (tiny) sx + 0.5f else to.first
            endY = to.second
            val path = Path().apply { moveTo(sx, sy); lineTo(endX, endY) }
            next = if (prev == null) GestureDescription.StrokeDescription(path, 0, SEG_MS, true) else prev.continueStroke(path, 0, SEG_MS, true)
            builder.addStroke(next); strokes++
        } else if (prev != null) {
            val path = Path().apply { moveTo(lastX, lastY); lineTo(lastX + 0.5f, lastY) }
            builder.addStroke(prev.continueStroke(path, 0, TAP_MS, false)); strokes++
        }
        for (t in cmd.taps) {
            val path = Path().apply { moveTo(t.x, t.y); lineTo(t.x + 0.5f, t.y) }
            builder.addStroke(GestureDescription.StrokeDescription(path, 0, TAP_MS, false)); strokes++
        }
        if (strokes == 0) { post(Runnable { onDone(true, "nothing to send") }); return null }

        val gesture = try { builder.build() } catch (e: Exception) { held = null; return "cannot build gesture: ${e.message}" }
        val releasing = to == null
        val sentHeld = next
        val ex = endX; val ey = endY
        inFlight = true
        DebugMonitor.gesturesSent.incrementAndGet()
        DebugMonitor.gestureState = "Dispatched"
        svc.postMain(Runnable {
            fun failed(why: String) {
                inFlight = false; held = null
                DebugMonitor.gesturesFailed.incrementAndGet()
                DebugMonitor.gestureState = "Failed ($why)"
                DebugMonitor.error("continuous gesture failed: $why [${cmd.describe()}]")
                onDone(false, why)
            }
            try {
                val accepted = svc.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(g: GestureDescription?) {
                        inFlight = false
                        if (releasing) held = null else { held = sentHeld; lastX = ex; lastY = ey; if (closing) liftNow() }
                        DebugMonitor.gesturesOk.incrementAndGet()
                        DebugMonitor.gestureState = "Success"
                        onDone(true, "completed")
                    }
                    override fun onCancelled(g: GestureDescription?) { failed("cancelled by system") }
                }, null)
                if (!accepted) failed("dispatchGesture=false")
            } catch (e: Exception) { failed("exception: ${e.message}") }
        })
        return null
    }

    /** ปล่อยนิ้วจอยที่ค้างอยู่ (ถ้าหน้าเกมของเรายังอยู่หน้าสุด) ไม่งั้นแค่ลืมสถานะ ไม่ยิง gesture ไปโดนแอปอื่น */
    private fun liftNow() {
        val prev = held ?: return
        held = null
        if (ArenaGate.info == null) return
        val svc = BotAccessibilityService.instance ?: return
        try {
            val path = Path().apply { moveTo(lastX, lastY); lineTo(lastX + 0.5f, lastY) }
            val g = GestureDescription.Builder().addStroke(prev.continueStroke(path, 0, TAP_MS, false)).build()
            svc.postMain(Runnable {
                try { svc.dispatchGesture(g, null, null) } catch (e: Exception) { DebugMonitor.error("release gesture failed: ${e.message}", e) }
            })
        } catch (e: Exception) { DebugMonitor.error("release gesture build failed: ${e.message}", e) }
    }

    /** หยุดทั้งหมด: ถ้ายังมี gesture ค้างอยู่ จะยกนิ้วทันทีที่มันจบ (กันนิ้วจอยค้างหลังกดหยุดฉุกเฉิน) */
    override fun releaseAll() {
        closing = true
        if (!inFlight) liftNow()
    }
}
