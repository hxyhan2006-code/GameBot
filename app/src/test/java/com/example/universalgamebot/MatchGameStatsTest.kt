package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test

class MatchGameStatsTest {
    @Test fun exactPossessionIsTimeWeightedAndLooseTimeIsSeparate() {
        val s = MatchTruth()
        s.recordPossession(Team.HOME, 2f); s.recordPossession(Team.AWAY, 1f); s.recordPossession(null, 0.5f)
        s.turnovers[0] = 14
        val line = s.defenseLine(3)
        assertTrue(line, line.contains("conceded=3 turnovers=14 possession=66.7%"))
        assertTrue(line, line.contains("loose=500ms"))
    }

    @Test fun possessionHasNoInventedPercentageBeforePlay() {
        val s = MatchTruth()
        s.recordPossession(Team.HOME, -1f); s.recordPossession(Team.HOME, Float.NaN)
        assertTrue(s.defenseLine(0).contains("possession=n/a"))
    }

    @Test fun pausedAndFinishedWorldsDoNotAccumulatePossession() {
        val w = MatchWorld(MatchParams(800f, 600f, 1f), 1L, AiLevel.EASY, true)
        w.give(w.agents[0])
        w.update(0.1f, AgentInput())
        assertEquals(0.0, w.truth.possessionSeconds(Team.HOME), 0.00001)
        w.started = true
        w.update(0.1f, AgentInput())
        assertEquals(0.1, w.truth.possessionSeconds(Team.HOME), 0.00001)
        while (!w.finished) w.update(0.05f, AgentInput())
        val home = w.truth.possessionSeconds(Team.HOME)
        val away = w.truth.possessionSeconds(Team.AWAY)
        w.update(10f, AgentInput())
        assertEquals(home, w.truth.possessionSeconds(Team.HOME), 0.0)
        assertEquals(away, w.truth.possessionSeconds(Team.AWAY), 0.0)
    }
}
