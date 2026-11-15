package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test

class EFootballHudTest {
    private fun attack()=listOf(EFootballWord("Pass",980f,487f),EFootballWord("Shoot",1145f,322f),
        EFootballWord("Through",1013f,354f),EFootballWord("Dash",1135f,476f))
    @Test fun requiresAllFourLabelsFromOneImageAndStandardArrangement(){
        assertTrue(EFootballHud.read(attack(),1280,591)!!.attack)
        for(i in 0..3)assertNull(EFootballHud.read(attack().filterIndexed {n,_->n!=i},1280,591))
        assertNull(EFootballHud.read(attack()+EFootballWord("Pass",970f,480f),1280,591))
        assertNull(EFootballHud.read(attack().map{if(it.text=="Dash")it.copy(y=220f)else it},1280,591))
    }
    @Test fun defenseMustHaveStandardMatchUpNotJustReusedAttackingCoordinates(){
        val defense=listOf(EFootballWord("Switch",980f,487f),EFootballWord("Tackle",1145f,322f),
            EFootballWord("Match-up",1013f,354f),EFootballWord("Pressure",1135f,476f))
        assertFalse(EFootballHud.read(defense,1280,591)!!.attack)
        assertNull(EFootballHud.read(defense.filter{it.text!="Match-up"},1280,591))
    }
    @Test fun partialPolicyIsOptInAndDoesNotManufactureFullPlayerCoverage(){
        val f=EFootballTrainingFrame(1,1,100,1,EFootballScene.TRAINING_LIVE,EFootballDirection.RIGHT,
            EFootballPossession.OWN,listOf(EFootballPlayer(1,EFootballTeam.OWN,EFootballPoint(.85f,.5f),1f)),
            EFootballBall(EFootballPoint(.85f,.5f),1f),1,true,1f,false)
        assertTrue(EFootballTrainingPolicy().decide(f).intent is EFootballTrainingIntent.Neutral)
        val d=EFootballTrainingPolicy(EFootballTrainingConfig(allowPartialCoverage=true)).decide(f)
        assertTrue(d.intent is EFootballTrainingIntent.Shoot);assertFalse(d.frame.playerCoverageConfirmed)
        assertTrue(d.reason.contains("unseen defenders"))
        assertTrue(EFootballTrainingPolicy(EFootballTrainingConfig(allowPartialCoverage=true)).decide(f.copy(ball=null)).intent is EFootballTrainingIntent.Neutral)
    }
}
