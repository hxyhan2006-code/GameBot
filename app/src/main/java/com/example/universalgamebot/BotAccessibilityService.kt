package com.example.universalgamebot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
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
    override fun onInterrupt() { efootballWindowEvidence.reset(); board2048ScopeObserver?.invoke(null); readOnlyScopeObserver?.invoke(null,null); efootballScopeObserver?.invoke(null,null) }
    override fun onDestroy() { efootballBadge?.close();efootballBadge=null;efootballWindowEvidence.reset(); board2048ScopeObserver?.invoke(null); readOnlyScopeObserver?.invoke(null,null); efootballScopeObserver?.invoke(null,null); instance = null; super.onDestroy() }

    fun postMain(r: Runnable) { main.post(r) }

    /** ปิดบริการตัวเอง (API 24+): ใช้ตอนเลิกทดสอบ เพราะแอปการเงินบางแอป (เช่น Dime) ไม่ยอมให้ใช้งานถ้ามี Accessibility ของแอปนอก Store เปิดอยู่ */
    fun turnOff() {
        DebugMonitor.log("Accessibility turned off from the app")
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
                val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
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
                }, null)
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
