package com.example.universalgamebot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal class ClickerProfiles(context: Context) {
    private val prefs = context.getSharedPreferences("auto_clicker_profiles", Context.MODE_PRIVATE)

    fun names() = prefs.all.keys.sorted()

    fun save(name: String, profile: ClickProfile) {
        val targets = JSONArray()
        profile.targets.forEach { target ->
            targets.put(JSONObject().put("x", target.x.toDouble()).put("y", target.y.toDouble())
                .put("delay", target.delayMs).put("press", target.pressMs))
        }
        prefs.edit().putString(name, JSONObject().put("multi", profile.multi)
            .put("rounds", profile.rounds).put("targets", targets).toString()).apply()
    }

    fun load(name: String): ClickProfile? = try {
        prefs.getString(name, null)?.let { stored ->
            val json = JSONObject(stored)
            val targets = json.getJSONArray("targets")
            ClickProfile(List(targets.length()) { index ->
                val item = targets.getJSONObject(index)
                ClickTarget(item.getDouble("x").toFloat(), item.getDouble("y").toFloat(),
                    item.getLong("delay"), item.getLong("press"))
            }, json.getBoolean("multi"), json.getInt("rounds"))
        }
    } catch (e: Exception) {
        DebugMonitor.error("Cannot load clicker profile", e)
        null
    }
}
