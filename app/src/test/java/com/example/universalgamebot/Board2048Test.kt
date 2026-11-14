package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class Board2048Test {
    private val cells=listOf(2,0,4,0,8,16,32,64,128,256,512,1024,2048,0,2,4)
    private fun raster(values:List<Int> = cells, offset:Int=0, blocked:Boolean=false, unsupported:Boolean=false):SampledFrame {
        return SampledFrame.fromFunction(540,900,2) { x,y ->
            val ox=30+offset; val oy=220+offset
            var c=0xfaf8ef
            if(x in ox until ox+480 && y in oy until oy+480) {
                c=0xbbada0
                val col=(x-ox-10)/118; val row=(y-oy-10)/118
                if(x>=ox+10 && y>=oy+10 && col in 0..3 && row in 0..3) {
                    val dx=x-(ox+10+col*118); val dy=y-(oy+10+row*118)
                    if(dx in 0..107 && dy in 0..107) {
                        val value=values[row*4+col]
                        c=Board2048Reader.PALETTE.getValue(value)
                        // Simulate anti-aliased numeral holes in the connected tile background.
                        if(value!=0 && dx in 35..62 && dy in 35..70) c=0x776e65
                        if(unsupported && row==1 && col==1) c=0x3c3a32
                    }
                }
            }
            if(blocked && x in 100..440 && y in 400..560) c=0xffffff
            c
        }
    }
    @Test fun readsEverySupportedValueIncludingEmptyCellsWithNumeralHoles() {
        val b=Board2048Reader().read(raster())
        assertNotNull(b); assertEquals(cells,b!!.cells)
    }
    @Test fun translatedBoardIsLocatedWithoutHardcodedScreenCoordinates() {
        assertEquals(cells,Board2048Reader().read(raster(offset=10))!!.cells)
    }
    @Test fun overlaysAndUnsupportedTilesRejectWholeBoardInsteadOfInventingValues() {
        val r=Board2048Reader()
        assertNull(r.read(raster(blocked=true)))
        assertNull(r.read(raster(unsupported=true)))
        assertNull(r.read(SampledFrame.fromFunction(540,900,2) { _,_->0 }))
    }
    @Test fun changesAnimationOrGapsInvalidateConfirmation() {
        val r=Board2048Reader(); val b=r.read(raster())!!; val s=Stable2048Board()
        assertNull(s.observe(b,0)); assertNull(s.observe(b,200)); assertEquals(b,s.observe(b,400))
        assertNull(s.observe(null,600))
        assertNull(s.observe(b,800)); assertNull(s.observe(b,1000)); assertEquals(b,s.observe(b,1200))
        assertNull(s.observe(b,2200))
        assertNull(s.observe(b.copy(cells=cells.reversed()),2400))
        assertNull(s.observe(b,2400)) // duplicate timestamp cannot stabilize another frame
    }
    @Test fun scopeRequiresExactTargetAndStopsOnUnknownFocusAfterEntering() {
        val s=Board2048Session(0)
        assertEquals(Board2048Session.Access.WAIT,s.access(null,0))
        assertEquals(Board2048Session.Access.WAIT,s.access("com.example.universalgamebot",100))
        assertEquals(Board2048Session.Access.READ,s.access(Board2048Session.TARGET,200))
        assertEquals(Board2048Session.Access.STOP,s.access(null,300))
        assertEquals(Board2048Session.Access.STOP,s.access("com.example.gamebot2048test.fake",400))
        assertEquals(Board2048Session.Access.STOP,s.access(Board2048Session.TARGET,500))
    }
    @Test fun startupAndSessionHaveFiniteDeadlines() {
        assertEquals(Board2048Session.Access.STOP,Board2048Session(0).access(null,10_000))
        assertEquals(Board2048Session.Access.STOP,Board2048Session(0).access(Board2048Session.TARGET,120_001))
    }
    @Test fun reportedOldBoardIsExplicitlyNotLive() {
        Board2048Readout.begin()
        val b=Board2048Reader().read(raster())!!
        Board2048Readout.frame(b,400); Board2048Readout.frame(null,600); Board2048Readout.stop()
        assertFalse(Board2048Readout.snapshot.running)
        assertEquals(b,Board2048Readout.snapshot.board)
        assertTrue(Board2048Readout.report().contains("not guaranteed current"))
    }
    @Test fun lateDuplicateOrPreEntryCaptureBuffersAreNeverCountedAsFresh() {
        val c=Board2048FrameClock()
        assertFalse(c.accept(900_000_000,1_000_000_000,950_000_000))
        assertTrue(c.accept(1_000_000_000,1_010_000_000,950_000_000))
        assertFalse(c.accept(1_000_000_000,1_210_000_000,950_000_000))
        assertFalse(c.accept(1_100_000_000,2_000_000_000,950_000_000))
        assertFalse(c.accept(3_000_000_000,2_000_000_000,950_000_000))
        assertTrue(c.accept(2_100_000_000,2_110_000_000,950_000_000))
    }
    @Test fun stoppingPreventsLateWorkerResultsFromMakingTheReadoutLiveAgain() {
        Board2048Readout.begin(); Board2048Readout.stop()
        Board2048Readout.frame(Board2048Reader().read(raster()),1000)
        assertEquals("stopped",Board2048Readout.snapshot.status)
        assertEquals(0,Board2048Readout.snapshot.frames)
    }
    @Test fun readerComponentsHaveNoDirectGestureOrInternalGameStateDependency() {
        val root=if(File("src/main").exists()) File("src/main") else File("app/src/main")
        val names=listOf("Board2048CaptureService.kt","Board2048Activity.kt","Board2048Reader.kt","Board2048Session.kt")
        for(name in names) {
            val code=File(root,"java/com/example/universalgamebot/$name").readText()
            for(word in listOf("dispatchGesture(",".tap(",".swipe(","MainGame","SharedPreferences","SnapshotManager"))
                assertFalse("$name must not use $word",code.contains(word))
        }
    }
}
