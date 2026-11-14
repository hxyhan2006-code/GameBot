package com.example.universalgamebot

/** Fixed allowlist. This class has no action capability and cannot be configured for arbitrary apps. */
class ReadOnlyGameSession(private val startedAt:Long) {
    companion object { const val TARGET="com.example.gamebotbricktest"
        const val ENTRY_ACTIVITY="com.faddensoft.breakout.GameActivity" }
    enum class Access { WAIT,READ,STOP }
    private var entered=false; private var stopped=false
    @Synchronized fun access(pkg:String?,now:Long):Access {
        if(stopped || now-startedAt>=120_000) { stopped=true; return Access.STOP }
        if(pkg==TARGET) { entered=true; return Access.READ }
        if(entered || now-startedAt>=10_000) { stopped=true; return Access.STOP }
        return Access.WAIT
    }
}
