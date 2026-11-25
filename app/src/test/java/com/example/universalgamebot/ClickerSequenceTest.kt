package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test

class ClickerSequenceTest {
    private class Runner {
        var scheduled: Pair<Runnable, Long>? = null
        var result: ((Boolean) -> Unit)? = null
        var valid: (() -> Boolean)? = null
        var now = 0L
        val sent = mutableListOf<ClickTarget>()
        val sequence = ClickerSequence(
            { task, delay -> assertNull(scheduled); scheduled = task to delay },
            { task -> if (scheduled?.first == task) scheduled = null },
            { now },
            { target, allowed, done ->
                assertNull(result)
                sent += target
                valid = allowed
                result = done
                true
            },
            {}
        )

        fun step(delay: Long) {
            val next = checkNotNull(scheduled)
            assertEquals(delay, next.second)
            scheduled = null
            now += delay
            next.first.run()
        }

        fun complete(success: Boolean = true) {
            val callback = checkNotNull(result)
            assertNull(scheduled)
            result = null
            callback(success)
        }
    }

    private val one = ClickTarget(.1f, .2f, 1000, 60)
    private val two = ClickTarget(.3f, .4f, 30000, 125)
    private val three = ClickTarget(.5f, .6f, 2000, 20)

    @Test fun followsCallbackOrderDelaysAndRoundLimit() {
        val runner = Runner()
        runner.sequence.play(ClickProfile(listOf(one, two, three), multi = true, rounds = 2))
        runner.step(0)
        repeat(2) { round ->
            runner.complete()
            runner.step(1000)
            runner.complete()
            runner.step(30000)
            runner.complete()
            if (round == 0) runner.step(2000)
        }
        assertNull(runner.scheduled)
        assertEquals(listOf(one, two, three, one, two, three), runner.sent)
        assertEquals(ClickerSequence.State.STOPPED, runner.sequence.state)
    }

    @Test fun singleTargetLoopsUntilStoppedAndCancelsLongDelay() {
        val runner = Runner()
        runner.sequence.play(ClickProfile(listOf(one, two)))
        runner.step(0)
        repeat(2000) { runner.complete(); runner.step(1000) }
        runner.complete()
        runner.sequence.stop()
        assertNull(runner.scheduled)
        assertEquals(List(2001) { one }, runner.sent)
    }

    @Test fun stopInvalidatesQueuedPressAndIgnoresLateCallback() {
        val runner = Runner()
        val profile = ClickProfile(listOf(one, two), multi = true)
        runner.sequence.play(profile)
        runner.step(0)
        assertTrue(checkNotNull(runner.valid)())
        runner.sequence.stop()
        assertFalse(checkNotNull(runner.valid)())
        runner.sequence.play(profile)
        assertNull(runner.scheduled)
        runner.complete()
        runner.sequence.play(profile)
        runner.step(0)
        assertEquals(listOf(one, one), runner.sent)
    }

    @Test fun pauseDoesNotDispatchAndResumeContinuesAtNextTarget() {
        val runner = Runner()
        val profile = ClickProfile(listOf(one, two), multi = true)
        runner.sequence.play(profile)
        runner.step(0)
        runner.complete()
        runner.now += 300
        runner.sequence.pause()
        assertNull(runner.scheduled)
        runner.now += 5000
        runner.sequence.play(profile)
        runner.step(700)
        runner.complete(false)
        assertNull(runner.scheduled)
        assertEquals(ClickerSequence.State.STOPPED, runner.sequence.state)
        assertEquals(listOf(one, two), runner.sent)
    }

    @Test fun modeSwitchAndGestureReservationAreExclusive() {
        val slot = GestureSlot()
        assertTrue(slot.acquire(false))
        assertTrue(slot.acquire(false))
        assertFalse(slot.enterClicker())
        slot.release(false)
        assertFalse(slot.enterClicker())
        slot.release(false)
        assertTrue(slot.enterClicker())
        assertFalse(slot.acquire(false))
        assertTrue(slot.acquire(true))
        assertFalse(slot.acquire(true))
        assertFalse(slot.acquire(false))
        slot.release(true)
        slot.leaveClicker()
        assertFalse(slot.acquire(true))
        assertTrue(slot.acquire(false))
    }
}
