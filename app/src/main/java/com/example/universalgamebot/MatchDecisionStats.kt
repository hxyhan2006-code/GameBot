package com.example.universalgamebot

enum class MatchDecision { CHASE, PRESS, SUPPORT, DRIBBLE, AIM, SHOOT, PASS, HOLD }

/** สถิติการตัดสินใจของบอท (ทั้งหมดมาจากภาพ ไม่มีข้อมูลภายในเกม) */
class MatchDecisionStats {
    val episodes = IntArray(MatchDecision.values().size)   // จำนวนครั้งที่เริ่มตัดสินใจแบบนั้น (นับตอนเปลี่ยนแบบ)
    var shotsGoal = 0; var shotsNoGoal = 0; var shotsLost = 0
    var passOk = 0; var passIntercepted = 0; var passLost = 0
    var visionGoalsFor = 0; var visionGoalsAgainst = 0
    var shotsInside = 0; var goalsInside = 0; var shotsOutside = 0; var goalsOutside = 0
    val defense = MatchDefenseStats()
    var kicksCancelled = 0

    fun line() = "decisions " + MatchDecision.values().joinToString(" ") { "${it.name.lowercase()}=${episodes[it.ordinal]}" } +
        " | shots goal=$shotsGoal noGoal=$shotsNoGoal lost=$shotsLost | passes ok=$passOk intercepted=$passIntercepted lost=$passLost" +
        " | vision score $visionGoalsFor:$visionGoalsAgainst | conceded=$visionGoalsAgainst | kicks cancelled=$kicksCancelled" +
        " | shot band inside=$shotsInside goals=$goalsInside outside=$shotsOutside goals=$goalsOutside | ${defense.line()}"
}
