package com.example.universalgamebot

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Provisional rule policy. No OCR, hidden game state, fixed screen-Y band, or claims of pro strength. */
class EFootballTrainingPolicy(private val config: EFootballTrainingConfig = EFootballTrainingConfig()) {
    fun decide(frame: EFootballTrainingFrame): EFootballTrainingDecision {
        fun result(intent: EFootballTrainingIntent, reason: String) = EFootballTrainingDecision(frame, intent, reason)
        fun neutral(reason: String) = result(EFootballTrainingIntent.Neutral(reason), reason)
        if (frame.scene !in setOf(EFootballScene.TRAINING_LIVE, EFootballScene.TRAINING_KICKOFF)) {
            return neutral("scene has no verified live-play routine")
        }
        if (!frame.controlsRecognized || !trusted(frame.teamConfidence)) return neutral("controls or team uncertain")
        if (!frame.playerCoverageConfirmed) return neutral("player coverage incomplete; empty lanes not established")
        val direction = frame.attackDirection ?: return neutral("attack direction unknown")
        val ball = frame.ball?.takeIf { trusted(it.confidence) && it.position.valid() } ?: return neutral("ball uncertain")
        // Dropping an uncertain defender would manufacture an apparently empty passing lane.
        if (frame.players.any { !trusted(it.confidence) || !it.position.valid() }) return neutral("uncertain player track")
        val players = frame.players
        if (players.map { it.id }.distinct().size != players.size) return neutral("ambiguous player tracks")
        val own = players.filter { it.team == EFootballTeam.OWN }
        val opponents = players.filter { it.team == EFootballTeam.OPPONENT }
        val active = own.singleOrNull { it.id == frame.selectedPlayerId }
        val ownGoal = EFootballPoint(if (direction == EFootballDirection.RIGHT) 0f else 1f, 0.5f)
        val opponentGoal = EFootballPoint(1f - ownGoal.x, 0.5f)
        if (frame.possession == EFootballPossession.UNKNOWN) return neutral("possession unknown")
        if (frame.possession == EFootballPossession.LOOSE) return neutral("loose ball; do not queue kicks")

        if (frame.possession == EFootballPossession.OPPONENT) {
            // Approach from the goal side rather than dragging the nearest defender into every chase.
            val cover = EFootballPoint(
                ball.position.x * 0.75f + ownGoal.x * 0.25f,
                ball.position.y * 0.75f + ownGoal.y * 0.25f
            )
            val candidates = own.filterNot { it.goalkeeper }
            val best = candidates.minByOrNull { defenderCost(it, cover, ball.position, direction) }
                ?: return neutral("no defender track")
            if (active == null || (active.id != best.id &&
                    defenderCost(active, cover, ball.position, direction) > defenderCost(best, cover, ball.position, direction) + 0.15f)) {
                return result(EFootballTrainingIntent.Switch(best.id, best.position), "manual selection for goal-side cover")
            }
            val close = active.position.distance(ball.position) < config.pressureDistance
            return result(EFootballTrainingIntent.Move(cover, dash = !close, matchUp = close), "recover shape or block the lane")
        }

        if (active == null) return neutral("controlled player unknown")
        if (active.position.distance(ball.position) > config.pressureDistance) return neutral("ball not controlled by selected player")
        fun progress(p: EFootballPoint) = if (direction == EFootballDirection.RIGHT) p.x else 1f - p.x
        val pressured = opponents.any { it.position.distance(active.position) < config.pressureDistance }
        val shotClear = corridorClear(ball.position, opponentGoal, opponents.filterNot { it.goalkeeper })
        if (progress(ball.position) >= config.shootingProgress && abs(ball.position.y - 0.5f) <= config.shootingHalfWidth && shotClear) {
            return result(EFootballTrainingIntent.Shoot(opponentGoal), "shooting region and unblocked observed lane")
        }

        // Prefer a receiver with a route onwards, not simply the highest-X or nearest teammate.
        val receivers = own.filter { it.id != active.id && !it.goalkeeper }
            .filter { it.position.distance(ball.position) in config.minPassDistance..config.maxPassDistance }
            .filter { corridorClear(ball.position, it.position, opponents) }
            .filter { receiver -> opponents.none { it.position.distance(receiver.position) < config.passCorridor * 1.5f } }
        fun score(receiver: EFootballPlayer): Float {
            val forward = progress(receiver.position) - progress(ball.position)
            val onward = own.count { it.id != receiver.id && it.id != active.id &&
                it.position.distance(receiver.position) <= config.maxPassDistance && corridorClear(receiver.position, it.position, opponents) }
            return forward * 2f + min(onward, 2) * 0.08f - receiver.position.distance(ball.position) * 0.1f
        }
        val receiver = receivers.maxByOrNull(::score)
        if (receiver != null && (pressured || progress(receiver.position) > progress(ball.position) + 0.1f || frame.scene == EFootballScene.TRAINING_KICKOFF)) {
            return result(EFootballTrainingIntent.Pass(receiver.id, receiver.position), "open lane and observed receiver; reassess after pass")
        }
        if (frame.scene == EFootballScene.TRAINING_KICKOFF) return neutral("kickoff receiver not confirmed")

        val sign = if (direction == EFootballDirection.RIGHT) 1f else -1f
        val targets = listOf(-0.14f, 0f, 0.14f).map { dy ->
            EFootballPoint((ball.position.x + sign * 0.10f).coerceIn(0.03f, 0.97f), (ball.position.y + dy).coerceIn(0.08f, 0.92f))
        }
        val advance = targets.filter { corridorClear(ball.position, it, opponents) }
            .maxByOrNull { target -> opponents.minOfOrNull { it.position.distance(target) } ?: 1f }
            ?: return neutral("no observed dribble exit")
        return result(EFootballTrainingIntent.Move(advance, dash = !pressured), "advance towards observed space")
    }

    private fun trusted(confidence: Float) = confidence.isFinite() && confidence >= config.minConfidence && confidence <= 1f

    private fun defenderCost(player: EFootballPlayer, cover: EFootballPoint, ball: EFootballPoint, direction: EFootballDirection): Float {
        val wrongSide = if (direction == EFootballDirection.RIGHT) player.position.x > ball.x else player.position.x < ball.x
        return player.position.distance(cover) + if (wrongSide) 0.12f else 0f
    }

    private fun corridorClear(from: EFootballPoint, to: EFootballPoint, opponents: List<EFootballPlayer>): Boolean {
        val dx = (to.x - from.x) * 1.5f
        val dy = to.y - from.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared < 0.000001f) return false
        return opponents.none { opponent ->
            val ox = (opponent.position.x - from.x) * 1.5f
            val oy = opponent.position.y - from.y
            val t = (ox * dx + oy * dy) / lengthSquared
            t in 0f..1f && kotlin.math.hypot(ox - max(0f, min(1f, t)) * dx, oy - max(0f, min(1f, t)) * dy) < config.passCorridor
        }
    }
}
