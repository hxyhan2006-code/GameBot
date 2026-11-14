package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test

class Board2048HistoryTest {
    private fun result(phase: Board2048SingleStep.Phase, value: Int = 2) = Board2048Readout.Snapshot(
        status="stopped", board=Board2048Reader.Board(List(16) { if(it==0)value else 0 },Box(0f,0f,400f,400f)),
        step=Board2048SingleStep.Report(phase=phase, before=List(16){value}, after=List(16){value}))

    @Test fun retainsOrderedSuccessFailureAndStopWithoutDiscardingPreviousRuns() {
        val h=Board2048History()
        h.record(result(Board2048SingleStep.Phase.VERIFIED))
        h.record(result(Board2048SingleStep.Phase.ERROR,4))
        h.record(result(Board2048SingleStep.Phase.STOPPED,8))
        assertEquals(listOf(1L,2L,3L),h.entries.map { it.number })
        assertEquals(listOf(Board2048SingleStep.Phase.VERIFIED,Board2048SingleStep.Phase.ERROR,Board2048SingleStep.Phase.STOPPED),h.entries.map { it.result.step!!.phase })
        assertEquals(listOf(2,4,8),h.entries.map { it.result.board!!.cells[0] })
    }
    @Test fun boundedHistoryDropsOldestAndKeepsMonotonicRunNumbers() {
        val h=Board2048History(20)
        repeat(25) { h.record(result(Board2048SingleStep.Phase.VERIFIED)) }
        assertEquals(25L,h.total); assertEquals(20,h.size)
        assertEquals((6L..25L).toList(),h.entries.map { it.number })
        assertEquals(0,Board2048History().size) // a new process/model starts empty
    }
    @Test fun freezesBoardsAndStepEvidenceInsteadOfKeepingMutableInputLists() {
        val values=MutableList(16){2}
        val h=Board2048History()
        val r=result(Board2048SingleStep.Phase.VERIFIED)
        h.record(r.copy(board=r.board!!.copy(cells=values), step=r.step!!.copy(before=values,expected=values,after=values)))
        values[0]=2048
        val saved=h.entries.single().result
        assertEquals(2,saved.board!!.cells[0]); assertEquals(2,saved.step!!.before!![0])
        assertEquals(2,saved.step.expected!![0]); assertEquals(2,saved.step.after!![0])
    }
    @Test fun observedVictoryWithoutActionIsSeparateFromVerifiedRunCount() {
        Board2048Readout.stop()
        fun headerValue(name:String):Int = Regex("$name=([0-9]+)").find(Board2048Readout.report())?.groupValues?.get(1)?.toInt() ?: 0
        val completed=headerValue("verifiedInRetained")
        val batch=Board2048Batch(0,Board2048Batch.UNTIL_END)
        batch.endScreen(true,1000)
        Board2048Readout.begin(true,batch.report)
        Board2048Readout.step(batch.current.report); Board2048Readout.batch(batch.report); Board2048Readout.stop()
        assertEquals(completed,headerValue("verifiedInRetained"))
        assertTrue(headerValue("winsObservedInRetained")>=1)
        assertTrue(Board2048Readout.report().contains("no move requested in this run"))
    }
    @Test fun threeSessionsAreCopiedTogetherAndRepeatedShutdownDoesNotDuplicate() {
        Board2048Readout.stop()
        val startTotal=Board2048Readout.snapshot.historyTotal
        for((index,phase) in listOf(Board2048SingleStep.Phase.VERIFIED,Board2048SingleStep.Phase.ERROR,Board2048SingleStep.Phase.STOPPED).withIndex()) {
            Board2048Readout.begin(true)
            Board2048Readout.step(result(phase).step!!)
            Board2048Readout.frame(result(phase).board,1000L+index)
            if(phase==Board2048SingleStep.Phase.ERROR) Board2048Readout.fail() else Board2048Readout.stop()
            Board2048Readout.stop(); Board2048Readout.stop()
        }
        assertEquals(startTotal+3,Board2048Readout.snapshot.historyTotal)
        val report=Board2048Readout.report()
        assertTrue(report.contains("=== run #${startTotal+1} ==="))
        assertTrue(report.contains("=== run #${startTotal+2} ==="))
        assertTrue(report.contains("=== run #${startTotal+3} ==="))
        assertTrue(report.contains("step=VERIFIED")); assertTrue(report.contains("step=ERROR")); assertTrue(report.contains("step=STOPPED"))
        assertFalse(report.contains("current session / status (not archived)"))
        Board2048Readout.begin() // read-only must not erase history or count as a swipe run
        assertTrue(Board2048Readout.report().contains("current session / status (not archived)"))
        Board2048Readout.stop()
        assertEquals(startTotal+3,Board2048Readout.snapshot.historyTotal)
        assertTrue(Board2048Readout.report().contains("2048 READ ONLY"))
    }
}
