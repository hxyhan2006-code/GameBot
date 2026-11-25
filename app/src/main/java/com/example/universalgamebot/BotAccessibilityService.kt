package com.example.universalgamebot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Region
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import java.util.concurrent.atomic.AtomicBoolean

class BotAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: BotAccessibilityService? = null
        @Volatile internal var efootballScopeObserver: ((String?,String?) -> Unit)? = null
        @Volatile internal var readOnlyScopeObserver: ((String?,String?) -> Unit)? = null
        @Volatile internal var board2048ScopeObserver: ((String?) -> Unit)? = null
    }

    private val main = Handler(Looper.getMainLooper())
    private val gestureSlot = GestureSlot()
    private var clicker: ClickerOverlay? = null

    fun showClicker(): Boolean {
        if (clicker != null) return true
        val stoppedCapture = listOf(
            CaptureService::class.java,
            EFootballCaptureService::class.java,
            Board2048CaptureService::class.java,
            ReadOnlyGameCaptureService::class.java
        ).map { stopService(Intent(this, it)) }.any { it }
        if (stoppedCapture || !gestureSlot.enterClicker()) return false
        val overlay = ClickerOverlay(this)
        clicker = overlay
        if (overlay.open()) return true
        closeClicker()
        return false
    }

    fun closeClicker() {
        val overlay = clicker ?: return
        clicker = null
        overlay.close { gestureSlot.leaveClicker() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        clicker?.displayChanged()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        closeClicker()
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    private fun AccessibilityWindowInfo.appPackage(): String? {
        val node = root
        val name = node?.packageName?.toString()
        @Suppress("DEPRECATION")
        node?.recycle()
        return name
    }

    internal fun focusedApplication(): String? = try {
        windows.firstOrNull {
            it.isFocused && it.type == AccessibilityWindowInfo.TYPE_APPLICATION
        }?.appPackage()
    } catch (e: Exception) {
        null
    }

    internal enum class ClickerWindowCheck { ALLOWED, FOCUS_CHANGED, OUTSIDE_GAME, COVERED }

    internal fun checkClickerWindow(expected: String, x: Int, y: Int): ClickerWindowCheck {
        try {
            val visible = windows
            val focus = visible.firstOrNull {
                it.isFocused && it.type == AccessibilityWindowInfo.TYPE_APPLICATION
            } ?: return ClickerWindowCheck.FOCUS_CHANGED
            if (focus.appPackage() != expected) return ClickerWindowCheck.FOCUS_CHANGED

            val bounds = Rect()
            val region = Region()
            fun contains(window: AccessibilityWindowInfo): Boolean {
                if (Build.VERSION.SDK_INT >= 30) {
                    window.getRegionInScreen(region)
                    return region.contains(x, y)
                }
                window.getBoundsInScreen(bounds)
                return bounds.contains(x, y)
            }
            if (!contains(focus)) return ClickerWindowCheck.OUTSIDE_GAME
            if (visible.any { other ->
                    other.layer > focus.layer && contains(other) &&
                        !(other.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY &&
                            other.title?.toString() == ClickerOverlay.EDIT_MARKER_TITLE &&
                            other.appPackage() == packageName)
                }) return ClickerWindowCheck.COVERED
            return ClickerWindowCheck.ALLOWED
        } catch (e: Exception) {
            return ClickerWindowCheck.FOCUS_CHANGED
        }
    }

    internal fun submitClicker(gesture: GestureDescription, onDone: (Boolean) -> Unit): Boolean =
        dispatchTracked(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) = onDone(true)
            override fun onCancelled(g: GestureDescription?) = onDone(false)
        }, autoClicker = true)

    internal fun dispatchVisionGesture(gesture: GestureDescription, callback: GestureResultCallback): Boolean =
        dispatchTracked(gesture, callback, autoClicker = false)

    private fun dispatchTracked(
        gesture: GestureDescription,
        callback: GestureResultCallback,
        autoClicker: Boolean
    ): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (instance !== this || !gestureSlot.acquire(autoClicker)) return false
        var pending = true
        lateinit var timeout: Runnable
        fun finish(ok: Boolean, notify: Boolean = true) {
            if (!pending) return
            pending = false
            main.removeCallbacks(timeout)
            gestureSlot.release(autoClicker)
            if (notify) {
                if (ok) callback.onCompleted(gesture) else callback.onCancelled(gesture)
            }
        }
        timeout = Runnable {
            DebugMonitor.skip("gesture callback timed out")
            finish(false)
        }
        val accepted = try {
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) = finish(true)
                override fun onCancelled(g: GestureDescription?) = finish(false)
            }, main)
        } catch (e: Exception) {
            DebugMonitor.error("dispatchGesture failed: ${e.message}", e)
            false
        }
        if (accepted) {
            val duration = (0 until gesture.strokeCount).maxOf { i ->
                gesture.getStroke(i).let { it.startTime + it.duration }
            }
            main.postDelayed(timeout, duration + 3000L)
        } else finish(false, notify = false)
        return accepted
    }
    private val efootballWindowEvidence=EFootballForeground()
    private var efootballBadge:EFootballStatusBadge?=null
    private val pending = AtomicBoolean(false)
    @Volatile private var pendingSince = 0L

    override fun onServiceConnected() {
        instance = this
        ErrorLog.init(this)
        val canRead = (serviceInfo?.capabilities ?: 0) and AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT != 0
        DebugMonitor.log("Accessibility connected, canRetrieveContent=$canRead")
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        clicker?.windowChanged()
        val badgeEvent=event!=null && isOwnBadgeWindow(event.windowId) && event.packageName?.toString()==packageName
        if(event?.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !badgeEvent)
            efootballWindowEvidence.announced(event.packageName?.toString(),event.windowId)
        val observer=board2048ScopeObserver
        val readOnly=readOnlyScopeObserver
        if(observer==null && readOnly==null && efootballScopeObserver==null) return
        if(event?.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Use the announced window too: a brief focus loss must remain latched even if focus returned.
            val foreground=activePackage()
            val announced=event.packageName?.toString()
            observer?.invoke(Board2048Session.eventPackage(foreground,announced))
            readOnly?.invoke(foreground,announced)
            val ef=efootballForeground()
            efootballScopeObserver?.invoke(ef,if(badgeEvent)ef else announced)
        }
    }
    override fun onInterrupt() { clicker?.stop(); efootballWindowEvidence.reset(); board2048ScopeObserver?.invoke(null); readOnlyScopeObserver?.invoke(null,null); efootballScopeObserver?.invoke(null,null) }
    override fun onDestroy() { closeClicker(); efootballBadge?.close();efootballBadge=null;efootballWindowEvidence.reset(); board2048ScopeObserver?.invoke(null); readOnlyScopeObserver?.invoke(null,null); efootballScopeObserver?.invoke(null,null); instance = null; super.onDestroy() }

    fun postMain(r: Runnable) { main.post(r) }

    /** ปิดบริการตัวเอง (API 24+): ใช้ตอนเลิกทดสอบ เพราะแอปการเงินบางแอป (เช่น Dime) ไม่ยอมให้ใช้งานถ้ามี Accessibility ของแอปนอก Store เปิดอยู่ */
    fun turnOff() {
        DebugMonitor.log("Accessibility turned off from the app")
        closeClicker()
        efootballBadge?.close();efootballBadge=null
        efootballWindowEvidence.reset()
        board2048ScopeObserver?.invoke(null); readOnlyScopeObserver?.invoke(null,null); efootballScopeObserver?.invoke(null,null)
        instance = null
        try { disableSelf() } catch (e: Exception) { DebugMonitor.error("disableSelf failed: ${e.message}", e) }
    }

    /** package ของหน้าต่างที่ active (null ถ้าอ่านไม่ได้) ใช้เป็นข้อมูลประกอบ ไม่ใช่เงื่อนไขหลัก */
    fun activePackage(): String? = try { rootInActiveWindow?.packageName?.toString() } catch (e: Exception) { null }

    /** Surface-based games can temporarily have no root. Revalidate the focused OS window ID. */
    internal fun showEfootballStatus(){
        if(efootballBadge==null)efootballBadge=EFootballStatusBadge(this)
        efootballBadge!!.show()
    }
    private fun isOwnBadgeWindow(id:Int):Boolean = efootballBadge?.ownsWindow(id)==true && try{
        windows.any{it.id==id && it.type==AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY && !it.isFocused}
    }catch(e:Exception){false}
    fun efootballForeground():String? {
        return try {
            val root=rootInActiveWindow
            val pkg=root?.packageName?.toString();val rootId=root?.windowId ?: -1
            @Suppress("DEPRECATION") root?.recycle()
            val badgeRoot=pkg==packageName && efootballBadge?.ownsWindow(rootId)==true
            if(pkg!=null && !badgeRoot)return efootballWindowEvidence.resolve(pkg,emptyList())
            val evidence=windows.map{w->
                val node=w.root;val name=node?.packageName?.toString()
                @Suppress("DEPRECATION") node?.recycle()
                EFootballWindow(w.id,w.isFocused,w.isActive,w.type==AccessibilityWindowInfo.TYPE_APPLICATION,name,
                    w.type==AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY)
            }
            val badgeId=evidence.singleOrNull{efootballBadge?.ownsWindow(it.id)==true}?.id ?: -1
            efootballWindowEvidence.resolveWithOwnedOverlay(pkg,rootId,evidence,badgeId,packageName)
        }catch(e:Exception){efootballWindowEvidence.resolve(null,emptyList())}
    }
    fun efootballFocusDescription()=efootballWindowEvidence.diagnostic

    fun tap(x: Float, y: Float, source: String, onDone: ((Boolean, String) -> Unit)? = null): Boolean {
        val path = Path().apply { moveTo(x, y); lineTo(x + 0.5f, y + 0.5f) }
        return perform(path, 60, "$source tap (${x.toInt()},${y.toInt()})", source, onDone)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long, source: String, onDone: ((Boolean, String) -> Unit)? = null): Boolean {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        return perform(path, durationMs, "$source swipe (${x1.toInt()},${y1.toInt()})->(${x2.toInt()},${y2.toInt()})", source, onDone)
    }

    /**
     * ส่งท่าทางและบันทึกผลจริงจาก GestureResultCallback (completed/cancelled)
     * คืน true ถ้าระบบรับคำสั่งแล้ว (ผลจริงมาทาง onDone / DebugMonitor.gestureState) คืน false ถ้ายังมีท่าทางค้างอยู่
     * เรียกได้จากทุก thread: dispatchGesture ถูกส่งผ่าน main thread
     */
    private fun perform(path: Path, durationMs: Long, desc: String, source: String, onDone: ((Boolean, String) -> Unit)?): Boolean {
        if (!pending.compareAndSet(false, true)) {
            if (System.currentTimeMillis() - pendingSince < 3000) { DebugMonitor.skip("gesture still pending"); return false }
            pending.set(true) // ค้างนานผิดปกติ ปล่อยให้ส่งใหม่ได้
        }
        pendingSince = System.currentTimeMillis()
        DebugMonitor.gesturesSent.incrementAndGet()
        DebugMonitor.gestureState = "Dispatched"
        main.post {
            try {
                val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, durationMs)).build()
                val accepted = dispatchVisionGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(g: GestureDescription?) {
                        pending.set(false)
                        DebugMonitor.gesturesOk.incrementAndGet()
                        DebugMonitor.gestureState = "Success"
                        DebugMonitor.log("gesture OK [$desc]")
                        onDone?.invoke(true, "completed")
                    }
                    override fun onCancelled(g: GestureDescription?) {
                        pending.set(false)
                        DebugMonitor.gesturesFailed.incrementAndGet()
                        DebugMonitor.gestureState = "Failed (cancelled)"
                        DebugMonitor.error("gesture cancelled [$desc]")
                        onDone?.invoke(false, "cancelled by system")
                    }
                })
                if (!accepted) {
                    pending.set(false)
                    DebugMonitor.gesturesFailed.incrementAndGet()
                    DebugMonitor.gestureState = "Failed (dispatchGesture=false)"
                    DebugMonitor.error("dispatchGesture returned false [$desc]")
                    onDone?.invoke(false, "dispatchGesture=false")
                }
            } catch (e: Exception) {
                pending.set(false)
                DebugMonitor.gesturesFailed.incrementAndGet()
                DebugMonitor.gestureState = "Failed (exception)"
                DebugMonitor.error("dispatchGesture exception: ${e.message}", e)
                onDone?.invoke(false, "exception: ${e.message}")
            }
        }
        return true
    }
}
