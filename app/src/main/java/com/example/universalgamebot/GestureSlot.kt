package com.example.universalgamebot

/** Keeps Vision and Auto Clicker mutually exclusive while Android owns an input gesture. */
internal class GestureSlot {
    private var clicker = false
    private var clickPending = false
    private var visionPending = 0

    @Synchronized fun enterClicker(): Boolean {
        if (clicker || clickPending || visionPending != 0) return false
        clicker = true
        return true
    }

    @Synchronized fun leaveClicker() { clicker = false }

    @Synchronized fun acquire(autoClicker: Boolean): Boolean {
        if (autoClicker != clicker || clickPending) return false
        if (autoClicker) clickPending = true else visionPending++
        return true
    }

    @Synchronized fun release(autoClicker: Boolean) {
        if (autoClicker) clickPending = false else visionPending--
    }
}
