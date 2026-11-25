package com.example.universalgamebot

/** Reserves the shared Android gesture dispatcher until its result arrives. */
internal class GestureSlot {
    private var clicker = false
    private var busy = false

    @Synchronized fun enterClicker(): Boolean {
        if (busy || clicker) return false
        clicker = true
        return true
    }

    @Synchronized fun leaveClicker() { clicker = false }

    @Synchronized fun acquire(autoClicker: Boolean): Boolean {
        if (busy || autoClicker != clicker) return false
        busy = true
        return true
    }

    @Synchronized fun release() { busy = false }
}
