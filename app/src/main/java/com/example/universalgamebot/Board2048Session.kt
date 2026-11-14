package com.example.universalgamebot

/** Fixed read-only target. This session has no action executor and grants no gesture permission. */
class Board2048Session(private val startedAt:Long) {
    companion object { const val TARGET="com.example.gamebot2048test" }
    enum class Access { WAIT, READ, STOP }
    private var entered=false
    private var stopped=false
    fun access(pkg:String?, now:Long):Access {
        if(stopped || now-startedAt>=120_000) { stopped=true; return Access.STOP }
        if(pkg==TARGET) { entered=true; return Access.READ }
        if(entered || now-startedAt>=10_000) { stopped=true; return Access.STOP }
        return Access.WAIT
    }
}

/** Only numeric results are retained in memory; no screenshots are persisted or uploaded. */
object Board2048Readout {
    data class Snapshot(val running:Boolean=false, val status:String="idle",val board:Board2048Reader.Board?=null,
        val at:Long=0, val frames:Int=0,val confirmed:Int=0, val step:Board2048SingleStep.Report?=null, val historyCount:Int=0, val historyTotal:Long=0)
    private val history=Board2048History()
    private var archivedCurrent:Snapshot?=null
    @Volatile var snapshot=Snapshot(); private set
    @Synchronized fun begin(singleStep:Boolean=false) { archivedCurrent=null; snapshot=Snapshot(running=true,status="waiting_target",step=if(singleStep) Board2048SingleStep.Report() else null,historyCount=history.size,historyTotal=history.total) }
    @Synchronized fun step(report:Board2048SingleStep.Report) { if(snapshot.running) snapshot=snapshot.copy(step=report) }
    @Synchronized fun frame(board:Board2048Reader.Board?,at:Long) {
        val old=snapshot
        if(!old.running) return
        snapshot=old.copy(status=if(board==null) "uncertain" else "stable",board=board ?: old.board,
            at=if(board==null) old.at else at,frames=old.frames+1,confirmed=old.confirmed+if(board!=null)1 else 0)
    }
    @Synchronized fun stop() { finish("stopped") }
    @Synchronized fun fail() {
        if(snapshot.running) finish("error") else snapshot=snapshot.copy(status="error")
    }
    private fun finish(status:String) {
        if(!snapshot.running) return // Stop, projection callback and onDestroy may all arrive.
        snapshot=snapshot.copy(running=false,status=status)
        if(snapshot.step!=null) history.record(snapshot)
        snapshot=snapshot.copy(historyCount=history.size,historyTotal=history.total)
        if(snapshot.step!=null) archivedCurrent=snapshot
    }
    @Synchronized fun report():String {
        if(history.size==0) return format(snapshot)
        val archived=history.entries
        val verified=archived.count { it.result.step?.phase==Board2048SingleStep.Phase.VERIFIED }
        val header="2048 SINGLE STEP HISTORY\nretained=${history.size}/${history.capacity} total=${history.total} dropped=${history.total-history.size} verifiedInRetained=$verified\nOldest first. Memory only; history resets when the app process exits.\n"
        val runs=archived.joinToString("\n\n") { "=== run #${it.number} ===\n${format(it.result)}" }
        val current=if(snapshot!=archivedCurrent) "\n\n=== current session / status (not archived) ===\n${format(snapshot)}" else ""
        return header+"\n"+runs+current
    }
    private fun format(s:Snapshot):String {
        val mode=if(s.step==null) "READ ONLY" else "SINGLE STEP"
        val step=s.step?.let { r -> "\nstep=${r.phase} direction=${r.direction} requests=${r.commands} commandCompleted=${r.commandCompleted}\ndetail=${r.detail}\nbefore=${r.before}\nexpectedBeforeSpawn=${r.expected}\nafter=${r.after}" } ?: ""
        return "2048 $mode\ntarget=${Board2048Session.TARGET}\nstatus=${s.status} running=${s.running}\nframes=${s.frames} confirmed=${s.confirmed} lastConfirmedAt=${s.at}\n${s.board?.rows() ?: "no confirmed board"}$step\nLast confirmed board; not guaranteed current after a change. ${if(s.step==null) "No gestures." else "At most one swipe per user request; no retries."} No cloud calls or screenshot files."
    }

}

/** Source timestamp uses the monotonic nanoTime clock, independent of callback scheduling. */
class Board2048FrameClock {
    private var previous=0L
    fun accept(timestampNs:Long,nowNs:Long,enteredNs:Long):Boolean {
        val newer=timestampNs>previous
        if(timestampNs>previous && timestampNs<=nowNs) previous=timestampNs
        return newer && timestampNs>0 && timestampNs>=enteredNs && nowNs-timestampNs in 0L..750_000_000L
    }
}
