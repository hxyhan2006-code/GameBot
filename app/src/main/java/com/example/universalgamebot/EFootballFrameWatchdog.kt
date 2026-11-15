package com.example.universalgamebot

/** The first frame after entering may predate the source barrier. Startup silence is not play loss. */
class EFootballFrameWatchdog {
    @Volatile var enteredNs=0L;private set
    @Volatile private var lastArrivalNs=0L
    @Synchronized fun enter(nowNs:Long){lastArrivalNs=nowNs;enteredNs=nowNs}
    @Synchronized fun leaveBeforePlay(){enteredNs=0;lastArrivalNs=0}
    @Synchronized fun fresh(nowNs:Long){lastArrivalNs=nowNs}
    fun expired(nowNs:Long,armed:Boolean)=armed && lastArrivalNs>0 && nowNs-lastArrivalNs>3_000_000_000L
}
