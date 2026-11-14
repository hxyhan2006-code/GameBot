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
        val at:Long=0, val frames:Int=0,val confirmed:Int=0, val step:Board2048SingleStep.Report?=null)
    @Volatile var snapshot=Snapshot(); private set
    @Synchronized fun begin(singleStep:Boolean=false) { snapshot=Snapshot(running=true,status="waiting_target",step=if(singleStep) Board2048SingleStep.Report() else null) }
    @Synchronized fun step(report:Board2048SingleStep.Report) { if(snapshot.running) snapshot=snapshot.copy(step=report) }
    @Synchronized fun frame(board:Board2048Reader.Board?,at:Long) {
        val old=snapshot
        if(!old.running) return
        snapshot=old.copy(status=if(board==null) "uncertain" else "stable",board=board ?: old.board,
            at=if(board==null) old.at else at,frames=old.frames+1,confirmed=old.confirmed+if(board!=null)1 else 0)
    }
    @Synchronized fun stop() { snapshot=snapshot.copy(running=false,status="stopped") }
    @Synchronized fun fail() { snapshot=snapshot.copy(running=false,status="error") }
    fun report() = snapshot.let { s ->
        val mode=if(s.step==null) "READ ONLY" else "SINGLE STEP"
        val step=s.step?.let { r -> "\nstep=${r.phase} direction=${r.direction} requests=${r.commands} commandCompleted=${r.commandCompleted}\ndetail=${r.detail}\nbefore=${r.before}\nexpectedBeforeSpawn=${r.expected}\nafter=${r.after}" } ?: ""
        "2048 $mode\ntarget=${Board2048Session.TARGET}\nstatus=${s.status} running=${s.running}\nframes=${s.frames} confirmed=${s.confirmed} lastConfirmedAt=${s.at}\n${s.board?.rows() ?: "no confirmed board"}$step\nLast confirmed board; not guaranteed current after a change. ${if(s.step==null) "No gestures." else "At most one swipe per user request; no retries."} No cloud calls or screenshot files."
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
