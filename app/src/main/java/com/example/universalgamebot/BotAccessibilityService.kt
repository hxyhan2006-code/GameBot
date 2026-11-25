package com.example.universalgamebot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.content.Intent

class BotAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: BotAccessibilityService? = null
    }

    private val main = Handler(Looper.getMainLooper())
    private val gestures = GestureSlot()
    private var clicker: ClickerOverlay? = null

    fun showClicker(): Boolean {
        if (clicker != null) return true
        stopService(Intent(this, CaptureService::class.java))
        if (!gestures.enterClicker()) return false
        val overlay = ClickerOverlay(this)
        clicker = overlay
        if (overlay.open()) return true
        closeClicker()
        return false
    }

    fun closeClicker() {
        val overlay = clicker ?: return
        clicker = null
        overlay.close { gestures.leaveClicker() }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        clicker?.displayChanged()
    }

    override fun onServiceConnected() {
        instance = this
        ErrorLog.init(this)
        val canRead = (serviceInfo?.capabilities ?: 0) and AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT != 0
        DebugMonitor.log("Accessibility connected, canRetrieveContent=$canRead")
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { clicker?.windowChanged() }
    override fun onInterrupt() { clicker?.stop() }
    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun disconnect() {
        if (instance === this) instance = null
        closeClicker()
    }

    /** ปิดบริการตัวเอง (API 24+): ใช้ตอนเลิกทดสอบ เพราะแอปการเงินบางแอป (เช่น Dime) ไม่ยอมให้ใช้งานถ้ามี Accessibility ของแอปนอก Store เปิดอยู่ */
    fun turnOff() {
        DebugMonitor.log("Accessibility turned off from the app")
        disconnect()
        try { disableSelf() } catch (e: Exception) { DebugMonitor.error("disableSelf failed: ${e.message}", e) }
    }

    /** package ของหน้าต่างที่ active (null ถ้าอ่านไม่ได้) ใช้เป็นข้อมูลประกอบ ไม่ใช่เงื่อนไขหลัก */
    fun activePackage(): String? = try { rootInActiveWindow?.packageName?.toString() } catch (e: Exception) { null }

    internal fun focusedApplication(): String? = try {
        windows.firstOrNull { it.isFocused }?.takeIf {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION
        }?.root?.packageName?.toString()
    } catch (e: Exception) {
        null
    }

    fun tap(x: Float, y: Float, source: String, onDone: ((Boolean, String) -> Unit)? = null): Boolean {
        val path = Path().apply { moveTo(x, y); lineTo(x + 0.5f, y + 0.5f) }
        return perform(path, 60, "$source tap (${x.toInt()},${y.toInt()})", source, onDone)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long, source: String, onDone: ((Boolean, String) -> Unit)? = null): Boolean {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        return perform(path, durationMs, "$source swipe (${x1.toInt()},${y1.toInt()})->(${x2.toInt()},${y2.toInt()})", source, onDone)
    }

    private fun perform(path: Path, durationMs: Long, desc: String, source: String, onDone: ((Boolean, String) -> Unit)?): Boolean {
        val arena = ArenaGate.info
        val gesture = try {
            GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, durationMs)).build()
        } catch (e: Exception) {
            DebugMonitor.error("Cannot build gesture [$desc]", e)
            return false
        }
        val accepted = submit(gesture, valid = {
            arena != null && ArenaGate.info?.rect == arena.rect &&
                (source != "bot" || !EmergencyStop.triggered) &&
                activePackage().let { it == null || it == packageName }
        }) { ok, reason ->
            if (ok) DebugMonitor.log("gesture OK [$desc]")
            else DebugMonitor.error("gesture failed [$desc]: $reason")
            onDone?.invoke(ok, reason)
        }
        if (!accepted) DebugMonitor.skip("gesture still pending or clicker active")
        return accepted
    }

    fun submit(
        gesture: GestureDescription,
        autoClicker: Boolean = false,
        valid: () -> Boolean = { true },
        onDone: (Boolean, String) -> Unit
    ): Boolean {
        if (instance !== this || !gestures.acquire(autoClicker)) return false
        if (!autoClicker) {
            DebugMonitor.gesturesSent.incrementAndGet()
            DebugMonitor.gestureState = "Dispatched"
        }
        val dispatch = Runnable {
            var finished = false
            lateinit var watchdog: Runnable
            fun finish(ok: Boolean, reason: String) {
                if (finished) return
                finished = true
                main.removeCallbacks(watchdog)
                gestures.release()
                if (!autoClicker) {
                    if (ok) DebugMonitor.gesturesOk.incrementAndGet()
                    else DebugMonitor.gesturesFailed.incrementAndGet()
                    DebugMonitor.gestureState = if (ok) "Success" else "Failed ($reason)"
                }
                onDone(ok, reason)
            }
            watchdog = Runnable { finish(false, "gesture callback timed out") }
            if (instance !== this || !valid()) {
                finish(false, "no longer active")
                return@Runnable
            }
            val accepted = try {
                dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(g: GestureDescription?) { finish(true, "completed") }
                    override fun onCancelled(g: GestureDescription?) { finish(false, "cancelled by system") }
                }, main)
            } catch (e: Exception) {
                DebugMonitor.error("dispatchGesture failed: ${e.message}", e)
                false
            }
            if (accepted) {
                val duration = (0 until gesture.strokeCount).maxOf { i ->
                    gesture.getStroke(i).let { it.startTime + it.duration }
                }
                main.postDelayed(watchdog, duration + 3000L)
            } else finish(false, "dispatchGesture=false")
        }
        if (autoClicker && Looper.myLooper() == Looper.getMainLooper()) dispatch.run()
        else main.post(dispatch)
        return true
    }
}
