package com.example.universalgamebot

import kotlin.math.abs

/** One user request can emit at most one move. No retries, loop, or executor dependency. */
class Board2048SingleStep(private val startedAt: Long) {
    enum class Phase { WAITING, DISPATCHED, VERIFYING, VERIFIED, NO_LEGAL_MOVE, ERROR, STOPPED }
    data class Request(val move: Board2048Moves.Move, val bounds: Box, val sourceNs: Long)
    data class Report(val phase: Phase = Phase.WAITING, val direction: Board2048Moves.Direction? = null,
        val before: List<Int>? = null, val expected: List<Int>? = null, val after: List<Int>? = null,
        val commands: Int = 0, val commandCompleted: Boolean = false, val detail: String = "waiting for fresh stable pixels")
    @Volatile var report = Report(); private set
    private var request: Request? = null
    private var dispatchedAt = 0L
    private var barrierNs = Long.MAX_VALUE
    private var completedAt = 0L
    val terminal get() = report.phase in listOf(Phase.VERIFIED, Phase.NO_LEGAL_MOVE, Phase.ERROR, Phase.STOPPED)

    @Synchronized fun observe(board: Board2048Reader.Board?, sourceNs: Long, now: Long): Request? {
        tick(now)
        if (terminal || board == null) return null
        when (report.phase) {
            Phase.WAITING -> {
                val move = Board2048Moves.choose(board.cells)
                if (move == null) { report = report.copy(phase = Phase.NO_LEGAL_MOVE, detail = "no supported legal move"); return null }
                val next = Request(move, board.bounds, sourceNs)
                request = next; dispatchedAt = now
                report = Report(Phase.DISPATCHED, move.direction, move.before, move.shifted,
                    commands = 1, detail = "one move requested; awaiting gesture callback")
                return next
            }
            Phase.VERIFYING -> if (sourceNs > barrierNs) {
                val r = request!!
                if (!sameBounds(r.bounds, board.bounds)) {
                    report = report.copy(phase = Phase.ERROR, after = board.cells, detail = "board geometry changed")
                } else if (Board2048Moves.verifies(r.move, board.cells)) {
                    report = report.copy(phase = Phase.VERIFIED, after = board.cells, detail = "legal slide plus exactly one spawned 2 or 4")
                } else if (board.cells != r.move.before) {
                    report = report.copy(phase = Phase.ERROR, after = board.cells, detail = "stable result does not match one legal slide plus spawn")
                }
            }
            else -> {}
        }
        return null
    }
    @Synchronized fun commandResult(ok: Boolean, reason: String, now: Long, completedSourceNs: Long) {
        if (report.phase != Phase.DISPATCHED) return
        if (!ok) report = report.copy(phase = Phase.ERROR, detail = "command failed: $reason")
        else {
            completedAt = now; barrierNs = completedSourceNs
            report = report.copy(phase = Phase.VERIFYING, commandCompleted = true, detail = "gesture completed; awaiting fresh stable result")
        }
    }
    @Synchronized fun tick(now: Long) {
        val expired = when (report.phase) {
            Phase.WAITING -> now - startedAt > 10_000
            Phase.DISPATCHED -> now - dispatchedAt > 2500
            Phase.VERIFYING -> now - completedAt > 4000
            else -> false
        }
        if (expired) report = report.copy(phase = Phase.ERROR, detail = "timeout in ${report.phase}; no retry")
    }
    @Synchronized fun stop() { if (!terminal) report = report.copy(phase = Phase.STOPPED, detail = "stopped; no retry") }
    companion object {
        fun sameBounds(a: Box, b: Box) = listOf(abs(a.left-b.left), abs(a.top-b.top), abs(a.right-b.right), abs(a.bottom-b.bottom)).all { it <= 8f }
    }
}

/** Consumable, short-lived permission, rechecked on the actual dispatch thread. */
class Board2048SwipePermit(val request: Board2048SingleStep.Request, private val width: Int, private val height: Int) {
    private val available = java.util.concurrent.atomic.AtomicBoolean(true)
    fun revoke() { available.set(false) }
    fun consume(pkg: String?, nowNs: Long, active: Boolean, currentWidth: Int, currentHeight: Int, window: Box): Boolean {
        if (!available.compareAndSet(true, false)) return false
        val b = request.bounds
        val coordinates = listOf(b.left,b.top,b.right,b.bottom)
        return active && pkg == Board2048Session.TARGET && nowNs-request.sourceNs in 0..750_000_000L &&
            width == currentWidth && height == currentHeight && width > 0 && height > 0 && coordinates.all { it.isFinite() } &&
            b.width >= 384 && b.height >= 384 && abs(b.width-b.height) <= b.width*0.08f &&
            b.left >= 0 && b.top >= 0 && b.right <= width && b.bottom <= height &&
            b.left >= window.left && b.top >= window.top && b.right <= window.right && b.bottom <= window.bottom
    }
}

/** Conservative window occlusion gate; unavailable metadata never grants a swipe. */
object Board2048WindowGate {
    data class Window(val id:Int,val type:Int,val layer:Int,val active:Boolean,val focused:Boolean,val displayId:Int,val bounds:Box)
    fun allows(windows:List<Window>,rootWindowId:Int,board:Box):Boolean {
        val target=windows.singleOrNull { it.id==rootWindowId } ?: return false
        if(target.type!=1 || !target.active || !target.focused || target.displayId!=0) return false
        if(board.left<target.bounds.left || board.top<target.bounds.top || board.right>target.bounds.right || board.bottom>target.bounds.bottom) return false
        return windows.none { w ->
            w.id!=target.id && w.displayId==target.displayId && w.layer>=target.layer &&
                w.bounds.left<board.right && w.bounds.right>board.left && w.bounds.top<board.bottom && w.bounds.bottom>board.top
        }
    }
}
