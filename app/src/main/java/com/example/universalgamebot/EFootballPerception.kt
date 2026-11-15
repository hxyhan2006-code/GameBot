package com.example.universalgamebot

import kotlin.math.hypot

/** Joins local OCR and pixel evidence from ONE image. No carry-over of old controls/ball. */
class EFootballPerception(private val generation:Long,private val geometryId:Long,
    private val direction:EFootballDirection,private val ownRed:Boolean,private val config:EFootballTrainingConfig) {
    private var sequence=0L
    private val pixels=EFootballPixels()
    private val policy=EFootballTrainingPolicy(config)
    private var nextId=1L
    private var tracks=emptyList<EFootballPlayer>()
    private var previousNs=0L
    var lastDiagnostic="waiting"; private set
    fun read(f:SampledFrame,sourceNs:Long,words:List<EFootballWord>,screenWidth:Int=f.width,screenHeight:Int=f.height):EFootballPilotObservation {
        sequence++
        val a=pixels.read(f,withAxes=false);val hud=EFootballHud.read(words,f.width,f.height)
        fun norm(b:EFootballPixels.Blob)=EFootballPoint((b.x-a.radar.left)/a.radar.width,(b.y-a.radar.top)/a.radar.height)
        val selected=a.radarSelected?.let(::norm)?.takeIf {it.valid()}
        val ball=a.ball?.let(::norm)?.takeIf {it.valid()}
        val header=EFootballHeader.read(words,f.width,f.height)
        val match=header.match || a.scoreboard
        val field=a.training && !match && !header.obstructed
        val selectedSeen=field && a.selected!=null && selected!=null
        val usable=selectedSeen && hud!=null
        if(sourceNs-previousNs>500_000_000L || sourceNs<=previousNs)tracks=emptyList()
        previousNs=sourceNs
        val assigned=HashSet<Long>()
        val players=ArrayList<EFootballPlayer>()
        fun track(p:EFootballPoint,team:EFootballTeam):Long {
            val old=tracks.filter {it.team==team && it.id !in assigned && it.position.distance(p)<.075f}
                .minByOrNull {it.position.distance(p)}
            val id=old?.id ?: nextId++;assigned+=id;players+=EFootballPlayer(id,team,p,.90f);return id
        }
        var selectedId:Long?=null
        if(selectedSeen && selected!=null) selectedId=track(selected,EFootballTeam.OWN)
        for((color,blobs) in listOf(true to a.red,false to a.green)) for(b in blobs) {
            val p=norm(b)
            if(!p.valid() || (selected!=null && p.distance(selected)<.055f))continue
            track(p,if(color==ownRed)EFootballTeam.OWN else EFootballTeam.OPPONENT)
        }
        tracks=players.toList()
        val possession=when {hud==null->EFootballPossession.UNKNOWN;hud.attack->EFootballPossession.OWN;else->EFootballPossession.OPPONENT}
        val frame=EFootballTrainingFrame(generation,sequence,sourceNs,geometryId,
            if(match)EFootballScene.MATCH else if(field)EFootballScene.TRAINING_LIVE else EFootballScene.UNKNOWN,direction,possession,players.toList(),
            ball?.let {EFootballBall(it,.90f)},selectedId,usable,if(usable).90f else 0f,false,shootingAvailable=hud?.canShoot==true)
        lastDiagnostic=when {
            match->"game scoreboard outside Free Training; header OCR=[${header.text}]"
            header.obstructed->"ambiguous header clock/score; screen may be covered; header OCR=[${header.text}]"
            !a.training->"training swap icon / pitch not established"
            hud==null->"four Standard control labels not read"
            a.selected==null->"solid controlled-player marker not established"
            selected==null->"controlled-player radar marker not established"
            ball==null->"ball colour marker not established"
            else->"partial radar (${players.size} players); camera=Dynamic Wide preset approximation (not calibrated)"
        }
        val tokens=words.filter{it.x>=f.width*.70f && it.y>=f.height*.35f}.take(12).joinToString("|"){it.text}
        lastDiagnostic+="; control OCR=[$tokens]"
        lastDiagnostic+="; anchors=pitch:${"%.2f".format(java.util.Locale.ROOT,a.grass)} selected:${a.selected!=null} radarSelected:${a.radarSelected!=null} ball:${a.ball!=null} hud:${hud!=null}"
        val controls=if(usable && hud!=null) {
            val sx=screenWidth.toFloat()/f.width;val sy=screenHeight.toFloat()/f.height
            val b=hud.buttons.mapValues {(_,p)->EFootballScreenPoint(p.x*sx,p.y*sy)}
            val pass=b[if(hud.attack)"pass" else "switch"]!!
            val shoot=b[if(!hud.attack)"tackle" else if(hud.canShoot)"shoot" else "clear"]!!
            val through=b[if(hud.attack)"through" else "matchup"]!!
            val dash=b["dash"] ?: b["pressure"]!!
            // Invisible floating stick: an explicit owner's input preset, not a visually detected stick.
            EFootballControlEvidence(generation,sequence,sourceNs,geometryId,
                EFootballScreenPoint(screenWidth*.16f,screenHeight*.76f),screenHeight*.10f,
                Box(screenWidth*.05f,screenHeight*.55f,screenWidth*.35f,screenHeight*.94f),
                Box(screenWidth*.70f,screenHeight*.35f,screenWidth.toFloat(),screenHeight*.96f),
                pass,shoot,dash,through,pass,null,true)
        } else null
        val d=policy.decide(frame)
        val target=when(val i=d.intent) {is EFootballTrainingIntent.Move->i.target;is EFootballTrainingIntent.Pass->i.target
            is EFootballTrainingIntent.Shoot->i.target;else->null}
        val active=players.find {it.id==selectedId}
        val bearing=if(target!=null && active!=null) {
            // An explicit camera-preset approximation, not an invented line measurement.
            val (dx,dy)=EFootballPixels.Axes(0f,0f,true).bearing(target.x-active.position.x,target.y-active.position.y)
            if(hypot(dx,dy)>.0001f) EFootballActionBearing(generation,sequence,sourceNs,geometryId,target,dx,dy) else null
        } else null
        return EFootballPilotObservation(frame,controls,bearing)
    }
}
