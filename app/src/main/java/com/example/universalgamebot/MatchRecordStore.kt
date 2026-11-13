package com.example.universalgamebot

import android.content.Context

/** ผลแข่งสะสมของมนุษย์ (ชนะ/เสมอ/แพ้ + ประตูได้เสีย) แยกตามระดับคู่แข่ง · เก็บในเครื่องเท่านั้น */
class MatchRecord(val wins: Int, val draws: Int, val losses: Int, val goalsFor: Int, val goalsAgainst: Int) {
    val played get() = wins + draws + losses
}

object MatchRecordStore {
    private const val FILE = "match_stats"
    const val LEVEL_KEY = "match_level"

    private fun prefs(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun level(c: Context): AiLevel = AiLevel.parse(c.getSharedPreferences("settings", Context.MODE_PRIVATE).getString(LEVEL_KEY, null))

    /** วนระดับ ง่าย → ปกติ → ยาก → ง่าย (ใช้ในหน้าตั้งค่า) */
    fun cycleLevel(c: Context): AiLevel {
        val all = AiLevel.values()
        val next = all[(level(c).ordinal + 1) % all.size]
        c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString(LEVEL_KEY, next.name).apply()
        return next
    }

    fun load(c: Context, lv: AiLevel): MatchRecord {
        val p = prefs(c); val k = lv.name
        return MatchRecord(p.getInt("$k.w", 0), p.getInt("$k.d", 0), p.getInt("$k.l", 0), p.getInt("$k.gf", 0), p.getInt("$k.ga", 0))
    }

    fun add(c: Context, lv: AiLevel, home: Int, away: Int) {
        val r = load(c, lv); val k = lv.name
        prefs(c).edit()
            .putInt("$k.w", r.wins + if (home > away) 1 else 0).putInt("$k.d", r.draws + if (home == away) 1 else 0).putInt("$k.l", r.losses + if (home < away) 1 else 0)
            .putInt("$k.gf", r.goalsFor + home).putInt("$k.ga", r.goalsAgainst + away).apply()
    }

    fun reset(c: Context) { prefs(c).edit().clear().apply() }

    fun levelName(c: Context, lv: AiLevel) = c.getString(when (lv) { AiLevel.EASY -> R.string.level_easy; AiLevel.NORMAL -> R.string.level_normal; AiLevel.HARD -> R.string.level_hard })
}
