package com.example.universalgamebot

/** Fixed read-only target. This session has no action executor and grants no gesture permission. */
class Board2048Session(private val startedAt:Long,private val durationMs:Long=120_000) {
    init { require(durationMs in 1..600_000) }
    companion object {
        const val TARGET="com.example.gamebot2048test"
        fun eventPackage(foreground:String?,announced:String?) = if(foreground==TARGET) announced else foreground
    }
    enum class Access { WAIT, READ, STOP }
    private var entered=false
    private var stopped=false
    @Synchronized fun access(pkg:String?, now:Long):Access {
        if(stopped || now-startedAt>=durationMs) { stopped=true; return Access.STOP }
        if(pkg==TARGET) { entered=true; return Access.READ }
        if(entered || now-startedAt>=10_000) { stopped=true; return Access.STOP }
        return Access.WAIT
    }
}

/** Only numeric results are retained in memory; no screenshots are persisted or uploaded. */
object Board2048Readout {
    data class Snapshot(val running:Boolean=false, val status:String="idle",val board:Board2048Reader.Board?=null,
        val at:Long=0, val frames:Int=0,val confirmed:Int=0, val step:Board2048SingleStep.Report?=null, val historyCount:Int=0, val historyTotal:Long=0, val batch:Board2048Batch.Report?=null)
    private val history=Board2048History()
    private var archivedCurrent:Snapshot?=null
    @Volatile var snapshot=Snapshot(); private set
    @Synchronized fun begin(singleStep:Boolean=false,batch:Board2048Batch.Report?=null) { archivedCurrent=null; snapshot=Snapshot(running=true,status="waiting_target",step=if(singleStep) Board2048SingleStep.Report() else null,historyCount=history.size,historyTotal=history.total,batch=batch) }
    @Synchronized fun step(report:Board2048SingleStep.Report) { if(snapshot.running) snapshot=snapshot.copy(step=report) }
    @Synchronized fun batch(report:Board2048Batch.Report) { if(snapshot.running) snapshot=snapshot.copy(batch=report) }
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
        val verified=archived.count { if(it.result.batch!=null) it.result.batch.phase==Board2048Batch.Phase.COMPLETE else it.result.step?.phase==Board2048SingleStep.Phase.VERIFIED }
        val header="2048 RUN HISTORY\nretained=${history.size}/${history.capacity} total=${history.total} dropped=${history.total-history.size} verifiedInRetained=$verified\nOldest first. Memory only; history resets when the app process exits.\n"
        val runs=archived.joinToString("\n\n") { "=== run #${it.number} ===\n${format(it.result,fullBatch=it.number==archived.last().number)}" }
        val current=if(snapshot!=archivedCurrent) "\n\n=== current session / status (not archived) ===\n${format(snapshot)}" else ""
        return header+"\n"+runs+current
    }
    private fun format(s:Snapshot,fullBatch:Boolean=true):String {
        val mode=if(s.batch!=null) "BATCH" else if(s.step==null) "READ ONLY" else "SINGLE STEP"
        val step=s.step?.let { r -> "\nstep=${r.phase} direction=${r.direction} requests=${r.commands} commandCompleted=${r.commandCompleted}\ndetail=${r.detail}\nbefore=${r.before}\nexpectedBeforeSpawn=${r.expected}\nafter=${r.after}" } ?: ""
        val batch=s.batch?.let { b ->
            val summary="\nbatch=${b.phase} limit=${b.limit} verified=${b.verified} requests=${b.requests} detail=${b.detail}"
            val evidence=if(fullBatch) b.turns.mapIndexed { i,r ->
                "\nturn #${i+1}: ${r.phase} direction=${r.direction} requests=${r.commands} commandCompleted=${r.commandCompleted} detail=${r.detail}\nbefore=${r.before}\nexpectedBeforeSpawn=${r.expected}\nafter=${r.after}"
            }.joinToString("") else "\nOlder batch: summary only; full turn evidence is included for the latest run."
            summary+evidence
        } ?: ""
        return "2048 $mode\ntarget=${Board2048Session.TARGET}\nstatus=${s.status} running=${s.running}\nframes=${s.frames} confirmed=${s.confirmed} lastConfirmedAt=${s.at}\n${s.board?.rows() ?: "no confirmed board"}$step$batch\nLast confirmed board; not guaranteed current after a change. ${if(s.step==null) "No gestures." else if(s.batch!=null) "Bounded batch; verify every move before the next; no retries." else "At most one swipe per user request; no retries."} No cloud calls or screenshot files."
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
