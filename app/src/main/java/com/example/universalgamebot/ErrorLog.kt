package com.example.universalgamebot

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** บันทึก Error ลงไฟล์ในเครื่อง (files/errors.log) รวมถึงแอปพังกลางคัน · ไม่ส่งออกนอกเครื่อง */
object ErrorLog {
    private const val MAX_BYTES = 256 * 1024L
    private var file: File? = null
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val recent = ArrayDeque<String>()

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        file = File(context.applicationContext.filesDir, "errors.log")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            write("CRASH", "uncaught in ${t.name}: ${e.javaClass.name}: ${e.message}", e)
            previous?.uncaughtException(t, e)
        }
    }

    @Synchronized
    fun write(level: String, msg: String, t: Throwable? = null) {
        val line = "${fmt.format(Date())} $level $msg"
        recent.addLast(line)
        while (recent.size > 50) recent.removeFirst()
        val f = file ?: return
        try {
            if (f.length() > MAX_BYTES) f.renameTo(File(f.parentFile, "errors.old.log"))
            f.appendText(line + "\n")
            if (t != null) f.appendText(t.stackTraceToString().lines().take(8).joinToString("\n") { "    $it" } + "\n")
        } catch (_: Exception) { /* log ห้ามทำให้แอปพังซ้ำ */ }
    }

    @Synchronized
    fun tail(n: Int = 4): String = recent.toList().takeLast(n).joinToString("\n").ifEmpty { "-" }

    @Synchronized
    fun readAll(maxLines: Int = 120): String = try {
        file?.takeIf { it.exists() }?.readLines()?.takeLast(maxLines)?.joinToString("\n") ?: "(empty)"
    } catch (e: Exception) { "(cannot read: ${e.message})" }

    @Synchronized
    fun clear() { recent.clear(); try { file?.delete() } catch (_: Exception) {} }
}
