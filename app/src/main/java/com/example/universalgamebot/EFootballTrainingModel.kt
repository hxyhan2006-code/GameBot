package com.example.universalgamebot

import kotlin.math.hypot

/** Pitch coordinates are independent of the moving camera. They are not screen pixels or metres. */
data class EFootballPoint(val x: Float, val y: Float) {
    fun valid() = x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f
    fun distance(other: EFootballPoint) = hypot((x - other.x) * 1.5f, y - other.y)
}

enum class EFootballTeam { OWN, OPPONENT }
enum class EFootballDirection { LEFT, RIGHT }
enum class EFootballPossession { OWN, OPPONENT, LOOSE, UNKNOWN }
enum class EFootballScene { TRAINING_LIVE, TRAINING_KICKOFF, TRAINING_DEAD_BALL, PAUSED, REPLAY, MATCH, UNKNOWN }

data class EFootballPlayer(
    val id: Long,
    val team: EFootballTeam,
    val position: EFootballPoint,
    val confidence: Float,
    val goalkeeper: Boolean = false
)

data class EFootballBall(val position: EFootballPoint, val confidence: Float)

/** This contract must eventually be supplied by real eFootball perception, not simulator state. */
data class EFootballTrainingFrame(
    val generation: Long,
    val sequence: Long,
    val sourceNs: Long,
    val geometryId: Long,
    val scene: EFootballScene,
    val attackDirection: EFootballDirection?,
    val possession: EFootballPossession,
    val players: List<EFootballPlayer>,
    val ball: EFootballBall?,
    val selectedPlayerId: Long?,
    val controlsRecognized: Boolean,
    val teamConfidence: Float,
    val playerCoverageConfirmed: Boolean = false
)

/** Values are provisional policy parameters, not measurements validated on the owner's phone. */
data class EFootballTrainingConfig(
    val maxFrameAgeMs: Long = 200,
    val minConfidence: Float = 0.85f,
    val switchTimeoutMs: Long = 900,
    val commandTimeoutMs: Long = 1000,
    val maxDurationMs: Long = 120_000,
    val pressureDistance: Float = 0.12f,
    val passCorridor: Float = 0.065f,
    val minPassDistance: Float = 0.10f,
    val maxPassDistance: Float = 0.70f,
    val shootingProgress: Float = 0.78f,
    val shootingHalfWidth: Float = 0.25f
) {
    init {
        require(maxFrameAgeMs in 1..500)
        require(minConfidence in 0f..1f && minConfidence.isFinite())
        require(switchTimeoutMs in 1..3000 && commandTimeoutMs in 1..3000)
        require(maxDurationMs in 1..120_000)
        require(pressureDistance > 0f && pressureDistance.isFinite())
        require(passCorridor > 0f && passCorridor.isFinite())
        require(minPassDistance > 0f && maxPassDistance > minPassDistance && maxPassDistance <= 2f)
        require(shootingProgress in 0f..1f && shootingHalfWidth in 0f..0.5f)
    }
}

/** Semantic intentions only. None of these objects dispatches a gesture. */
sealed class EFootballTrainingIntent {
    data class Move(val target: EFootballPoint, val dash: Boolean, val matchUp: Boolean = false) : EFootballTrainingIntent()
    data class Pass(val receiverId: Long, val target: EFootballPoint) : EFootballTrainingIntent()
    data class Shoot(val target: EFootballPoint) : EFootballTrainingIntent()
    data class Switch(val playerId: Long, val target: EFootballPoint) : EFootballTrainingIntent()
    data class Wait(val reason: String) : EFootballTrainingIntent()
    data class Neutral(val reason: String) : EFootballTrainingIntent()
    data class Stop(val reason: String) : EFootballTrainingIntent()
}

/** Binds a decision to the observation and selected player that produced it. */
data class EFootballTrainingDecision(
    val frame: EFootballTrainingFrame,
    val intent: EFootballTrainingIntent,
    val reason: String
)
