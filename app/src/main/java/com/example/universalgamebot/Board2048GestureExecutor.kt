package com.example.universalgamebot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.WindowManager

/** Dedicated single-step dispatch. Does not relax ArenaGate or the internal-game executors. */
object Board2048GestureExecutor {
    fun execute(permit: Board2048SwipePermit, stillAuthorized: () -> Boolean, done: (Boolean, String) -> Unit) {
        val service = BotAccessibilityService.instance
        if (service == null) { done(false, "Accessibility disconnected"); return }
        service.postMain {
            try {
                val root = service.rootInActiveWindow
                val rect = Rect()
                root?.getBoundsInScreen(rect)
                val wm = service.getSystemService(WindowManager::class.java)
                val size = if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds else {
                    val m = android.util.DisplayMetrics()
                    @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
                    Rect(0, 0, m.widthPixels, m.heightPixels)
                }
                val windows=service.windows.map { w ->
                    val bounds=Rect(); w.getBoundsInScreen(bounds)
                    Board2048WindowGate.Window(w.id,w.type,w.layer,w.isActive,w.isFocused,
                        if(Build.VERSION.SDK_INT>=30) w.displayId else 0,
                        Box(bounds.left.toFloat(),bounds.top.toFloat(),bounds.right.toFloat(),bounds.bottom.toFloat()))
                }
                val readout = Board2048Readout.snapshot
                val current = readout.board
                val step = readout.step
                val ready = stillAuthorized() && !BotState.running &&
                    Board2048WindowGate.allows(windows,root?.windowId ?: -1,permit.request.bounds) &&
                    BotAccessibilityService.instance === service &&
                    !service.getSystemService(KeyguardManager::class.java).isKeyguardLocked &&
                    readout.running && readout.status == "stable" &&
                    step?.phase == Board2048SingleStep.Phase.DISPATCHED &&
                    step.direction == permit.request.move.direction && step.before == permit.request.move.before && current != null &&
                    current.cells == permit.request.move.before && Board2048SingleStep.sameBounds(current.bounds, permit.request.bounds)
                if (!permit.consume(root?.packageName?.toString(), System.nanoTime(), ready,
                        size.width(), size.height(), Box(rect.left.toFloat(),rect.top.toFloat(),rect.right.toFloat(),rect.bottom.toFloat()))) {
                    done(false, "scope, window occlusion, freshness, geometry or one-use permission rejected"); return@postMain
                }
                val b = permit.request.bounds
                val cx = b.centerX; val cy = b.centerY
                val dx = b.width * 0.42f; val dy = b.height * 0.42f
                val path = Path().apply {
                    when (permit.request.move.direction) {
                        Board2048Moves.Direction.LEFT -> { moveTo(cx+dx,cy); lineTo(cx-dx,cy) }
                        Board2048Moves.Direction.RIGHT -> { moveTo(cx-dx,cy); lineTo(cx+dx,cy) }
                        Board2048Moves.Direction.UP -> { moveTo(cx,cy+dy); lineTo(cx,cy-dy) }
                        Board2048Moves.Direction.DOWN -> { moveTo(cx,cy-dy); lineTo(cx,cy+dy) }
                    }
                }
                val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 180)).build()
                val accepted = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(g: GestureDescription?) { done(true, "completed") }
                    override fun onCancelled(g: GestureDescription?) { done(false, "cancelled by system") }
                }, null)
                if (!accepted) done(false, "dispatchGesture rejected")
            } catch (e: Exception) { done(false, "${e.javaClass.simpleName}: ${e.message}") }
        }
    }
}
