package com.example.universalgamebot

/** Verified bounded run. Evidence is capped independently of lifetime counters. */
class Board2048Batch(private val startedAt:Long,val limit:Int) {
    enum class Phase { RUNNING, COMPLETE, ERROR, STOPPED, NO_LEGAL_MOVE, WON, GAME_OVER }
    enum class EndEvidence { NONE, WIN_LABEL, GAME_OVER_LABEL, OBSERVED_2048, NO_LEGAL_MOVES }
    data class Report(val limit:Int,val verified:Int=0,val requests:Int=0,val phase:Phase=Phase.RUNNING,
        val turns:List<Board2048SingleStep.Report> = emptyList(),val detail:String="waiting for fresh stable pixels",
        val highestTile:Int=0,val mergeScore:Int=0,val elapsedMs:Long=0,val turnsDropped:Int=0,
        val maxPlanningMs:Long=0,val evidence:EndEvidence=EndEvidence.NONE)
    companion object {
        val LIMITS=listOf(10,30,100)
        const val UNTIL_END=3000
        const val MAX_DURATION_MS=1_800_000L
        fun supported(limit:Int)=limit in LIMITS || limit==UNTIL_END
        fun durationMs(limit:Int):Long {
            require(supported(limit))
            return if(limit==UNTIL_END) MAX_DURATION_MS else minOf(600_000L,10_000L+limit*6000L)
        }
    }
    init { require(supported(limit)) }
    @Volatile var current=Board2048SingleStep(startedAt); private set
    @Volatile var report=Report(limit); private set
    private val turns=ArrayDeque<Board2048SingleStep.Report>()
    private var evidence=EndEvidence.NONE
    private var recorded=0; private var verified=0; private var requests=0
    private var highest=0; private var score=0; private var lastNow=startedAt; private var maxPlanningMs=0L
    private var afterSourceNs=0L
    private var nextBoard:Board2048Reader.Board?=null
    val terminal get()=report.phase!=Phase.RUNNING

    @Synchronized fun observe(board:Board2048Reader.Board?,sourceNs:Long,now:Long):Board2048SingleStep.Request? {
        if(terminal || sourceNs<=afterSourceNs) return null
        lastNow=now
        if(recorded>0 && current.report.phase==Board2048SingleStep.Phase.WAITING && (board==null || board.cells!=nextBoard?.cells || !Board2048SingleStep.sameBounds(board.bounds,nextBoard!!.bounds))) {
            current.stop(); finish(Phase.STOPPED,"uncertain or changed board between moves; no retry"); return null
        }
        if(board!=null) highest=maxOf(highest,board.cells.max())
        if(board!=null && current.report.phase==Board2048SingleStep.Phase.WAITING && highest>=2048) {
            evidence=EndEvidence.OBSERVED_2048
            current.stop(); finish(Phase.WON,"2048 tile on stable observed board"); return null
        }
        val request=current.observe(board,sourceNs,now)
        maxPlanningMs=maxOf(maxPlanningMs,current.report.planningMs)
        if(current.report.phase==Board2048SingleStep.Phase.VERIFIED) nextBoard=board
        settle(now,sourceNs)
        return request
    }
    @Synchronized fun commandResult(step:Board2048SingleStep,ok:Boolean,reason:String,now:Long,sourceNs:Long) {
        if(terminal || current!==step) return
        lastNow=now; current.commandResult(ok,reason,now,sourceNs); settle(now,sourceNs)
    }
    @Synchronized fun tick(now:Long) {
        if(terminal) return
        lastNow=now; current.tick(now); settle(now,afterSourceNs)
    }
    @Synchronized fun stop(reason:String="stopped; no further moves") {
        if(terminal) return
        current.stop(); finish(Phase.STOPPED,reason)
    }
    @Synchronized fun abort(reason:String) {
        if(terminal) return
        current.stop(); finish(Phase.ERROR,reason)
    }
    @Synchronized fun endScreen(won:Boolean,now:Long) {
        if(terminal) return
        lastNow=now; current.stop()
        evidence=if(won) EndEvidence.WIN_LABEL else EndEvidence.GAME_OVER_LABEL
        finish(if(won) Phase.WON else Phase.GAME_OVER,"stable ${if(won) "win" else "game-over"} label and overlay observed; final board is not verified${if(requests+current.report.commands==0) "; no move requested in this run" else ""}")
    }
    private fun remember() {
        val r=current.report
        turns.addLast(r); recorded++; requests+=r.commands
        if(r.phase==Board2048SingleStep.Phase.VERIFIED) { verified++; score+=r.scoreGain }
        if(turns.size>100) turns.removeFirst()
    }
    private fun settle(now:Long,sourceNs:Long) {
        when(current.report.phase) {
            Board2048SingleStep.Phase.VERIFIED -> {
                remember()
                if(highest>=2048) { evidence=EndEvidence.OBSERVED_2048; publish(Phase.WON,"2048 tile verified") }
                else if(verified==limit) publish(Phase.COMPLETE,"requested limit reached")
                else {
                    afterSourceNs=sourceNs; current=Board2048SingleStep(now)
                    publish(Phase.RUNNING,"waiting for a newer stable frame before the next move")
                }
            }
            Board2048SingleStep.Phase.ERROR -> finish(Phase.ERROR,current.report.detail)
            Board2048SingleStep.Phase.NO_LEGAL_MOVE -> { evidence=EndEvidence.NO_LEGAL_MOVES; finish(Phase.GAME_OVER,"no legal supported move on observed board") }
            Board2048SingleStep.Phase.STOPPED -> finish(Phase.STOPPED,current.report.detail)
            else -> publish(Phase.RUNNING,current.report.detail)
        }
    }
    private fun publish(phase:Phase,detail:String) {
        val pending=if(phase==Phase.RUNNING)current.report.commands else 0
        report=Report(limit,verified,requests+pending,phase,turns.toList(),detail,highest,score,
            (lastNow-startedAt).coerceAtLeast(0),recorded-turns.size,maxPlanningMs,evidence)
    }
    private fun finish(phase:Phase,detail:String) { remember(); publish(phase,detail) }
}
