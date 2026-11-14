package com.example.universalgamebot

/**
 * Pure session/dispatch policy, deliberately not connected to Android Accessibility.
 * A future perception adapter must establish TRAINING_* scenes from actual game evidence.
 */
class EFootballTrainingSession(
    private val generation: Long,
    private val geometryId: Long,
    private val startedNs: Long,
    private val config: EFootballTrainingConfig = EFootballTrainingConfig(),
    private val policy: EFootballTrainingPolicy = EFootballTrainingPolicy(config)
) {
    companion object { const val TARGET = "jp.konami.pesam" }
    private var terminal: String? = null
    private var previousSequence = -1L
    private var previousSourceNs = 0L
    private var stableLiveFrames = 0
    private var awaitingSwitch: Pair<Long, Long>? = null
    private var lastSwitchSequence = -1L
    private var approvedDecision: EFootballTrainingDecision? = null
    private var inFlightDecision: EFootballTrainingDecision? = null
    private var dispatchedAtNs = 0L

    init { require(generation > 0 && geometryId > 0 && startedNs > 0) }

    @Synchronized fun decide(packageName: String?, frame: EFootballTrainingFrame, nowNs: Long, emergency: Boolean = false): EFootballTrainingDecision {
        fun neutral(reason: String) = EFootballTrainingDecision(frame, EFootballTrainingIntent.Neutral(reason), reason)
        fun stop(reason: String): EFootballTrainingDecision {
            terminal = reason
            awaitingSwitch = null
            approvedDecision = null
            return EFootballTrainingDecision(frame, EFootballTrainingIntent.Stop(reason), reason)
        }
        terminal?.let { return stop(it) }
        scopeFailure(packageName, frame, nowNs, emergency)?.let { return stop(it) }
        if (inFlightDecision != null) {
            if (nowNs - dispatchedAtNs > config.commandTimeoutMs * 1_000_000L) return stop("command callback timeout")
            if (!fresh(frame, nowNs) || frame.scene !in liveScenes || !frame.controlsRecognized) return stop("play evidence lost while gesture in flight")
            return EFootballTrainingDecision(frame, EFootballTrainingIntent.Wait("command still in flight"), "command still in flight")
        }
        awaitingSwitch?.let { (_, beganNs) ->
            if (nowNs - beganNs > config.switchTimeoutMs * 1_000_000L) return stop("manual switch target not confirmed")
        }
        if (!fresh(frame, nowNs)) { stableLiveFrames = 0; return neutral("source frame not fresh") }
        if (frame.sequence <= previousSequence || frame.sourceNs <= previousSourceNs) return neutral("duplicate or out-of-order observation")
        previousSequence = frame.sequence
        previousSourceNs = frame.sourceNs
        if (frame.scene !in liveScenes || !frame.controlsRecognized) {
            stableLiveFrames = 0
            return neutral("release for non-live or unrecognized controls")
        }
        stableLiveFrames++
        if (stableLiveFrames < 2) return neutral("waiting for stable training play")
        awaitingSwitch?.let { (expected, beganNs) ->
            if (frame.sequence <= lastSwitchSequence) return neutral("awaiting newer switch evidence")
            if (frame.selectedPlayerId == expected) awaitingSwitch = null
            else if (nowNs - beganNs > config.switchTimeoutMs * 1_000_000L) return stop("manual switch target not confirmed")
            else return EFootballTrainingDecision(frame, EFootballTrainingIntent.Wait("awaiting controlled-player marker"), "awaiting controlled-player marker")
        }
        return policy.decide(frame.copy(players = frame.players.toList())).also { decision ->
            if (decision.intent !is EFootballTrainingIntent.Neutral && decision.intent !is EFootballTrainingIntent.Wait && decision.intent !is EFootballTrainingIntent.Stop) {
                approvedDecision = decision
            }
        }
    }

    /** Must be invoked immediately before a backend dispatch, with current foreground and observation. */
    @Synchronized fun reserveDispatch(
        decision: EFootballTrainingDecision,
        packageName: String?,
        current: EFootballTrainingFrame,
        nowNs: Long,
        emergency: Boolean = false
    ): String? {
        terminal?.let { return it }
        scopeFailure(packageName, current, nowNs, emergency)?.let { terminal = it; return it }
        if (inFlightDecision != null) return "gesture busy"
        val basis = decision.frame
        if (decision !== approvedDecision || basis.generation != generation || basis.geometryId != geometryId) return "decision is not current session output"
        if (current != basis) return "observation changed; replan before dispatch"
        if (basis.sequence != previousSequence || basis.sourceNs != previousSourceNs) return "decision superseded"
        if (!fresh(basis, nowNs) || !fresh(current, nowNs)) return "command expired"
        if (current.sequence < basis.sequence || current.sourceNs < basis.sourceNs) return "current evidence predates decision"
        if (current.scene !in liveScenes || current.scene != basis.scene || !current.controlsRecognized) return "scene or controls changed"
        if (!current.playerCoverageConfirmed) return "player coverage lost"
        if (current.attackDirection != basis.attackDirection || current.selectedPlayerId != basis.selectedPlayerId || current.possession != basis.possession) return "player, direction or possession changed"
        if (!current.teamConfidence.isFinite() || current.teamConfidence < config.minConfidence || current.teamConfidence > 1f) return "team uncertain"
        if (decision.intent is EFootballTrainingIntent.Neutral || decision.intent is EFootballTrainingIntent.Wait || decision.intent is EFootballTrainingIntent.Stop) return "decision has no physical action"
        inFlightDecision = decision
        approvedDecision = null
        dispatchedAtNs = nowNs
        return null
    }

    /** Callbacks are correlated to their observation; a late callback cannot complete a new command. */
    @Synchronized fun commandResult(sequence: Long, decision: EFootballTrainingDecision, completed: Boolean, nowNs: Long) {
        if (terminal != null || inFlightDecision !== decision || decision.frame.sequence != sequence) return
        inFlightDecision = null
        if (nowNs < dispatchedAtNs || nowNs - dispatchedAtNs > config.commandTimeoutMs * 1_000_000L) {
            terminal = "command callback outside time budget"
            return
        }
        if (!completed) {
            terminal = "gesture cancelled or rejected; held-pointer state must be reset"
            return
        }
        (decision.intent as? EFootballTrainingIntent.Switch)?.let {
            awaitingSwitch = it.playerId to nowNs
            lastSwitchSequence = sequence
        }
    }

    @Synchronized fun stop(reason: String) { terminal = reason; awaitingSwitch = null; approvedDecision = null }

    private fun fresh(frame: EFootballTrainingFrame, nowNs: Long) = frame.sourceNs >= startedNs &&
        frame.sourceNs <= nowNs && nowNs - frame.sourceNs <= config.maxFrameAgeMs * 1_000_000L

    private fun scopeFailure(pkg: String?, frame: EFootballTrainingFrame, nowNs: Long, emergency: Boolean): String? = when {
        emergency -> "emergency stop"
        pkg != TARGET -> "target focus lost or unknown"
        frame.generation != generation -> "session generation changed"
        frame.geometryId != geometryId -> "capture geometry changed"
        nowNs < startedNs || nowNs - startedNs >= config.maxDurationMs * 1_000_000L -> "training deadline"
        frame.scene == EFootballScene.MATCH -> "match scene outside training scope"
        else -> null
    }

    private val liveScenes = setOf(EFootballScene.TRAINING_LIVE, EFootballScene.TRAINING_KICKOFF)
}
