package com.example.universalgamebot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicBoolean

class BotAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: BotAccessibilityService? = null
    }

    private val main = Handler(Looper.getMainLooper())
    private val pending = AtomicBoolean(false)
    @Volatile private var pendingSince = 0L

    override fun onServiceConnected() {
        instance = this
        DebugMonitor.log("Accessibility connected, canRetrieveContent=${serviceInfo?.capabilities?.and(android.accessibilityservice.AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT) != 0}")
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { instance = null; super.onDestroy() }

    /** package ของหน้าต่างที่ active (null ถ้าอ่านไม่ได้) ใช้เป็นข้อมูลประกอบ ไม่ใช่เงื่อนไขหลัก */
    fun activePackage(): String? = try { rootInActiveWindow?.packageName?.toString() } catch (e: Exception) { null }

    /**
     * ส่งการแตะ และบันทึกผลจริงจาก GestureResultCallback (completed/cancelled)
     * คืน true ถ้าระบบรับคำสั่งแล้ว (ยังไม่ใช่ว่าแตะสำเร็จ ผลจริงดูที่ DebugMonitor.gestureState)
     * เรียกได้จากทุก thread: dispatchGesture ถูกส่งผ่าน main thread
     */
    fun tap(x: Float, y: Float, source: String): Boolean {
        if (pending.get() && System.currentTimeMillis() - pendingSince < 1500) {
            DebugMonitor.skip("gesture still pending")
            return false
        }
        pending.set(true); pendingSince = System.currentTimeMillis()
        DebugMonitor.gesturesSent.incrementAndGet()
        DebugMonitor.gestureState = "Dispatched"
        main.post {
            try {
                val path = Path().apply { moveTo(x, y); lineTo(x + 0.5f, y + 0.5f) }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 60)).build()
                val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(g: GestureDescription?) {
                        pending.set(false)
                        DebugMonitor.gesturesOk.incrementAndGet()
                        DebugMonitor.gestureState = "Success"
                        DebugMonitor.log("gesture OK [$source] (${x.toInt()},${y.toInt()})")
                    }
                    override fun onCancelled(g: GestureDescription?) {
                        pending.set(false)
                        DebugMonitor.gesturesFailed.incrementAndGet()
                        DebugMonitor.gestureState = "Failed (cancelled)"
                        DebugMonitor.error("gesture cancelled [$source] (${x.toInt()},${y.toInt()})")
                    }
                }, null)
                if (!accepted) {
                    pending.set(false)
                    DebugMonitor.gesturesFailed.incrementAndGet()
                    DebugMonitor.gestureState = "Failed (dispatchGesture=false)"
                    DebugMonitor.error("dispatchGesture returned false [$source]")
                }
            } catch (e: Exception) {
                pending.set(false)
                DebugMonitor.gesturesFailed.incrementAndGet()
                DebugMonitor.gestureState = "Failed (exception)"
                DebugMonitor.error("dispatchGesture exception: ${e.message}", e)
            }
        }
        return true
    }
}
