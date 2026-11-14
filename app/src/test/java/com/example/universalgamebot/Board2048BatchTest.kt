package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test

class Board2048BatchTest {
    private fun board(cells:List<Int> = List(16) { if(it==15 || it==4)2 else 0 }) = Board2048Reader.Board(cells,Box(20f,200f,500f,680f))
    private fun spawn(r:Board2048SingleStep.Request) = board(r.move.shifted.toMutableList().apply { this[indexOf(0)] = 2 })

    @Test fun tenThirtyAndHundredStopExactlyAtLimitWithOneCallbackPerVerifiedMove() {
        for(limit in Board2048Batch.LIMITS) {
            val batch=Board2048Batch(0,limit)
            var currentBoard=board()
            var time=1000L
            repeat(limit) { index ->
                val r=batch.observe(currentBoard,time*1_000_000,time) ?: error("no request at $index/$limit ${batch.report}")
                val step=batch.current
                assertNull(batch.observe(currentBoard,(time+50)*1_000_000,time+50))
                batch.commandResult(step,true,"completed",time+200,(time+200)*1_000_000)
                val after=spawn(r)
                batch.observe(after,(time+100)*1_000_000,time+300) // pre-callback source is not evidence
                assertEquals(index,batch.report.verified)
                assertNull(batch.observe(after,(time+800)*1_000_000,time+800)) // verify, never dispatch next here
                assertEquals(index+1,batch.report.verified)
                batch.commandResult(step,false,"late callback",time+850,(time+850)*1_000_000)
                assertEquals(index+1,batch.report.verified)
                currentBoard=after; time+=1000
            }
            assertEquals(Board2048Batch.Phase.COMPLETE,batch.report.phase)
            assertEquals(limit,batch.report.requests)
            assertEquals(limit,batch.report.turns.size)
            assertNull(batch.observe(currentBoard,time*1_000_000,time))
            batch.stop(); batch.tick(time+10000)
            assertEquals(Board2048Batch.Phase.COMPLETE,batch.report.phase)
        }
    }
    @Test fun rejectionCancellationTimeoutAndLeavingNeverDispatchAnotherMove() {
        for(cause in listOf("reject","timeout","leave")) {
            val b=Board2048Batch(0,10)
            val request=b.observe(board(),1_000_000_000,1000)!!
            val step=b.current
            when(cause) {
                "reject" -> b.commandResult(step,false,"cancelled",1200,1_200_000_000)
                "timeout" -> b.tick(3501)
                "leave" -> b.stop()
            }
            assertTrue(b.terminal)
            assertEquals(0,b.report.verified); assertEquals(1,b.report.requests)
            b.commandResult(step,true,"late",4000,4_000_000_000)
            assertNull(b.observe(spawn(request),5_000_000_000,5000))
            assertEquals(1,b.report.turns.size)
        }
    }
    @Test fun uncertainChangedOrOldBoardBetweenMovesCannotAuthorizeAnotherSwipe() {
        for(cause in listOf("uncertain","changed","geometry")) {
            val b=Board2048Batch(0,10)
            val r=b.observe(board(),1_000_000_000,1000)!!
            b.commandResult(b.current,true,"completed",1200,1_200_000_000)
            val after=spawn(r)
            b.observe(after,1_800_000_000,1800)
            assertNull(b.observe(after,1_800_000_000,1900)) // repeated source cannot request next
            val next=when(cause) { "uncertain"->null; "changed"->board(); else->after.copy(bounds=Box(40f,220f,520f,700f)) }
            assertNull(b.observe(next,2_000_000_000,2000))
            assertEquals(Board2048Batch.Phase.STOPPED,b.report.phase)
            assertEquals(1,b.report.verified); assertEquals(1,b.report.requests)
        }
    }
    @Test fun invalidLimitsAndUnboundedDeadlinesAreRejected() {
        for(limit in listOf(-1,0,1,11,1000)) {
            try { Board2048Batch(0,limit); fail("accepted $limit") } catch(_:IllegalArgumentException) {}
        }
        val duration=Board2048Batch.durationMs(100)
        assertTrue(duration<=600000)
        val session=Board2048Session(0,duration)
        assertEquals(Board2048Session.Access.READ,session.access(Board2048Session.TARGET,120001))
        assertEquals(Board2048Session.Access.STOP,session.access(Board2048Session.TARGET,duration))
        try { Board2048Session(0,Board2048Batch.MAX_DURATION_MS+1); fail("unbounded") } catch(_:IllegalArgumentException) {}
    }
    @Test fun announcedFocusLossIsLatchedEvenIfForegroundAlreadyReturned() {
        val s=Board2048Session(0)
        s.access(Board2048Session.TARGET,1)
        val observed=Board2048Session.eventPackage(Board2048Session.TARGET,"com.example.other")
        assertEquals(Board2048Session.Access.STOP,s.access(observed,2))
        assertEquals(Board2048Session.Access.STOP,s.access(Board2048Session.TARGET,3))
        val batch=Board2048Batch(0,10)
        batch.stop("window focus lost or unknown")
        assertNull(batch.observe(board(),1_000_000_000,1000))
        assertEquals("window focus lost or unknown",batch.report.detail)
        val failed=Board2048Batch(0,10)
        failed.abort("no fresh capture frames")
        assertEquals(Board2048Batch.Phase.ERROR,failed.report.phase)
        assertEquals(0,failed.report.requests)
    }
    @Test fun playToEndHasFiniteCapAndScreenWinDoesNotInventVerifiedMoves() {
        assertEquals(1_800_000L,Board2048Batch.durationMs(Board2048Batch.UNTIL_END))
        val b=Board2048Batch(0,Board2048Batch.UNTIL_END)
        b.endScreen(true,1200)
        assertEquals(Board2048Batch.Phase.WON,b.report.phase)
        assertEquals(0,b.report.verified); assertEquals(0,b.report.requests)
        assertNull(b.observe(board(),2_000_000_000,2000))
        assertTrue(b.report.detail.contains("no move requested"))
        val lost=Board2048Batch(0,Board2048Batch.UNTIL_END)
        lost.endScreen(false,1200)
        assertEquals(Board2048Batch.Phase.GAME_OVER,lost.report.phase)
    }
    @Test fun longRunEvidenceAndStatsStayBoundedIndependentlyOfTarget() {
        val b=Board2048Batch(0,Board2048Batch.UNTIL_END)
        var current=board(); var time=1000L
        repeat(150) {
            val r=b.observe(current,time*1000000,time)!!
            b.commandResult(b.current,true,"completed",time+200,(time+200)*1000000)
            current=spawn(r); b.observe(current,(time+800)*1000000,time+800); time+=1000
        }
        assertEquals(150,b.report.verified); assertEquals(150,b.report.requests)
        assertEquals(100,b.report.turns.size); assertEquals(50,b.report.turnsDropped)
        assertTrue(b.report.highestTile>=current.cells.max())
        assertTrue(b.report.mergeScore>0)
        b.stop("test stop")
        assertEquals(150,b.report.verified); assertTrue(b.report.turns.size<=100)
        Board2048Readout.stop(); Board2048Readout.begin(true,b.report)
        Board2048Readout.step(b.current.report); Board2048Readout.batch(b.report); Board2048Readout.stop()
        val text=Board2048Readout.report()
        assertTrue(text.contains("verified=150 requests=150"))
        assertTrue(text.contains("turnsExported=20 earlierTurnsNotShown=131"))
        assertTrue(text.contains("turn #150: VERIFIED"))
        assertFalse(text.contains("turn #1: VERIFIED"))
    }
    @Test fun batchIsArchivedOnceAndReportContainsEveryLatestTurn() {
        val b=Board2048Batch(0,10)
        var current=board(); var time=1000L
        Board2048Readout.stop()
        val initial=Board2048Readout.snapshot.historyTotal
        Board2048Readout.begin(true,b.report)
        repeat(10) {
            val r=b.observe(current,time*1000000,time)!!
            b.commandResult(b.current,true,"completed",time+200,(time+200)*1000000)
            current=spawn(r); b.observe(current,(time+800)*1000000,time+800)
            time+=1000
        }
        Board2048Readout.step(b.current.report); Board2048Readout.batch(b.report)
        Board2048Readout.frame(current,time); Board2048Readout.stop(); Board2048Readout.stop()
        assertEquals(initial+1,Board2048Readout.snapshot.historyTotal)
        val report=Board2048Readout.report()
        assertTrue(report.contains("batch=COMPLETE limit=10 verified=10 requests=10"))
        assertTrue(report.contains("turn #1: VERIFIED")); assertTrue(report.contains("turn #10: VERIFIED"))
    }
}
