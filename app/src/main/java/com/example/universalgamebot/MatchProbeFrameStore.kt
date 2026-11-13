package com.example.universalgamebot

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.util.concurrent.Executors

/** Saves only the sampled in-app arena, never the rest of the display. PNG work runs off Vision.
 * Bounded to the most recent 120 frames, stored locally in the app's files directory. */
class MatchProbeFrameStore(context: Context) {
    private class Pending(val at: Long, val saved: BooleanArray = BooleanArray(2))
    private val pending = ArrayList<Pending>()
    private val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "match-probes").apply { mkdirs() }
    private val run = System.currentTimeMillis()
    private val writer = Executors.newSingleThreadExecutor()

    fun arm(atMs: Long) { pending.add(Pending(atMs)) }

    fun onFrame(frame: SampledFrame, timeMs: Long, seq: Long) {
        for (shot in pending) for ((i, offset) in listOf(300L, 600L).withIndex()) {
            if (shot.saved[i] || timeMs - shot.at < offset) continue
            shot.saved[i] = true
            val w = frame.width; val h = frame.height
            val pixels = IntArray(w * h) { frame.rgb[it] or (0xff shl 24) }
            val name = "$run-shot${shot.at}-plus$offset-actual${timeMs-shot.at}-frame$seq.png"
            writer.execute {
                try {
                    val bitmap = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
                    try { File(dir,name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
                    finally { bitmap.recycle() }
                    dir.listFiles()?.filter { it.extension == "png" }?.sortedByDescending { it.lastModified() }?.drop(120)?.forEach { it.delete() }
                } catch (e: Exception) { DebugMonitor.error("shot probe PNG failed: ${e.message}") }
            }
        }
        pending.removeAll { it.saved.all { done -> done } }
    }

    fun close() { writer.shutdown() }

    companion object {
        fun report(context: Context): String {
            val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "match-probes")
            val files = dir.listFiles()?.filter { it.extension == "png" }?.sortedByDescending { it.lastModified() } ?: emptyList()
            return "Shot frame PNGs (sampled arena only, most recent 120): ${dir.absolutePath}\n" + files.take(60).joinToString("\n") { it.name }
        }
    }
}
