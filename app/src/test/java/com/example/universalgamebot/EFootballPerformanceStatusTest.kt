package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class EFootballPerformanceStatusTest {
    @Test fun scaledRgbaReadsCentresAndHonoursRowPadding(){
        val b=ByteBuffer.allocate(48)
        for(y in 0..1)for(x in 0..3){val at=y*24+x*4;b.put(at,(x+y*10).toByte());b.put(at+1,50);b.put(at+2,70);b.put(at+3,(-1).toByte())}
        val f=SampledFrame()
        assertTrue(EFootballImageSampler.sample(b,24,4,4,2,f,2))
        assertEquals(2,f.width);assertEquals(1,f.height);assertEquals(1,f.step)
        assertEquals(11,f.rgb[0] shr 16 and 255);assertEquals(13,f.rgb[1] shr 16 and 255)
        assertFalse(EFootballImageSampler.sample(ByteBuffer.allocate(10),24,4,4,2,f,2))
        assertFalse(EFootballImageSampler.sample(b,8,4,4,2,f,2))
    }
    @Test fun fieldGlimpseThenPauseDoesNotArmOrConsumeThePlayBudget(){
        val start=10_000_000_000L;val g=EFootballRunGate(start)
        g.scene(EFootballScene.TRAINING_LIVE,start+100_000_000L,ready=false)
        assertFalse(g.armed)
        assertNull(g.scene(EFootballScene.UNKNOWN,start+200_000_000L))
        assertNull(g.check(EFootballTrainingSession.TARGET,start+40_000_000_000L))
        g.scene(EFootballScene.TRAINING_LIVE,start+41_000_000_000L,true)
        assertFalse(g.armed)
        g.scene(EFootballScene.TRAINING_LIVE,start+41_100_000_000L,true)
        assertFalse(g.armed)
        g.scene(EFootballScene.TRAINING_LIVE,start+41_200_000_000L,true)
        assertTrue(g.armed)
        assertNotNull(g.scene(EFootballScene.PAUSED,start+42_000_000_000L))
    }
    @Test fun badgeIsNeverGreenForRunningWithoutRecentEligibleGameCommands(){
        val now=10_000_000_000L
        val s=EFootballReadout.Snapshot(running=true,armed=true)
        assertEquals(EFootballBadgeState.READING,EFootballBadgeModel.state(s,now))
        val active=s.copy(canAct=true,commands=1,lastSentNs=now-100_000_000L)
        assertEquals(EFootballBadgeState.CONTROLLING,EFootballBadgeModel.state(active,now))
        assertEquals(EFootballBadgeState.READING,EFootballBadgeModel.state(active,now+900_000_000L))
        assertEquals(EFootballBadgeState.READING,EFootballBadgeModel.state(active.copy(canAct=false),now))
        assertEquals(EFootballBadgeState.STOPPED,EFootballBadgeModel.state(active.copy(running=false),now))
        assertEquals(EFootballBadgeState.WAITING,EFootballBadgeModel.state(active.copy(armed=false),now))
    }
    @Test fun onlyKnownNonFocusedAccessibilityBadgeMayBeExcludedFromScope(){
        val g=EFootballForeground();g.announced(EFootballTrainingSession.TARGET,10)
        val game=EFootballWindow(10,true,true,true,EFootballTrainingSession.TARGET)
        val badge=EFootballWindow(20,false,false,false,"our.app",true)
        assertEquals(EFootballTrainingSession.TARGET,g.resolveWithOwnedOverlay("our.app",20,listOf(game,badge),20,"our.app"))
        assertEquals("our.app",g.resolveWithOwnedOverlay("our.app",30,listOf(game,badge),20,"our.app"))
        assertEquals("bank.app",g.resolveWithOwnedOverlay("bank.app",20,listOf(game,badge),20,"our.app"))
        assertEquals("our.app",g.resolveWithOwnedOverlay("our.app",20,listOf(game,badge.copy(focused=true)),20,"our.app"))
        assertEquals("our.app",g.resolveWithOwnedOverlay("our.app",20,listOf(game,badge.copy(accessibilityOverlay=false)),20,"our.app"))
    }
}
