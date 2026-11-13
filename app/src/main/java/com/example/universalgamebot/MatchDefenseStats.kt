package com.example.universalgamebot

enum class MatchPossession { HOME, AWAY, LOOSE, UNKNOWN }

/** Time-weighted visual estimate, never game truth. Missing frames and kickoff are unknown.
 * A turnover requires 200 ms of stable ownership on each side; loose ball may bridge <=1.5 s. */
class MatchDefenseStats {
    private val ms = LongArray(MatchPossession.values().size)
    private var at = -1L
    private var previous = MatchPossession.UNKNOWN
    private var homeLastSeen = -1L
    private var candidate = MatchPossession.UNKNOWN
    private var candidateSince = -1L
    private var confirmed = MatchPossession.UNKNOWN
    var turnovers = 0; private set
    val homeMs get() = ms[MatchPossession.HOME.ordinal]
    val awayMs get() = ms[MatchPossession.AWAY.ordinal]
    val looseMs get() = ms[MatchPossession.LOOSE.ordinal]
    val unknownMs get() = ms[MatchPossession.UNKNOWN.ordinal]
    val possessionPct get() = if (homeMs + awayMs == 0L) 0f else 100f * homeMs / (homeMs + awayMs)

    fun observe(timeMs: Long, owner: MatchPossession) {
        if (timeMs <= at) return
        val dt = if (at < 0) 0L else timeMs - at
        val gap = dt > 500
        ms[(if (gap) MatchPossession.UNKNOWN else previous).ordinal] += dt
        if (gap || owner == MatchPossession.UNKNOWN) {
            homeLastSeen = -1; confirmed = MatchPossession.UNKNOWN
            candidate = MatchPossession.UNKNOWN; candidateSince = -1
        }
        if (owner != candidate) { candidate = owner; candidateSince = timeMs }
        if (owner in listOf(MatchPossession.HOME, MatchPossession.AWAY) && timeMs - candidateSince >= 200) {
            if (owner == MatchPossession.HOME) homeLastSeen = timeMs
            else {
                if (confirmed != MatchPossession.AWAY && homeLastSeen >= 0 && timeMs - homeLastSeen <= 1500) turnovers++
                homeLastSeen = -1
            }
            confirmed = owner
        }
        previous = owner; at = timeMs
    }

    fun line() = "defense turnovers=$turnovers (ownership confirmed 200ms) possession=${"%.1f".format(java.util.Locale.US, possessionPct)}% (home/(home+away), visual estimate) home=${homeMs}ms away=${awayMs}ms loose=${looseMs}ms unknown=${unknownMs}ms"
}
