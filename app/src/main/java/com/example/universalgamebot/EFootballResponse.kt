package com.example.universalgamebot

/** Observed displacement after input is evidence of motion, never proof of a successful kick. */
internal class EFootballResponse {
    private data class Probe(val kind:String,val beganNs:Long,val sourceNs:Long,val selected:EFootballPoint?,val ball:EFootballPoint?,
        val selectedId:Long?,val possession:EFootballPossession,var attempts:Int=0,var samples:Int=0,var lastValidNs:Long=0)
    private var probe:Probe?=null
    private var lastKickNs=0L
    var motions=0;private set
    var unknown=0;private set
    var noResponse=0;private set
    var lastMotionNs=0L;private set
    var reason="no input observed yet";private set
    var terminal:String?=null;private set
    private var move=0;private var pass=0;private var shoot=0
    fun accepted(d:EFootballTrainingDecision,nowNs:Long){
        if(terminal!=null)return
        val kind=when(d.intent){is EFootballTrainingIntent.Move->"MOVE";is EFootballTrainingIntent.Pass->"PASS";is EFootballTrainingIntent.Shoot->"SHOOT";else->return}
        when(kind){"MOVE"->move++;"PASS"->pass++;else->shoot++}
        if(kind!="MOVE")lastKickNs=nowNs
        val selected=d.frame.players.find{it.id==d.frame.selectedPlayerId}?.position
        val old=probe
        if(old==null || old.kind!=kind)probe=Probe(kind,nowNs,d.frame.sourceNs,selected,d.frame.ball?.position,d.frame.selectedPlayerId,d.frame.possession)
        probe!!.attempts++
        reason="awaiting observed ${kind.lowercase()} response"
    }
    fun observe(f:EFootballTrainingFrame,nowNs:Long){
        if(terminal!=null)return
        val p=probe ?: return
        if(f.sourceNs<=p.sourceNs || nowNs-f.sourceNs !in 0..500_000_000L)return
        val selected=f.players.find{it.id==f.selectedPlayerId}?.position
        if(p.kind=="MOVE" && f.selectedPlayerId!=null &&
            (f.selectedPlayerId!=p.selectedId || f.possession!=p.possession)){
            unknown++;reason="move response unverified: controlled player or possession changed";probe=null;return
        }
        val position=if(p.kind=="MOVE")selected else f.ball?.position
        val before=if(p.kind=="MOVE")p.selected else p.ball
        val valid=f.scene in setOf(EFootballScene.TRAINING_LIVE,EFootballScene.TRAINING_KICKOFF) &&
            (p.kind!="MOVE" || f.controlsRecognized) && position!=null && before!=null &&
            (p.kind!="MOVE" || p.possession!=EFootballPossession.OWN || (p.ball!=null && f.ball!=null))
        if(valid){
            p.samples++
            p.lastValidNs=f.sourceNs
            val displaced=position!!.distance(before!!)>if(p.kind=="MOVE" && p.possession==EFootballPossession.OPPONENT).05f else if(p.kind=="MOVE").035f else .045f
            val ballSupportsMove=p.kind!="MOVE" || p.possession!=EFootballPossession.OWN ||
                (p.ball!=null && f.ball!=null && f.ball.position.distance(p.ball)>.025f)
            if(displaced && ballSupportsMove){
                motions++;lastMotionNs=nowNs;reason="${p.kind}: displacement observed; outcome not established";probe=null;return
            }
        }
        if(nowNs-p.beganNs>=2_500_000_000L){
            if(valid && p.samples>=3 && p.attempts>=3){
                noResponse++;reason="${p.kind}: unchanged after ${p.attempts} inputs / ${p.samples} readable observations"
                terminal="no visual response: $reason"
            }else{unknown++;reason="response unverified: insufficient current visual evidence"}
            probe=null
        }
    }
    fun waitingForKick(nowNs:Long)=lastKickNs>0 && nowNs-lastKickNs in 0..850_000_000L && probe?.kind in setOf("PASS","SHOOT")
    fun pause(){probe=null;lastKickNs=0;lastMotionNs=0;reason="response probe cleared for training menu"}
    fun report()="moves=$move passes=$pass shots=$shoot motionObserved=$motions unchanged=$noResponse unknown=$unknown; $reason"
}
