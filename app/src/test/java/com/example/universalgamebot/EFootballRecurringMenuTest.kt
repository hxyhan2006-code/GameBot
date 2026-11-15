package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test

class EFootballRecurringMenuTest {
    private val start=10_000_000_000L
    private fun menu(ns:Long,x:Float=170f):EFootballTrainingMenu {
        val words=listOf(EFootballWord("Practice List",310f,279f),EFootballWord("Command List",650f,279f),EFootballWord("Camera",970f,279f),
            EFootballWord("Play Settings",310f,430f),EFootballWord("Audio",650f,430f),EFootballWord("Quit Training",970f,430f),EFootballWord("Back",x,539f))
        return EFootballTrainingMenu.read(words,1280,591,ns,7)!!
    }
    private fun arm(g:EFootballRunGate,ns:Long){g.scene(EFootballScene.TRAINING_LIVE,ns);g.scene(EFootballScene.TRAINING_LIVE,ns+200_000_000L)}
    @Test fun backUsesCurrentOcrPositionAndOnePressPerAppearance(){
        val r=EFootballMenuResume();r.observe(menu(start,270f));val current=menu(start+200_000_000L,270f);r.observe(current)
        assertEquals(270f,current.back.x,0f);assertTrue(r.claim(current,EFootballTrainingSession.TARGET,7,current.sourceNs))
        val same=menu(start+300_000_000L,270f);r.observe(same);assertFalse(r.claim(same,EFootballTrainingSession.TARGET,7,same.sourceNs))
        r.observe(null);assertTrue(r.awaitingField())
        r.fieldConfirmed(start+1_000_000_000L);assertEquals(1,r.resumed)
        r.observe(menu(start+2_000_000_000L));val again=menu(start+2_200_000_000L);r.observe(again)
        assertTrue(r.claim(again,EFootballTrainingSession.TARGET,7,again.sourceNs))
    }
    @Test fun recognizedMenuAfterArmingSuspendsThenNeedsFreshFieldReadinessAgain(){
        val g=EFootballRunGate(start);arm(g,start+1);assertTrue(g.playing)
        assertNull(g.scene(EFootballScene.UNKNOWN,start+1_000_000_000L,false));assertFalse(g.playing)
        g.trainingMenu(start+1_200_000_000L)
        assertNull(g.check(EFootballTrainingSession.TARGET,start+2_000_000_000L))
        g.scene(EFootballScene.TRAINING_LIVE,start+2_200_000_000L,true);assertFalse(g.playing)
        g.scene(EFootballScene.TRAINING_LIVE,start+2_400_000_000L,true);assertTrue(g.playing)
        // A second pause is also recognized, without resetting the original wall deadline.
        g.trainingMenu(start+3_000_000_000L);arm(g,start+3_200_000_000L);assertTrue(g.playing)
        assertNotNull(g.check(EFootballTrainingSession.TARGET,start+121_000_000_000L))
    }
    @Test fun unknownScreensAndForeignFocusCannotResumeByMerelyReturningToTheField(){
        val g=EFootballRunGate(start);arm(g,start+1)
        g.scene(EFootballScene.UNKNOWN,start+1_000_000_000L,false)
        assertNotNull(g.check(EFootballTrainingSession.TARGET,start+2_600_000_000L))
        assertNotNull(g.scene(EFootballScene.TRAINING_LIVE,start+2_700_000_000L))
        val foreign=EFootballRunGate(start);arm(foreign,start+1);foreign.trainingMenu(start+1_000_000_000L)
        assertNotNull(foreign.check("other.app",start+1_100_000_000L))
        assertNotNull(foreign.scene(EFootballScene.TRAINING_LIVE,start+1_200_000_000L))
    }
    @Test fun verifiedMenuCannotWaitForeverOrWeakenMatchRejection(){
        val g=EFootballRunGate(start);arm(g,start+1);g.trainingMenu(start+1_000_000_000L)
        assertNotNull(g.check(EFootballTrainingSession.TARGET,start+11_100_000_000L))
        val match=EFootballRunGate(start);arm(match,start+1);match.trainingMenu(start+1_000_000_000L)
        assertNotNull(match.scene(EFootballScene.MATCH,start+1_200_000_000L))
    }
}
