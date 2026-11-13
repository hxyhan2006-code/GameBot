package com.example.universalgamebot

/** ตัวเลขจริงของเกม (ความจริงฝั่งเกม): ไว้เทียบกับสิ่งที่บอทเห็นในรุ่นหลัง · บอทห้ามอ่าน */
class MatchTruth {
    val shots = IntArray(2); val passes = IntArray(2); val passesOk = IntArray(2)
    val tacklesWon = IntArray(2); val tacklesLost = IntArray(2)
    val saves = IntArray(2); val turnovers = IntArray(2)
    private val possessionSeconds = DoubleArray(3) // HOME, AWAY, loose; excludes pauses/goal freeze
    fun recordPossession(team: Team?, dt: Float) {
        if (dt > 0 && dt.isFinite()) possessionSeconds[team?.ordinal ?: 2] += dt.toDouble()
    }
    fun possessionSeconds(team: Team?) = possessionSeconds[team?.ordinal ?: 2]
    fun defenseLine(conceded: Int): String {
        val home = possessionSeconds[0]; val away = possessionSeconds[1]
        val pct = if (home + away == 0.0) "n/a" else "%.1f%%".format(java.util.Locale.US, home * 100 / (home + away))
        return "actual conceded=$conceded turnovers=${turnovers[0]} possession=$pct (home/(home+away)) home=${(home*1000).toLong()}ms away=${(away*1000).toLong()}ms loose=${(possessionSeconds[2]*1000).toLong()}ms"
    }
    fun line() = "truth shots H${shots[0]}/A${shots[1]} passes H${passesOk[0]}of${passes[0]}/A${passesOk[1]}of${passes[1]} " +
        "tackles H${tacklesWon[0]}w${tacklesLost[0]}l/A${tacklesWon[1]}w${tacklesLost[1]}l saves H${saves[0]}/A${saves[1]} turnovers H${turnovers[0]}/A${turnovers[1]}"
}
