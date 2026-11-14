package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test

class Board2048RunSummaryTest {
    private fun result(phase:Board2048Batch.Phase,requests:Int=10,limit:Int=Board2048Batch.UNTIL_END)=
        Board2048Readout.Snapshot(status="stopped",step=Board2048SingleStep.Report(phase=Board2048SingleStep.Phase.STOPPED),
            batch=Board2048Batch.Report(limit,verified=(requests-1).coerceAtLeast(0),requests=requests,phase=phase,
                highestTile=1024,elapsedMs=986036,evidence=if(phase==Board2048Batch.Phase.WON)Board2048Batch.EndEvidence.WIN_LABEL else Board2048Batch.EndEvidence.NONE))
    @Test fun excludesNoMoveEndScreensAndCountLimitedTestsWithoutInventingFreshStarts() {
        val entries=listOf(
            Board2048History.Entry(1,result(Board2048Batch.Phase.WON)),
            Board2048History.Entry(2,result(Board2048Batch.Phase.WON,0)),
            Board2048History.Entry(3,result(Board2048Batch.Phase.COMPLETE,30,30)),
            Board2048History.Entry(4,result(Board2048Batch.Phase.STOPPED)),
            Board2048History.Entry(5,result(Board2048Batch.Phase.GAME_OVER)),
            Board2048History.Entry(6,result(Board2048Batch.Phase.WON)))
        val recent=Board2048RunSummary.recent(entries)
        assertEquals(listOf(4L,5L,6L),recent.map { it.number })
        assertEquals(Board2048Batch.Phase.STOPPED,recent.first().phase)
        assertEquals("16:26",recent.last().time)
    }
    @Test fun winLabelDoesNotTurnCoveredFinalActionIntoVerifiedAction() {
        val snapshot=result(Board2048Batch.Phase.WON)
        assertEquals("ENDED_BY_GAME_SCREEN",Board2048RunSummary.actionStatus(snapshot.step!!,snapshot.batch))
        assertEquals("STOPPED",Board2048RunSummary.actionStatus(snapshot.step,snapshot.batch,false))
        assertEquals(Board2048SingleStep.Phase.STOPPED,snapshot.step.phase)
        assertNull(snapshot.step.after)
        assertEquals(1024,snapshot.batch!!.highestTile)
        assertEquals("STOPPED",Board2048RunSummary.actionStatus(snapshot.step,result(Board2048Batch.Phase.STOPPED).batch))
    }
    @Test fun typedEvidenceAndComparisonSurviveShutdownAndReportExport() {
        Board2048Readout.stop()
        val batch=Board2048Batch(0,Board2048Batch.UNTIL_END)
        val board=Board2048Reader.Board(List(16){if(it==0)2 else if(it==15)4 else 0},Box(20f,200f,500f,680f))
        batch.observe(board,1_000_000_000,1000)
        batch.commandResult(batch.current,true,"completed",1200,1_200_000_000)
        batch.endScreen(true,1500)
        Board2048Readout.begin(true,batch.report)
        Board2048Readout.step(batch.current.report); Board2048Readout.batch(batch.report)
        Board2048Readout.frame(board,1500); Board2048Readout.stop()
        val s=Board2048Readout.snapshot
        assertEquals(Board2048Batch.EndEvidence.WIN_LABEL,s.batch!!.evidence)
        assertEquals(Board2048Batch.Phase.WON,s.recentGames.last().phase)
        val text=Board2048Readout.report()
        assertTrue(text.contains("status=won captureStatus=stopped"))
        assertTrue(text.contains("step=ENDED_BY_GAME_SCREEN rawStepPhase=STOPPED"))
        assertTrue(text.contains("fresh starts must be done manually"))
        assertFalse(text.contains("after=[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2048]"))
    }
}
