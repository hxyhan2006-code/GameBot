package com.example.universalgamebot

/** สถานะบอท ให้หน้าจอหลักรู้ว่าตอนนี้กำลังทำงานอยู่ไหม */
object BotState {
    @Volatile var running = false
        private set
    @Volatile var onChange: (() -> Unit)? = null

    fun set(value: Boolean) {
        running = value
        onChange?.invoke()
    }
}
