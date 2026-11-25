package com.example.universalgamebot

internal data class ClickTarget(val x: Float, val y: Float, val delayMs: Long = 1000, val pressMs: Long = 60)

internal data class ClickProfile(
    val targets: List<ClickTarget> = listOf(ClickTarget(.5f, .5f)),
    val multi: Boolean = false,
    val rounds: Int = 0
)

/** Main-thread scheduler. The delay belongs to the target whose press just completed. */
internal class ClickerSequence(
    private val schedule: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val press: (ClickTarget, valid: () -> Boolean, done: (Boolean) -> Unit) -> Boolean,
    private val changed: (failed: Boolean) -> Unit
) {
    enum class State { STOPPED, RUNNING, PAUSED }

    var state = State.STOPPED
        private set
    var inFlight = false
        private set
    private var generation = 0L
    private var targets = emptyList<ClickTarget>()
    private var rounds = 0
    private var index = 0
    private var completedRounds = 0
    private var finishedRoundLimit = false
    private val step = Runnable { send() }

    fun play(profile: ClickProfile) {
        if (state == State.RUNNING || inFlight) return
        if (state == State.STOPPED) {
            targets = if (profile.multi) profile.targets.toList() else profile.targets.take(1)
            if (targets.isEmpty()) return
            rounds = profile.rounds
        }
        state = State.RUNNING
        changed(false)
        schedule(step, 0)
    }

    fun pause() {
        if (state != State.RUNNING) return
        state = State.PAUSED
        cancel(step)
        changed(false)
    }

    fun stop() = stop(false)

    private fun stop(failed: Boolean) {
        generation++
        cancel(step)
        state = State.STOPPED
        index = 0
        completedRounds = 0
        finishedRoundLimit = false
        changed(failed)
    }

    private fun send() {
        if (state != State.RUNNING) return
        if (finishedRoundLimit) { stop(); return }
        val currentGeneration = generation
        val target = targets[index]
        inFlight = true
        val accepted = press(target, {
            generation == currentGeneration && state == State.RUNNING
        }) { success ->
            inFlight = false
            if (generation != currentGeneration || state == State.STOPPED) {
                changed(false)
                return@press
            }
            if (!success) {
                if (state == State.RUNNING) stop(true) else changed(false)
                return@press
            }
            index++
            if (index == targets.size) {
                index = 0
                if (rounds != 0) finishedRoundLimit = ++completedRounds == rounds
            }
            if (state == State.RUNNING) schedule(step, target.delayMs) else changed(false)
        }
        if (!accepted) {
            inFlight = false
            stop(true)
        }
    }
}
