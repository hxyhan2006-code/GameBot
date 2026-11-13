package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class MatchWorldTest {
    private val d = 3.25f
    private val p = MatchParams.forArena(1176f, 1455f, d)
    private val none = AgentInput()

    private fun play(w: MatchWorld, seconds: Float, dt: Float = 1f / 60f, each: (MatchWorld) -> Unit = {}) {
        var t = 0f
        while (t < seconds && !w.finished) { w.update(dt, none); each(w); t += dt }
    }

    private fun started(seed: Long, level: AiLevel = AiLevel.EASY, human: Boolean = false) = MatchWorld(p, seed, level, human).also { it.started = true }

    @Test fun nothingMovesBeforeStart() {
        val w = MatchWorld(p, 1)
        val x = w.agents[0].x
        play(w, 2f)
        assertEquals(x, w.agents[0].x, 0f)
        assertEquals(p.matchSeconds, w.timeLeft, 0f)
    }

    @Test fun sameSeedGivesSameMatch() {
        fun run(): String { val w = started(5); play(w, 60f); return "${w.homeGoals}-${w.awayGoals} ${w.bx.toInt()},${w.by.toInt()} ${w.truth.line()}" }
        assertEquals(run(), run())
    }

    @Test fun matchEndsAfterTheClockAndStaysEnded() {
        val w = started(2)
        var ended = 0
        w.onFinish = { ended++ }
        play(w, 400f) // นาฬิกาหยุดระหว่างฉลองประตู จึงใช้เวลาจำลองนานกว่า 120 วินาทีได้
        assertTrue(w.finished); assertEquals(0f, w.timeLeft, 0f); assertEquals(1, ended)
        val g = w.homeGoals + w.awayGoals
        play(w, 5f)
        assertEquals(g, w.homeGoals + w.awayGoals)
    }

    @Test fun playersNeverOverlapAndNeverLeaveTheField() {
        var worst = Float.MAX_VALUE
        for (seed in 1L..6L) {
            val w = started(seed, if (seed % 2 == 0L) AiLevel.NORMAL else AiLevel.HARD)
            play(w, p.matchSeconds) { world ->
                val a = world.agents
                for (i in a.indices) {
                    val m = a[i]
                    assertTrue("x ${m.x}", m.x in 0f..p.w && m.y in 0f..p.h)
                    for (j in i + 1 until a.size) {
                        val gap = hypot(a[i].x - a[j].x, a[i].y - a[j].y) - world.rad(a[i]) - world.rad(a[j])
                        if (gap < worst) worst = gap
                    }
                }
            }
        }
        assertTrue("closest gap ${worst / d} dp", worst >= 1f * d)
    }

    @Test fun ballStaysInsideTheFieldAndGoalsAreScoredInBothDirections() {
        var home = 0; var away = 0
        for (seed in 1L..8L) {
            val w = started(seed, AiLevel.NORMAL)
            play(w, p.matchSeconds) { world ->
                assertTrue("ball ${world.bx},${world.by}", world.bx in 0f..p.w && world.by in 0f..p.h)
            }
            home += w.homeGoals; away += w.awayGoals
        }
        assertTrue("home=$home away=$away", home > 0 && away > 0)
    }

    @Test fun shotThroughTheMouthScoresAndOutsideTheMouthDoesNot() {
        val w = started(3, human = true)
        w.placeBall(p.rightLineX - 40f * d, p.h / 2f, 300f * d, 0f)
        w.agents.filter { it.keeper }.forEach { it.stun = 5f; w.place(it, it.x, 0f) } // เอาผู้รักษาประตูออกจากทาง
        var scored = false; w.onGoal = { if (it == Team.HOME) scored = true }
        play(w, 1f)
        assertTrue(scored); assertEquals(1, w.homeGoals)

        val w2 = started(3, human = true)
        w2.placeBall(p.rightLineX - 40f * d, p.mouthTop - 30f * d, 300f * d, 0f)
        play(w2, 1f)
        assertEquals(0, w2.homeGoals)
        assertTrue(w2.bx <= p.rightLineX)
    }

    @Test fun awayGoalIsScoredOnTheLeftSide() {
        val w = started(3, human = true)
        w.placeBall(p.leftLineX + 40f * d, p.h / 2f, -300f * d, 0f)
        w.agents.filter { it.keeper }.forEach { it.stun = 5f }
        play(w, 1f)
        assertEquals(1, w.awayGoals)
    }

    @Test fun kickoffRestartsAfterAGoal() {
        val w = started(3, human = true)
        w.placeBall(p.rightLineX - 40f * d, p.h / 2f, 300f * d, 0f)
        w.agents.filter { it.keeper }.forEach { it.stun = 5f }
        play(w, 0.2f)
        assertTrue(w.inGoalFreeze)
        play(w, p.resetDelayS + 0.3f)
        assertEquals(p.w / 2f, w.bx, 1f); assertFalse(w.inGoalFreeze)
    }

    @Test fun humanKickUsesStickDirectionAndPassSnapsToAMate() {
        val w = started(4, human = true)
        val me = w.controlled
        val mate = w.agents.first { it.team == Team.HOME && !it.keeper && it.id != me.id }
        w.place(me, p.w * 0.4f, p.h * 0.5f); w.place(mate, p.w * 0.6f, p.h * 0.52f) // เพื่อนอยู่เกือบตรงทิศขวา
        w.give(me)
        val inp = AgentInput().apply { mx = 1f; my = 0f; pass = true }
        w.update(1f / 60f, inp)
        assertNull(w.carrier)
        assertTrue("ball should head towards the mate (slightly down) vy=${w.bvy}", w.bvx > 0f && w.bvy > 0f)
        assertEquals(1, w.truth.passes[0])
    }

    @Test fun tackleSucceedsSometimesAndFailureStunsTheTackler() {
        var won = 0; var lost = 0; var stunned = 0
        for (seed in 1L..200L) {
            val w = started(seed, human = true)
            val me = w.agents[0]; val opp = w.agents[3]
            w.place(me, p.w * 0.5f, p.h * 0.5f); w.place(opp, p.w * 0.5f + 28f * d, p.h * 0.5f)
            opp.fx = -1f; opp.fy = 0f
            w.give(opp)
            w.update(1f / 60f, AgentInput().apply { tackle = true })
            won += w.truth.tacklesWon[0]; lost += w.truth.tacklesLost[0]
            if (w.truth.tacklesLost[0] == 1 && me.stun > 0f) stunned++
        }
        assertEquals(200, won + lost)
        val rate = won / 200f
        assertTrue("tackle rate $rate", rate in 0.40f..0.70f)
        assertEquals(lost, stunned)
    }

    @Test fun keeperCatchesSlowBallsAndDeflectsFastOnes() {
        val w = started(6, human = true)
        val k = w.agents[5]
        w.placeBall(k.x - 30f * d, k.y, 100f * d, 0f)
        play(w, 0.5f)
        assertSame(k, w.carrier)

        val w2 = started(6, human = true)
        val k2 = w2.agents[5]
        w2.placeBall(k2.x - 30f * d, k2.y, 340f * d, 0f)
        play(w2, 0.3f)
        assertNull(w2.carrier); assertTrue(w2.bvx < 0f)
    }

    @Test fun difficultyIsMonotonic() {
        // ทีม HOME ทั้งหมดเป็น AI ระดับปกติ (ไม่มีมนุษย์) สู้กับระดับต่างกัน: ยิ่งคู่แข่งง่าย ยิ่งชนะมาก
        fun winRate(level: AiLevel): Float {
            var win = 0; var n = 0
            for (seed in 1L..24L) { val w = started(seed, level); play(w, p.matchSeconds); if (w.result > 0) win++; n++ }
            return win / n.toFloat()
        }
        val easy = winRate(AiLevel.EASY); val hard = winRate(AiLevel.HARD)
        assertTrue("normal-AI wins vs easy=$easy vs hard=$hard", easy > hard)
    }
}
