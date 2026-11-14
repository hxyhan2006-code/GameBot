package com.example.universalgamebot

/** Bounded sequence of independent one-use steps. Only VERIFIED can authorize the next step. */
class Board2048Batch(startedAt:Long,val limit:Int) {
    enum class Phase { RUNNING, COMPLETE, ERROR, STOPPED, NO_LEGAL_MOVE }
    data class Report(val limit:Int,val verified:Int=0,val requests:Int=0,val phase:Phase=Phase.RUNNING,
        val turns:List<Board2048SingleStep.Report> = emptyList(),val detail:String="waiting for fresh stable pixels")
    companion object {
        val LIMITS=listOf(10,30,100)
        fun durationMs(limit:Int):Long { require(limit in LIMITS); return minOf(600_000L,10_000L+limit*6000L) }
    }
    init { require(limit in LIMITS) }
    @Volatile var current=Board2048SingleStep(startedAt); private set
    @Volatile var report=Report(limit); private set
    private val turns=mutableListOf<Board2048SingleStep.Report>()
    private var afterSourceNs=0L
    private var nextBoard:Board2048Reader.Board?=null
    val terminal get()=report.phase!=Phase.RUNNING

    @Synchronized fun observe(board:Board2048Reader.Board?,sourceNs:Long,now:Long):Board2048SingleStep.Request? {
        if(terminal) return null
        if(sourceNs<=afterSourceNs) return null
        if(turns.isNotEmpty() && current.report.phase==Board2048SingleStep.Phase.WAITING && (board==null || board.cells!=nextBoard?.cells || !Board2048SingleStep.sameBounds(board.bounds,nextBoard!!.bounds))) {
            current.stop(); finish(Phase.STOPPED,"uncertain or changed board between moves; no retry"); return null
        }
        val request=current.observe(board,sourceNs,now)
        if(current.report.phase==Board2048SingleStep.Phase.VERIFIED) nextBoard=board
        settle(now,sourceNs)
        return request
    }
    @Synchronized fun commandResult(step:Board2048SingleStep,ok:Boolean,reason:String,now:Long,sourceNs:Long) {
        if(terminal || current!==step) return
        current.commandResult(ok,reason,now,sourceNs)
        settle(now,sourceNs)
    }
    @Synchronized fun tick(now:Long) {
        if(terminal) return
        current.tick(now); settle(now,afterSourceNs)
    }
    @Synchronized fun stop(reason:String="stopped; no further moves") {
        if(terminal) return
        current.stop(); finish(Phase.STOPPED,reason)
    }
    @Synchronized fun abort(reason:String) {
        if(terminal) return
        current.stop(); finish(Phase.ERROR,reason)
    }
    private fun settle(now:Long,sourceNs:Long) {
        when(current.report.phase) {
            Board2048SingleStep.Phase.VERIFIED -> {
                turns.add(current.report)
                if(turns.size==limit) {
                    report=Report(limit,turns.size,turns.sumOf { it.commands },Phase.COMPLETE,turns.toList(),"requested limit reached")
                } else {
                    afterSourceNs=sourceNs
                    current=Board2048SingleStep(now)
                    update("waiting for a newer stable frame before the next move")
                }
            }
            Board2048SingleStep.Phase.ERROR -> finish(Phase.ERROR,current.report.detail)
            Board2048SingleStep.Phase.NO_LEGAL_MOVE -> finish(Phase.NO_LEGAL_MOVE,current.report.detail)
            Board2048SingleStep.Phase.STOPPED -> finish(Phase.STOPPED,current.report.detail)
            else -> update(current.report.detail)
        }
    }
    private fun update(detail:String) {
        report=Report(limit,turns.size,turns.sumOf { it.commands }+current.report.commands,turns=turns.toList(),detail=detail)
    }
    private fun finish(phase:Phase,detail:String) {
        turns.add(current.report)
        report=Report(limit,turns.count { it.phase==Board2048SingleStep.Phase.VERIFIED },turns.sumOf { it.commands },phase,turns.toList(),detail)
    }
}
