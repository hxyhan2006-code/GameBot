package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test

class MatchTelemetryTest {
    private fun obj(label: String, x: Float, y: Float, r: Float = 12f) = DetectedObject(label, Box(x-r,y-r,x+r,y+r), 1f, "test")
    private fun obs(t: Long, ball: DetectedObject?, opp: List<DetectedObject> = emptyList(), keeper: DetectedObject? = null) =
        Observation(t, t, listOfNotNull(ball, keeper) + opp, Box(0f,500f,1200f,2000f))

    @Test fun possessionIsTimeWeightedAndTurnoversSurviveLooseBallButNotCaptureGaps() {
        val s = MatchDefenseStats()
        s.observe(0, MatchPossession.HOME)
        s.observe(100, MatchPossession.HOME)
        s.observe(200, MatchPossession.HOME)
        s.observe(300, MatchPossession.LOOSE)
        s.observe(400, MatchPossession.AWAY)
        s.observe(500, MatchPossession.AWAY)
        s.observe(600, MatchPossession.AWAY)
        assertEquals(1, s.turnovers)
        assertEquals(300L, s.homeMs); assertEquals(200L, s.awayMs)
        assertEquals(60f, s.possessionPct, 0.01f)
        s.observe(800, MatchPossession.HOME)
        s.observe(1000, MatchPossession.HOME)
        s.observe(2400, MatchPossession.AWAY)
        assertEquals(1, s.turnovers)
        assertEquals(1400L, s.unknownMs)
    }

    @Test fun shortOwnershipFlickerDoesNotCountAsRepeatedTurnovers() {
        val s = MatchDefenseStats()
        s.observe(0, MatchPossession.HOME); s.observe(200, MatchPossession.HOME)
        for (t in 250L..950L step 50) s.observe(t, if (t % 100 == 0L) MatchPossession.HOME else MatchPossession.AWAY)
        assertEquals(0, s.turnovers)
        s.observe(1000, MatchPossession.AWAY); s.observe(1200, MatchPossession.AWAY)
        assertEquals(1, s.turnovers)
    }

    @Test fun probesKeepActualFrameTimesAndDistinguishKeeperAndDefenderEvidence() {
        val s = MatchShotDiagnostics(1f)
        s.start(1000)
        val b = obj("ball", 800f, 1270f, 4f)
        s.observe(obs(1299,b), emptyList(), null, b)
        assertTrue(s.report().contains("+300ms MISSING"))
        val defender = obj("away",800f,1270f)
        s.observe(obs(1310,b,listOf(defender)), listOf(defender), null,b)
        val keeper = obj("away_keeper",800f,1270f)
        s.observe(obs(1610,b,keeper=keeper), emptyList(),keeper,b)
        val report = s.report()
        assertTrue(report, report.contains("+300ms actual=+310ms"))
        assertTrue(report, report.contains("DEFENDER_BLOCK_CANDIDATE"))
        assertTrue(report, report.contains("+600ms actual=+610ms"))
        assertTrue(report, report.contains("KEEPER_CONTACT_CANDIDATE"))
    }

    @Test fun lateOrMissingFramesNeverClaimACause() {
        val s = MatchShotDiagnostics(1f); s.start(0)
        s.observe(obs(900,null), emptyList(),null,null)
        assertTrue(s.report().contains("UNKNOWN_LATE_FRAME"))
        assertFalse(s.report().contains("CONTACT_CANDIDATE"))
    }

    private fun scene(t: Long, y: Float, defender: DetectedObject? = null): Observation {
        val objects = listOf(
            obj("stick",250f,1940f,70f), obj("shoot",1000f,1940f,40f), obj("pass",850f,1940f,40f),
            DetectedObject("goal", Box(30f,1000f,70f,1200f),1f,"test"),
            DetectedObject("goal", Box(1000f,1000f,1040f,1200f),1f,"test"),
            obj("marker",830f,y), obj("home",830f,y), obj("home",900f,y-150f),
            obj("ball",850f,y,5f), obj("away_keeper",980f,1180f)
        ) + listOfNotNull(defender)
        return Observation(t,t,objects, Box(0f,500f,1200f,2000f))
    }

    @Test fun restrictedShotsDribbleIntoBandAndNeverShootOutside() {
        val brain = MatchBrain(1f, MatchBrainConfig(aimMs=0,shootRangeDp=250f))
        brain.onCommandResult(ControlCommand(250f to 1940f,300f to 1940f),true)
        val decision = brain.decide(scene(1000,1240f),1000) as ControlDecision.Move
        assertFalse(decision.cmd.taps.any { it.label == "shoot" })
        assertTrue(decision.note,decision.note.contains("shot Y band"))
    }

    @Test fun configuredBandCanShootAndCountsOnlySuccessfulCommands() {
        val brain = MatchBrain(1f, MatchBrainConfig(aimMs=0,shootRangeDp=250f))
        brain.onCommandResult(ControlCommand(250f to 1940f,300f to 1940f),true)
        val decision = brain.decide(scene(1000,1280f),1000) as ControlDecision.Move
        assertTrue(decision.note,decision.cmd.taps.any { it.label == "shoot" })
        brain.onCommandResult(decision.cmd,false)
        assertEquals(0,brain.stats.shotsInside)
        brain.onCommandResult(decision.cmd,true)
        assertEquals(1,brain.stats.shotsInside)
        assertEquals(0,brain.stats.shotsOutside)
    }

    @Test fun aMissingBallCannotAuthorizeAKickFromItsLastPosition() {
        val brain = MatchBrain(1f, MatchBrainConfig(aimMs=0,shootRangeDp=250f))
        val visible = scene(1000,1280f)
        brain.onObservation(visible)
        brain.onCommandResult(ControlCommand(250f to 1940f,300f to 1940f),true)
        val missing = Observation(1100,1100,visible.objects.filter { it.label != "ball" },visible.bounds)
        val decision = brain.decide(missing,1100) as ControlDecision.Move
        assertTrue(decision.note,decision.cmd.taps.isEmpty())
    }

    @Test fun openPassLaneCanIssueAPass() {
        val brain = MatchBrain(1f, MatchBrainConfig(aimMs=0,shootRangeDp=20f))
        brain.onCommandResult(ControlCommand(250f to 1940f,300f to 1940f),true)
        val decision = brain.decide(scene(1000,1280f),1000) as ControlDecision.Move
        assertTrue(decision.note, decision.cmd.taps.any { it.label == "pass" })
    }

    @Test fun aKeeperInThePassLaneAlsoBlocksThePass() {
        val brain = MatchBrain(1f, MatchBrainConfig(aimMs=0,shootRangeDp=20f))
        brain.onCommandResult(ControlCommand(250f to 1940f,300f to 1940f),true)
        val base = scene(1000,1280f)
        val withKeeper = Observation(base.seq,base.timeMs,base.objects.filter { it.label != "away_keeper" } + obj("away_keeper",870f,1200f),base.bounds)
        val decision = brain.decide(withKeeper,1000) as ControlDecision.Move
        assertFalse(decision.note,decision.cmd.taps.any { it.label == "pass" })
    }

    @Test fun blockedPassLaneCannotIssueAPass() {
        val brain = MatchBrain(1f, MatchBrainConfig(aimMs=0,shootRangeDp=20f))
        brain.onCommandResult(ControlCommand(250f to 1940f,300f to 1940f),true)
        val decision = brain.decide(scene(1000,1280f,obj("away",870f,1200f)),1000) as ControlDecision.Move
        assertFalse(decision.note, decision.cmd.taps.any { it.label == "pass" })
    }
}
