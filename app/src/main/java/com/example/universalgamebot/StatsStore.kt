package com.example.universalgamebot

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** สถิติสะสมต่อโปรไฟล์ (SharedPreferences) + บันทึกสรุปแต่ละรอบ (files/sessions.log) เก็บในเครื่องเท่านั้น */
object StatsStore {
    private const val PREFS = "stats"
    private const val MAX_LINES = 200
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    @Synchronized
    fun load(context: Context, profileId: String): ActionStats =
        ActionStats.parse(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(profileId, null))

    @Synchronized
    fun addSession(context: Context, profileId: String, session: ActionStats) {
        if (session.attempts == 0) return
        val total = load(context, profileId)
        total.plusAssign(session)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(profileId, total.serialize()).apply()
    }

    @Synchronized
    fun reset(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        try { File(context.applicationContext.filesDir, "sessions.log").delete() } catch (_: Exception) {}
    }

    @Synchronized
    fun appendSession(context: Context, line: String) {
        try {
            val f = File(context.applicationContext.filesDir, "sessions.log")
            val lines = (if (f.exists()) f.readLines() else emptyList()).takeLast(MAX_LINES - 1) + "${fmt.format(Date())} $line"
            f.writeText(lines.joinToString("\n") + "\n")
        } catch (e: Exception) { ErrorLog.write("ERROR", "cannot write sessions.log: ${e.message}", e) }
    }

    @Synchronized
    fun recentSessions(context: Context, n: Int): String = try {
        val f = File(context.applicationContext.filesDir, "sessions.log")
        if (f.exists()) f.readLines().takeLast(n).joinToString("\n").ifEmpty { "-" } else "-"
    } catch (_: Exception) { "-" }
}

/** คะแนนจริงของเกมทดสอบ (ground truth) เทียบกับสิ่งที่บอทตรวจผลได้ บอทไม่เอาค่านี้ไปตัดสินใจ */
object GroundTruth {
    @Volatile var score = 0; private set
    @Volatile var mistakes = 0; private set
    @Volatile private var baseScore = 0
    @Volatile private var baseMistakes = 0

    fun update(score: Int, mistakes: Int) { this.score = score; this.mistakes = mistakes }
    fun begin() { baseScore = score; baseMistakes = mistakes }
    fun gained() = score - baseScore
    fun missed() = mistakes - baseMistakes
    fun line() = "game score +${gained()}, misses +${missed()} since Start"
}
