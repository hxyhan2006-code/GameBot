package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test

class EFootballTrainingTest {
    private val beginning = 10_000_000_000L
    private val config = EFootballTrainingConfig()
    private val policy = EFootballTrainingPolicy(config)
    private fun player(id: Long, x: Float, y: Float, team: EFootballTeam = EFootballTeam.OWN) =
        EFootballPlayer(id, team, EFootballPoint(x, y), 0.98f)
    private fun frame(sequence: Long = 1, possession: EFootballPossession = EFootballPossession.OWN) = EFootballTrainingFrame(
        generation = 4, sequence = sequence, sourceNs = beginning + sequence * 100_000_000L,
        geometryId = 7, scene = EFootballScene.TRAINING_LIVE, attackDirection = EFootballDirection.RIGHT,
        possession = possession, players = listOf(player(1, 0.35f, 0.5f), player(2, 0.60f, 0.5f)),
        ball = EFootballBall(EFootballPoint(0.35f, 0.5f), 0.98f), selectedPlayerId = 1,
        controlsRecognized = true, teamConfidence = 0.98f, playerCoverageConfirmed = true
    )
    private fun mirror(f: EFootballTrainingFrame) = f.copy(
        attackDirection = EFootballDirection.LEFT,
        players = f.players.map { it.copy(position = EFootballPoint(1f - it.position.x, it.position.y)) },
        ball = f.ball?.let { it.copy(position = EFootballPoint(1f - it.position.x, it.position.y)) }
    )
    private fun session() = EFootballTrainingSession(4, 7, beginning)
    private fun ready(s: EFootballTrainingSession, f: EFootballTrainingFrame = frame(2)): EFootballTrainingDecision {
        s.decide(EFootballTrainingSession.TARGET, f.copy(sequence = 1, sourceNs = beginning + 100_000_000L), beginning + 100_000_000L)
        return s.decide(EFootballTrainingSession.TARGET, f, f.sourceNs)
    }

    @Test fun mirroredPitchProducesMirroredTargetsAndDoesNotAssumeAttackRight() {
        val original = policy.decide(frame()).intent as EFootballTrainingIntent.Pass
        val reversed = policy.decide(mirror(frame())).intent as EFootballTrainingIntent.Pass
        assertEquals(original.receiverId, reversed.receiverId)
        assertEquals(1f - original.target.x, reversed.target.x, 0.0001f)
        val forwardShot = frame().copy(players = listOf(player(1, 0.85f, 0.5f)), ball = EFootballBall(EFootballPoint(0.85f, 0.5f), 1f))
        assertEquals(1f, (policy.decide(forwardShot).intent as EFootballTrainingIntent.Shoot).target.x, 0f)
        assertEquals(0f, (policy.decide(mirror(forwardShot)).intent as EFootballTrainingIntent.Shoot).target.x, 0f)
    }

    @Test fun blockedPassIsNotIssuedAndUnobservedBallNeverQueuesKick() {
        val blocked = frame().copy(players = frame().players + player(3, 0.47f, 0.5f, EFootballTeam.OPPONENT))
        assertFalse(policy.decide(blocked).intent is EFootballTrainingIntent.Pass)
        for (f in listOf(frame().copy(ball = null), frame().copy(selectedPlayerId = null), frame().copy(attackDirection = null),
                frame().copy(possession = EFootballPossession.LOOSE), frame().copy(teamConfidence = Float.NaN), frame().copy(playerCoverageConfirmed = false))) {
            assertTrue(policy.decide(f).intent is EFootballTrainingIntent.Neutral)
        }
    }

    @Test fun selectedPlayerIsRequiredRatherThanUsingNearestToBall() {
        val f = frame().copy(selectedPlayerId = 2)
        assertTrue(policy.decide(f).intent is EFootballTrainingIntent.Neutral)
        val ambiguous = frame().copy(players = frame().players + player(1, 0.4f, 0.4f))
        assertTrue(policy.decide(ambiguous).intent is EFootballTrainingIntent.Neutral)
    }

    @Test fun uncertainDefenderCannotBeFilteredOutToCreateAnEmptyLane() {
        val uncertain = player(3, 0.47f, 0.5f, EFootballTeam.OPPONENT).copy(confidence = 0.7f)
        val f = frame().copy(players = frame().players + uncertain)
        assertTrue(policy.decide(f).intent is EFootballTrainingIntent.Neutral)
    }

    @Test fun defenseChoosesGoalSideCoverAndRequiresManualMarkerConfirmation() {
        val s = session()
        val f = frame(2, EFootballPossession.OPPONENT).copy(
            players = listOf(player(1, 0.85f, 0.5f), player(2, 0.35f, 0.5f), player(3, 0.5f, 0.5f, EFootballTeam.OPPONENT)),
            ball = EFootballBall(EFootballPoint(0.50f, 0.5f), 1f)
        )
        val d = ready(s, f)
        assertEquals(2L, (d.intent as EFootballTrainingIntent.Switch).playerId)
        assertNull(s.reserveDispatch(d, EFootballTrainingSession.TARGET, f, f.sourceNs))
        s.commandResult(f.sequence, d, true, f.sourceNs + 50_000_000L)
        val waiting = f.copy(sequence = 3, sourceNs = f.sourceNs + 100_000_000L)
        assertTrue(s.decide(EFootballTrainingSession.TARGET, waiting, waiting.sourceNs).intent is EFootballTrainingIntent.Wait)
        val confirmed = waiting.copy(sequence = 4, sourceNs = waiting.sourceNs + 100_000_000L, selectedPlayerId = 2)
        assertTrue(s.decide(EFootballTrainingSession.TARGET, confirmed, confirmed.sourceNs).intent is EFootballTrainingIntent.Move)
    }

    @Test fun unconfirmedManualSwitchAbortsWithoutRepeatedSwitchSpam() {
        val s = session()
        val f = frame(2, EFootballPossession.OPPONENT).copy(players = listOf(player(2, 0.2f, 0.5f)), selectedPlayerId = null)
        val d = ready(s, f)
        assertTrue(d.intent is EFootballTrainingIntent.Switch)
        assertNull(s.reserveDispatch(d, EFootballTrainingSession.TARGET, f, f.sourceNs))
        s.commandResult(2, d, true, f.sourceNs)
        val later = f.copy(sequence = 3, sourceNs = f.sourceNs + 950_000_000L)
        assertTrue(s.decide(EFootballTrainingSession.TARGET, later, later.sourceNs).intent is EFootballTrainingIntent.Stop)
    }

    @Test fun unknownScenesReleaseAndResumeNeedsStableFreshTrainingFrames() {
        val s = session()
        ready(s)
        val paused = frame(3).copy(scene = EFootballScene.PAUSED)
        assertTrue(s.decide(EFootballTrainingSession.TARGET, paused, paused.sourceNs).intent is EFootballTrainingIntent.Neutral)
        val resumed = frame(4)
        assertTrue(s.decide(EFootballTrainingSession.TARGET, resumed, resumed.sourceNs).intent is EFootballTrainingIntent.Neutral)
        assertTrue(s.decide(EFootballTrainingSession.TARGET, frame(5), frame(5).sourceNs).intent is EFootballTrainingIntent.Pass)
        val actualMatch = frame(6).copy(scene = EFootballScene.MATCH)
        assertTrue(s.decide(EFootballTrainingSession.TARGET, actualMatch, actualMatch.sourceNs).intent is EFootballTrainingIntent.Stop)
        assertTrue(s.decide(EFootballTrainingSession.TARGET, frame(7), frame(7).sourceNs).intent is EFootballTrainingIntent.Stop)
    }

    @Test fun focusLossNullGeometryAndDeadlineLatchStop() {
        val f = frame(2)
        for (pkg in listOf(null, "com.android.settings")) {
            val s = session()
            ready(s)
            assertTrue(s.decide(pkg, f, f.sourceNs).intent is EFootballTrainingIntent.Stop)
            assertTrue(s.decide(EFootballTrainingSession.TARGET, frame(3), frame(3).sourceNs).intent is EFootballTrainingIntent.Stop)
        }
        assertTrue(session().decide(EFootballTrainingSession.TARGET, f.copy(geometryId = 8), f.sourceNs).intent is EFootballTrainingIntent.Stop)
        assertTrue(session().decide(EFootballTrainingSession.TARGET, f, beginning + 120_000_000_000L).intent is EFootballTrainingIntent.Stop)
        assertTrue(session().decide(EFootballTrainingSession.TARGET, f, f.sourceNs, emergency = true).intent is EFootballTrainingIntent.Stop)
    }

    @Test fun staleFutureAndDuplicateSourcesNeverAuthorizeAction() {
        for (offset in listOf(-1L, 1_000_000_000L)) {
            val f = frame(2)
            val s = session()
            s.decide(EFootballTrainingSession.TARGET, frame(1), frame(1).sourceNs)
            assertTrue(s.decide(EFootballTrainingSession.TARGET, f, f.sourceNs + offset).intent is EFootballTrainingIntent.Neutral)
        }
        val s = session()
        val d = ready(s)
        assertTrue(s.decide(EFootballTrainingSession.TARGET, d.frame, d.frame.sourceNs).intent is EFootballTrainingIntent.Neutral)
        assertTrue(s.decide(EFootballTrainingSession.TARGET, frame(3).copy(sourceNs = d.frame.sourceNs), d.frame.sourceNs).intent is EFootballTrainingIntent.Neutral)
    }

    @Test fun dispatchRechecksScenePlayerPackageFreshnessAndCannotReplayOrForgeDecision() {
        val s = session()
        val d = ready(s)
        val f = d.frame
        assertNotNull(s.reserveDispatch(d.copy(), EFootballTrainingSession.TARGET, f, f.sourceNs))
        assertNotNull(s.reserveDispatch(d, EFootballTrainingSession.TARGET, f.copy(selectedPlayerId = 2), f.sourceNs))
        assertNotNull(s.reserveDispatch(d, EFootballTrainingSession.TARGET, f.copy(scene = EFootballScene.REPLAY), f.sourceNs))
        assertNotNull(s.reserveDispatch(d, EFootballTrainingSession.TARGET, f, f.sourceNs + 201_000_000L))
        assertNull(s.reserveDispatch(d, EFootballTrainingSession.TARGET, f, f.sourceNs))
        assertNotNull(s.reserveDispatch(d, EFootballTrainingSession.TARGET, f, f.sourceNs))
        s.commandResult(2, d.copy(), true, f.sourceNs)
        assertNotNull(s.reserveDispatch(d, EFootballTrainingSession.TARGET, f, f.sourceNs))
        s.commandResult(2, d, true, f.sourceNs)
        assertNotNull(s.reserveDispatch(d, EFootballTrainingSession.TARGET, f, f.sourceNs))
        val next = s.decide(EFootballTrainingSession.TARGET, frame(3), frame(3).sourceNs)
        assertNotNull(s.reserveDispatch(next, "other.app", next.frame, next.frame.sourceNs))
        assertTrue(s.decide(EFootballTrainingSession.TARGET, frame(4), frame(4).sourceNs).intent is EFootballTrainingIntent.Stop)
    }

    @Test fun cancelledAndTimedOutCommandsDoNotPermitOverlappingRetry() {
        val s = session()
        val d = ready(s)
        assertNull(s.reserveDispatch(d, EFootballTrainingSession.TARGET, d.frame, d.frame.sourceNs))
        assertTrue(s.decide(EFootballTrainingSession.TARGET, frame(3), frame(3).sourceNs).intent is EFootballTrainingIntent.Wait)
        s.commandResult(2, d, false, d.frame.sourceNs + 50_000_000L)
        assertTrue(s.decide(EFootballTrainingSession.TARGET, frame(4), frame(4).sourceNs).intent is EFootballTrainingIntent.Stop)
        val t = session()
        val td = ready(t)
        assertNull(t.reserveDispatch(td, EFootballTrainingSession.TARGET, td.frame, td.frame.sourceNs))
        val afterTimeout = frame(13)
        assertTrue(t.decide(EFootballTrainingSession.TARGET, afterTimeout, afterTimeout.sourceNs).intent is EFootballTrainingIntent.Stop)
    }

    @Test fun changedBallReceiverOrOriginalMutableListCannotAuthorizeOldPass() {
        val s = session()
        val tracks = frame(2).players.toMutableList()
        val f = frame(2).copy(players = tracks)
        val d = ready(s, f)
        assertTrue(d.intent is EFootballTrainingIntent.Pass)
        assertNotNull(s.reserveDispatch(d, EFootballTrainingSession.TARGET, f.copy(ball = null), f.sourceNs))
        assertNotNull(s.reserveDispatch(d, EFootballTrainingSession.TARGET, f.copy(players = listOf(tracks.first())), f.sourceNs))
        assertNotNull(s.reserveDispatch(d, EFootballTrainingSession.TARGET, f.copy(sequence = 3, sourceNs = f.sourceNs + 10_000_000L), f.sourceNs + 10_000_000L))
        tracks.clear()
        assertNotNull(s.reserveDispatch(d, EFootballTrainingSession.TARGET, f, f.sourceNs))
    }

    @Test fun gesturePlansKeepPointerTimingExplicitAndRejectOutsideOrOverlappingStrokes() {
        val zones = mapOf(0 to Box(0f, 0f, 100f, 100f), 1 to Box(200f, 0f, 300f, 100f))
        val plan = EFootballGesturePlan.moveAndPress(EFootballScreenPoint(50f, 50f), EFootballScreenPoint(80f, 50f),
            EFootballScreenPoint(250f, 50f), 200, 35, 120)
        assertNull(plan.rejection(zones))
        assertEquals(120L, plan.strokes[1].keyframes.last().elapsedMs)
        assertNotNull(plan.rejection(zones, 100))
        assertNotNull(plan.copy(strokes = plan.strokes + plan.strokes[0]).rejection(zones))
        val bad = plan.strokes[0].copy(keyframes = listOf(EFootballGestureKeyframe(0, EFootballScreenPoint(Float.NaN, 10f)),
            EFootballGestureKeyframe(200, EFootballScreenPoint(50f, 50f))))
        assertNotNull(EFootballGesturePlan(listOf(bad)).rejection(zones))
        assertNull(EFootballSkillCatalogue().find("double_touch"))
    }

    @Test fun missingControlsCannotEraseSwitchConfirmationOrItsDeadline() {
        val s = session()
        val f = frame(2, EFootballPossession.OPPONENT).copy(players = listOf(player(2, 0.2f, 0.5f)), selectedPlayerId = null)
        val d = ready(s, f)
        assertNull(s.reserveDispatch(d, EFootballTrainingSession.TARGET, f, f.sourceNs))
        s.commandResult(2, d, true, f.sourceNs)
        val missing = f.copy(sequence = 3, sourceNs = f.sourceNs + 100_000_000L, controlsRecognized = false)
        assertTrue(s.decide(EFootballTrainingSession.TARGET, missing, missing.sourceNs).intent is EFootballTrainingIntent.Neutral)
        val recovering = f.copy(sequence = 4, sourceNs = f.sourceNs + 200_000_000L)
        assertTrue(s.decide(EFootballTrainingSession.TARGET, recovering, recovering.sourceNs).intent is EFootballTrainingIntent.Neutral)
        val stillWrong = f.copy(sequence = 5, sourceNs = f.sourceNs + 300_000_000L)
        assertTrue(s.decide(EFootballTrainingSession.TARGET, stillWrong, stillWrong.sourceNs).intent is EFootballTrainingIntent.Wait)
        val expired = f.copy(sequence = 6, sourceNs = f.sourceNs + 950_000_000L)
        assertTrue(s.decide(EFootballTrainingSession.TARGET, expired, expired.sourceNs).intent is EFootballTrainingIntent.Stop)
    }

    @Test fun phaseChangeWhileGestureInFlightRequestsTerminalStopImmediately() {
        val s = session()
        val d = ready(s)
        assertNull(s.reserveDispatch(d, EFootballTrainingSession.TARGET, d.frame, d.frame.sourceNs))
        val pause = frame(3).copy(scene = EFootballScene.PAUSED)
        assertTrue(s.decide(EFootballTrainingSession.TARGET, pause, pause.sourceNs).intent is EFootballTrainingIntent.Stop)
        s.commandResult(2, d, true, pause.sourceNs)
        assertTrue(s.decide(EFootballTrainingSession.TARGET, frame(4), frame(4).sourceNs).intent is EFootballTrainingIntent.Stop)
    }
}
