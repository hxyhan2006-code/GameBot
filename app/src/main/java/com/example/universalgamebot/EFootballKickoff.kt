package com.example.universalgamebot

/** Visual centre-restart candidate, not hidden game state. Requires centre ball + starting shape. */
internal class EFootballKickoff {
    private var sinceNs=0L
    private var lastNs=0L
    fun scene(f:EFootballTrainingFrame,radarSelected:EFootballPoint?):EFootballScene {
        val ball=f.ball?.position
        val own=f.players.filter{it.team==EFootballTeam.OWN}
        val away=f.players.filter{it.team==EFootballTeam.OPPONENT}
        val startingShape=(own.count{it.position.x<=.55f}>=own.size*.8f && away.count{it.position.x>=.45f}>=away.size*.8f) ||
            (own.count{it.position.x>=.45f}>=own.size*.8f && away.count{it.position.x<=.55f}>=away.size*.8f)
        val candidate=f.scene==EFootballScene.TRAINING_LIVE && f.possession==EFootballPossession.OWN &&
            f.attackDirection!=null && ball!=null && ball.distance(EFootballPoint(.5f,.5f))<.04f &&
            radarSelected!=null && radarSelected.distance(ball)<.07f && own.size>=3 && away.size>=3 &&
            startingShape
        if(!candidate || f.sourceNs<=lastNs || f.sourceNs-lastNs>500_000_000L){sinceNs=0L}
        lastNs=f.sourceNs
        if(!candidate)return f.scene
        if(sinceNs==0L)sinceNs=f.sourceNs
        return if(f.sourceNs-sinceNs>=400_000_000L)EFootballScene.TRAINING_KICKOFF else f.scene
    }
    fun reset(){sinceNs=0;lastNs=0}
}
