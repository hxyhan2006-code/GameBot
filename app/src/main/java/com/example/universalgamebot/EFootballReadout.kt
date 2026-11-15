package com.example.universalgamebot

/** Memory-only telemetry. Gesture completion never counts as a pass, shot outcome or win. */
object EFootballReadout {
    data class Snapshot(val running:Boolean=false,val generation:Long=0,val status:String="not started",
        val frames:Int=0,val live:Int=0,val commands:Long=0,val completed:Long=0,val failed:Long=0,
        val sourceAgeMs:Long=0,val diagnostic:String="",val decision:String="",val players:Int=0,
        val ball:EFootballPoint?=null,val selected:Long?=null,val startupMenu:String="not observed",val focus:String="not observed",val armed:Boolean=false,val canAct:Boolean=false,val lastSentNs:Long=0,val pipeline:String="not sampled")
    private var counter=0L
    private var stampedCommands=0L
    @Volatile var snapshot=Snapshot(); private set
    private val history=ArrayDeque<String>()
    @Synchronized fun begin():Long {counter++;stampedCommands=0;snapshot=Snapshot(true,counter,"waiting for training");return counter}
    @Synchronized internal fun frame(id:Long,o:EFootballPilotObservation,age:Long,diagnostic:String,c:EFootballTrainingController?) {
        if(!snapshot.running || snapshot.generation!=id)return
        snapshot=snapshot.copy(status=if(o.frame.scene==EFootballScene.TRAINING_LIVE)"training" else "uncertain",
            frames=snapshot.frames+1,live=snapshot.live+if(o.frame.scene==EFootballScene.TRAINING_LIVE)1 else 0,
            commands=c?.dispatched ?: 0,completed=c?.completed ?: 0,failed=c?.failed ?: 0,
            canAct=o.frame.controlsRecognized && o.bearing!=null && o.frame.ball!=null && age<=500,
            sourceAgeMs=age,diagnostic=diagnostic,decision=c?.lastReason ?: "waiting",players=o.frame.players.size,
            ball=o.frame.ball?.position,selected=o.frame.selectedPlayerId)
    }
    @Synchronized fun armed(id:Long,value:Boolean){if(snapshot.running && snapshot.generation==id)snapshot=snapshot.copy(armed=value)}
    @Synchronized fun pipeline(id:Long,text:String){if(snapshot.running && snapshot.generation==id)snapshot=snapshot.copy(pipeline=text)}
    @Synchronized fun focus(id:Long,text:String){if(snapshot.running && snapshot.generation==id)snapshot=snapshot.copy(focus=text)}
    @Synchronized fun menu(id:Long,text:String){if(snapshot.running && snapshot.generation==id)snapshot=snapshot.copy(startupMenu=text)}
    @Synchronized fun tally(id:Long,sent:Long,done:Long,fail:Long){
        if(snapshot.generation!=id)return
        val newCommand=sent>stampedCommands
        if(newCommand)stampedCommands=sent
        snapshot=snapshot.copy(commands=sent,completed=done,failed=fail,lastSentNs=if(newCommand)System.nanoTime() else snapshot.lastSentNs)
        if(!snapshot.running && history.isNotEmpty()){history.removeLast();history.addLast(report())}
    }
    @Synchronized fun note(id:Long,text:String){if(snapshot.running && snapshot.generation==id)snapshot=snapshot.copy(diagnostic=text)}
    @Synchronized internal fun stop(id:Long,reason:String,c:EFootballTrainingController?=null) {if(snapshot.running && snapshot.generation==id){snapshot=snapshot.copy(running=false,status=reason,commands=c?.dispatched ?: snapshot.commands,completed=c?.completed ?: snapshot.completed,failed=c?.failed ?: snapshot.failed)
        history.addLast(report());while(history.size>10)history.removeFirst()}}
    fun report():String=snapshot.let {s->"EFOOTBALL EXPERIMENTAL TRAINING\ntarget=${EFootballTrainingSession.TARGET}\nrunning=${s.running} status=${s.status}\narmed=${s.armed} canAct=${s.canAct}\npipeline=${s.pipeline}\nframes=${s.frames} live=${s.live} sourceAgeMs=${s.sourceAgeMs}\ncommands=${s.commands} AndroidCompleted=${s.completed} failures=${s.failed}\nplayers=${s.players} selected=${s.selected} ball=${s.ball}\nfocus=${s.focus}\nstartupMenu=${s.startupMenu}\nvision=${s.diagnostic}\ndecision=${s.decision}\nPartial visual estimates. No match results or successful kicks inferred from gestures. Stopped observations are historical. No cloud calls or screenshot files."}
    @Synchronized fun historyReport()=history.joinToString("\n\n")+if(snapshot.running)"\n\n"+report() else ""
}
