package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test

class Board2048SingleStepTest {
    private fun cells(vararg row: Int) = row.toList() + List(16-row.size) { 0 }
    private fun board(c: List<Int> = cells(2,2,4,4)) = Board2048Reader.Board(c, Box(20f,200f,500f,680f))
    private fun spawn(move: Board2048Moves.Move): List<Int> = move.shifted.toMutableList().apply { this[indexOf(0)] = 2 }

    @Test fun mergesEachTileOnlyOnceAndDoesNotMergeAcrossOtherTiles() {
        fun row(vararg values: Int) = Board2048Moves.slide(cells(*values), Board2048Moves.Direction.LEFT).shifted.take(4)
        assertEquals(listOf(4,4,0,0), row(2,2,2,2))
        assertEquals(listOf(4,2,0,0), row(2,2,2,0))
        assertEquals(listOf(2,4,2,0), row(2,4,2,0))
        assertEquals(listOf(8,4,0,0), row(4,0,4,4))
        assertEquals(listOf(4,8,0,0), row(2,2,4,4))
    }
    @Test fun allDirectionsPreserveSumAndNeverChooseAnUnchangedBoard() {
        val random=java.util.Random(38)
        repeat(200) {
            val before=List(16) { listOf(0,0,2,4,8,16)[random.nextInt(6)] }
            for(d in Board2048Moves.Direction.entries) {
                val moved=Board2048Moves.slide(before,d)
                assertEquals(before.sum(),moved.shifted.sum())
                assertTrue(moved.shifted.count { it!=0 } <= before.count { it!=0 })
                if(moved.shifted!=before) assertTrue(Board2048Moves.verifies(moved,spawn(moved)))
            }
            Board2048Moves.choose(before)?.let { assertNotEquals(before,it.shifted) }
        }
        val before=cells(2,0,0,0)
        assertEquals(2,Board2048Moves.slide(before,Board2048Moves.Direction.RIGHT).shifted[3])
        assertEquals(2,Board2048Moves.slide(before,Board2048Moves.Direction.DOWN).shifted[12])
        assertEquals(2,Board2048Moves.slide(cells(0,2),Board2048Moves.Direction.LEFT).shifted[0])
        assertEquals(2,Board2048Moves.slide(List(4){0}+listOf(2)+List(11){0},Board2048Moves.Direction.UP).shifted[0])
    }
    @Test fun verifiesExactlyOneSpawnAndRejectsMissingOrMultipleSpawnsOrWrongValue() {
        val move=Board2048Moves.slide(cells(2,2),Board2048Moves.Direction.LEFT)
        assertTrue(Board2048Moves.verifies(move,spawn(move)))
        assertTrue(Board2048Moves.verifies(move,move.shifted.toMutableList().apply { this[7]=4 }))
        assertFalse(Board2048Moves.verifies(move,move.shifted))
        assertFalse(Board2048Moves.verifies(move,move.shifted.toMutableList().apply { this[7]=8 }))
        assertFalse(Board2048Moves.verifies(move,move.shifted.toMutableList().apply { this[7]=2; this[8]=2 }))
        assertFalse(Board2048Moves.verifies(move,cells(2,2)))
    }
    @Test fun noLegalOrUnsupportedBoardsNeverRequestASwipe() {
        val full=List(16) { if((it/4+it%4)%2==0)2 else 4 }
        assertNull(Board2048Moves.choose(full))
        assertNull(Board2048Moves.choose(List(16){3}))
        assertNull(Board2048Moves.choose(List(15){2}))
        assertNull(Board2048Moves.choose(List(16){0}))
        val step=Board2048SingleStep(0)
        assertNull(step.observe(board(full),1,500))
        assertEquals(Board2048SingleStep.Phase.NO_LEGAL_MOVE,step.report.phase)
        assertEquals(0,step.report.commands)
    }
    @Test fun oneRequestOneCallbackFreshStableResultThenTerminal() {
        val step=Board2048SingleStep(0)
        assertNull(step.observe(null,1,200))
        val r=step.observe(board(),500_000_000,500)!!
        assertNull(step.observe(board(),700_000_000,700))
        assertEquals(1,step.report.commands)
        step.commandResult(true,"ok",800,800_000_000)
        val after=board(spawn(r.move))
        assertNull(step.observe(after,799_000_000,900)) // pre-callback source cannot verify
        assertEquals(Board2048SingleStep.Phase.VERIFYING,step.report.phase)
        step.observe(after,1_300_000_000,1300)
        assertEquals(Board2048SingleStep.Phase.VERIFIED,step.report.phase)
        assertTrue(step.report.commandCompleted)
        repeat(10) { assertNull(step.observe(board(),2_000_000_000,2000)) }
        step.stop(); step.commandResult(false,"late",2100,2_100_000_000)
        assertEquals(Board2048SingleStep.Phase.VERIFIED,step.report.phase)
    }
    @Test fun failureCancellationChangedGeometryAndTimeoutNeverRetry() {
        for(cause in listOf("failed","stopped","no_callback","no_result","wrong_result","geometry")) {
            val s=Board2048SingleStep(0); val r=s.observe(board(),500_000_000,500)!!
            when(cause) {
                "failed" -> s.commandResult(false,"cancelled",600,600_000_000)
                "stopped" -> s.stop()
                "no_callback" -> s.tick(3100)
                else -> {
                    s.commandResult(true,"completed",800,800_000_000)
                    when(cause) {
                        "no_result" -> s.tick(4801)
                        "wrong_result" -> s.observe(board(List(16){2}),1_400_000_000,1400)
                        "geometry" -> s.observe(board(spawn(r.move)).copy(bounds=Box(100f,200f,580f,680f)),1_400_000_000,1400)
                    }
                }
            }
            assertTrue("$cause must terminate",s.terminal)
            assertNull(s.observe(board(),5_000_000_000,5000))
            assertEquals(1,s.report.commands)
        }
        val s=Board2048SingleStep(0); s.tick(10_001)
        assertNull(s.observe(board(),11_000_000_000,11_000))
        assertEquals(0,s.report.commands)
    }
    private fun permit()=Board2048SwipePermit(Board2048SingleStep.Request(
        Board2048Moves.choose(board().cells)!!,board().bounds,1_000_000_000),540,900)
    private val window=Box(0f,60f,540f,860f)
    @Test fun gateConsumesOnceAndRejectsWrongUnknownForegroundStaleImageStopResizeAndOffWindow() {
        fun accept(p:Board2048SwipePermit,pkg:String?=Board2048Session.TARGET,now:Long=1_100_000_000,active:Boolean=true,w:Int=540,h:Int=900,b:Box=window)=p.consume(pkg,now,active,w,h,b)
        val ok=permit(); assertTrue(accept(ok)); assertFalse(accept(ok))
        assertFalse(accept(permit(),pkg=null))
        assertFalse(accept(permit(),pkg="com.example.gamebot2048test.fake"))
        assertFalse(accept(permit(),pkg="com.example.universalgamebot"))
        assertFalse(accept(permit(),now=1_750_000_001))
        assertFalse(accept(permit(),now=999_000_000))
        assertFalse(accept(permit(),active=false))
        assertFalse(accept(permit(),w=900,h=540))
        assertFalse(accept(permit(),b=Box(0f,300f,540f,860f)))
        val revoked=permit(); revoked.revoke(); assertFalse(accept(revoked))
        val rejected=permit(); assertFalse(accept(rejected,pkg=null)); assertFalse(accept(rejected))
    }
    @Test fun windowMetadataMustIdentifyOneFocusedTargetAndNoOccludingWindow() {
        val target=Board2048WindowGate.Window(7,1,2,true,true,0,window)
        val status=Board2048WindowGate.Window(8,3,9,false,false,0,Box(0f,0f,540f,60f))
        assertTrue(Board2048WindowGate.allows(listOf(target,status),7,board().bounds))
        assertFalse(Board2048WindowGate.allows(emptyList(),7,board().bounds))
        assertFalse(Board2048WindowGate.allows(listOf(target),9,board().bounds))
        assertFalse(Board2048WindowGate.allows(listOf(target.copy(focused=false)),7,board().bounds))
        assertFalse(Board2048WindowGate.allows(listOf(target.copy(displayId=1)),7,board().bounds))
        assertFalse(Board2048WindowGate.allows(listOf(target,target.copy(id=9,layer=5,type=4,active=false,focused=false)),7,board().bounds))
        assertFalse(Board2048WindowGate.allows(listOf(target,target),7,board().bounds))
    }
    @Test fun minimumSupportedBoardCanSwipeBeyondFixtureDistanceThreshold() {
        // Upstream InputListener requires 250 physical px cumulative travel. Endpoints remain in-board.
        val b=Box(20f,200f,404f,584f)
        val request=Board2048SingleStep.Request(Board2048Moves.choose(board().cells)!!,b,1_000_000_000)
        assertTrue(Board2048SwipePermit(request,540,900).consume(Board2048Session.TARGET,1_100_000_000,true,540,900,window))
        assertTrue(b.width*0.84f>250f)
        val small=Board2048SwipePermit(request.copy(bounds=Box(20f,200f,380f,560f)),540,900)
        assertFalse(small.consume(Board2048Session.TARGET,1_100_000_000,true,540,900,window))
    }
    @Test fun sourceOnlyScopeAndReadOnlyReportsRemainDistinct() {
        Board2048Readout.begin(true)
        val s=Board2048SingleStep(0); val r=s.observe(board(),500_000_000,500)!!
        s.commandResult(true,"completed",800,800_000_000)
        s.observe(board(spawn(r.move)),1_400_000_000,1400)
        Board2048Readout.step(s.report); Board2048Readout.stop()
        assertTrue(Board2048Readout.report().contains("SINGLE STEP"))
        assertTrue(Board2048Readout.report().contains("step=VERIFIED"))
        Board2048Readout.begin(); Board2048Readout.stop()
        assertNull(Board2048Readout.snapshot.step)
        assertTrue(Board2048Readout.report().contains("No gestures."))
    }
}
