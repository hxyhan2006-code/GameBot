package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test

class EFootballStartFlowTest {
    private val start=10_000_000_000L
    private fun arm(g:EFootballRunGate,sourceNs:Long){
        assertNull(g.scene(EFootballScene.TRAINING_LIVE,sourceNs))
        assertNull(g.scene(EFootballScene.TRAINING_LIVE,sourceNs+200_000_000L))
    }
    private fun words()=listOf(
        EFootballWord("Practice List",310f,279f),EFootballWord("Command List",650f,279f),EFootballWord("Camera",970f,279f),
        EFootballWord("Play Settings",310f,430f),EFootballWord("Audio",650f,430f),EFootballWord("Quit Training",970f,430f),EFootballWord("Back",170f,539f))
    private fun menu(ns:Long,geometry:Long=7)=EFootballTrainingMenu.read(words(),1280,591,ns,geometry)!!
    @Test fun autoPauseMenuMayWaitFortyFiveSecondsWithoutUsingThePlayBudget(){
        val g=EFootballRunGate(start)
        assertNull(g.check("com.example.universalgamebot",start+5_000_000_000L))
        assertNull(g.scene(EFootballScene.UNKNOWN,start+45_000_000_000L))
        assertNull(g.check(EFootballTrainingSession.TARGET,start+45_000_000_000L))
        assertFalse(g.armed)
        arm(g,start+46_000_000_000L)
        assertTrue(g.armed)
        assertNull(g.check(EFootballTrainingSession.TARGET,start+165_000_000_000L))
        assertNotNull(g.check(EFootballTrainingSession.TARGET,start+166_200_000_000L))
    }
    @Test fun startupExpiresAndFocusLossAfterArmingCannotResume(){
        assertNotNull(EFootballRunGate(start).check(null,start+60_000_000_000L))
        val g=EFootballRunGate(start);arm(g,start+1)
        assertNotNull(g.check(null,start+300_000_000L))
        assertNotNull(g.check(EFootballTrainingSession.TARGET,start+301_000_000L))
        assertNotNull(EFootballRunGate(start).scene(EFootballScene.MATCH,start+1))
    }
    @Test fun menuNeedsAllSixTilesAndBackInTheirActualRegions(){
        assertNotNull(EFootballTrainingMenu.read(words(),1280,591,start,7))
        for(i in words().indices)assertNull(EFootballTrainingMenu.read(words().filterIndexed{j,_->i!=j},1280,591,start,7))
        assertNull(EFootballTrainingMenu.read(words().map{if(it.text=="Back")it.copy(x=980f,y=430f)else it},1280,591,start,7))
        assertNull(EFootballTrainingMenu.read(words()+words().last(),1280,591,start,7))
    }
    @Test fun backNeedsTwoFreshMatchingFramesAndIsConsumedOnlyOnce(){
        val g=EFootballMenuResume();val first=menu(start);g.observe(first)
        assertFalse(g.claim(first,EFootballTrainingSession.TARGET,7,start))
        val second=menu(start+100_000_000L);g.observe(second)
        assertFalse(g.claim(first,EFootballTrainingSession.TARGET,7,second.sourceNs))
        assertFalse(g.claim(second,null,7,second.sourceNs))
        assertFalse(g.claim(second,EFootballTrainingSession.TARGET,8,second.sourceNs))
        assertFalse(g.claim(second,EFootballTrainingSession.TARGET,7,second.sourceNs+501_000_000L))
        assertTrue(g.claim(second,EFootballTrainingSession.TARGET,7,second.sourceNs+1))
        val third=menu(start+200_000_000L);g.observe(third)
        assertFalse(g.claim(third,EFootballTrainingSession.TARGET,7,third.sourceNs))
    }
    @Test fun leavingMenuInvalidatesQueuedBack(){
        val g=EFootballMenuResume();g.observe(menu(start));val second=menu(start+100_000_000L);g.observe(second)
        g.observe(null)
        assertFalse(g.claim(second,EFootballTrainingSession.TARGET,7,second.sourceNs))
    }
    @Test fun clearIsAnAttackingControlButNotAShootCapability(){
        val w=listOf(EFootballWord("Pass",980f,487f),EFootballWord("Clear",1145f,322f),
            EFootballWord("Through",1013f,354f),EFootballWord("Dash",1135f,476f))
        val hud=EFootballHud.read(w,1280,591)!!
        assertTrue(hud.attack);assertFalse(hud.canShoot)
        val f=EFootballTrainingFrame(1,1,start,1,EFootballScene.TRAINING_LIVE,EFootballDirection.RIGHT,
            EFootballPossession.OWN,listOf(EFootballPlayer(1,EFootballTeam.OWN,EFootballPoint(.9f,.5f),1f)),
            EFootballBall(EFootballPoint(.9f,.5f),1f),1,true,1f,false,shootingAvailable=false)
        assertFalse(EFootballTrainingPolicy(EFootballTrainingConfig(allowPartialCoverage=true)).decide(f).intent is EFootballTrainingIntent.Shoot)
    }
    @Test fun unreadableControlsOnKnownFieldDoNotCancelBoundedInFlightGesture(){
        fun f(seq:Long,controls:Boolean=true)=EFootballTrainingFrame(1,seq,start+seq*100_000_000,1,
            EFootballScene.TRAINING_LIVE,EFootballDirection.RIGHT,EFootballPossession.OWN,
            listOf(EFootballPlayer(1,EFootballTeam.OWN,EFootballPoint(.9f,.5f),1f)),
            EFootballBall(EFootballPoint(.9f,.5f),1f),1,controls,1f,true)
        val s=EFootballTrainingSession(1,1,start)
        s.decide(EFootballTrainingSession.TARGET,f(1),f(1).sourceNs)
        val d=s.decide(EFootballTrainingSession.TARGET,f(2),f(2).sourceNs)
        assertNull(s.reserveDispatch(d,EFootballTrainingSession.TARGET,d.frame,d.frame.sourceNs))
        val unreadable=f(3,false)
        assertTrue(s.decide(EFootballTrainingSession.TARGET,unreadable,unreadable.sourceNs).intent is EFootballTrainingIntent.Wait)
        s.commandResult(2,d,true,unreadable.sourceNs)
        assertTrue(s.decide(EFootballTrainingSession.TARGET,f(4),f(4).sourceNs).intent is EFootballTrainingIntent.Neutral)
        assertTrue(s.decide(EFootballTrainingSession.TARGET,f(5),f(5).sourceNs).intent is EFootballTrainingIntent.Shoot)
    }
    @Test fun lateGameEntryAndStaticPauseMenuDoNotTripThePlayWatchdog(){
        val health=EFootballFrameWatchdog();val gate=EFootballRunGate(start)
        val entry=start+5_000_000_000L
        health.enter(entry)
        assertEquals(entry,health.enteredNs)
        // First queued image timestamp is too old and rejected; tick runs before a new image.
        assertFalse(health.expired(entry+50_000_000L,gate.armed))
        assertFalse(health.expired(start+45_000_000_000L,gate.armed))
        health.leaveBeforePlay();assertEquals(0L,health.enteredNs)
        health.enter(start+46_000_000_000L);health.fresh(start+46_100_000_000L)
        arm(gate,start+46_100_000_000L)
        assertFalse(health.expired(start+49_100_000_000L,gate.armed))
        assertTrue(health.expired(start+49_100_000_001L,gate.armed))
    }
    @Test fun missingRootNeedsTheCurrentFocusedWindowNotJustACachedPackage(){
        val g=EFootballForeground();g.announced(EFootballTrainingSession.TARGET,12)
        fun window(id:Int)=EFootballWindow(id,true,true,true,null)
        assertNull(g.resolve(null,emptyList()))
        assertNull(g.resolve(null,listOf(window(13))))
        assertNull(g.resolve(null,listOf(window(12).copy(active=false))))
        assertNull(g.resolve(null,listOf(window(12).copy(application=false))))
        assertNull(g.resolve(null,listOf(window(12),window(13))))
        assertEquals(EFootballTrainingSession.TARGET,g.resolve(null,listOf(window(12))))
        assertEquals("com.android.settings",g.resolve("com.android.settings",listOf(window(12))))
        assertEquals("com.android.settings",g.resolve(null,listOf(window(12).copy(rootPackage="com.android.settings"))))
        g.announced("com.android.settings",13);assertNull(g.resolve(null,listOf(window(12))))
        g.announced(EFootballTrainingSession.TARGET,12);g.reset();assertNull(g.resolve(null,listOf(window(12))))
    }
}
