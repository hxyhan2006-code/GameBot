package com.example.universalgamebot

/** Source timestamp uses the monotonic nanoTime clock, independent of callback scheduling. */
class CapturedFrameClock {
    private var previous=0L
    fun accept(timestampNs:Long,nowNs:Long,enteredNs:Long):Boolean {
        val newer=timestampNs>previous
        if(timestampNs>previous && timestampNs<=nowNs) previous=timestampNs
        return newer && timestampNs>0 && timestampNs>=enteredNs && nowNs-timestampNs in 0L..750_000_000L
    }
}
