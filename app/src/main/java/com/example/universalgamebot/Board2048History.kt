package com.example.universalgamebot

/** Bounded numeric reports for this process only. Never stores images or grants action permission. */
class Board2048History(val capacity: Int = 20) {
    data class Entry(val number: Long, val result: Board2048Readout.Snapshot)
    private val saved = ArrayDeque<Entry>()
    var total: Long = 0; private set
    val entries: List<Entry> get() = saved.toList()
    val size: Int get() = saved.size

    init { require(capacity > 0) }

    fun record(result: Board2048Readout.Snapshot) {
        require(!result.running && result.step != null)
        val step = result.step
        // Freeze lists so starting another reading cannot change an archived result.
        val frozen = result.copy(
            board = result.board?.let { it.copy(cells = it.cells.toList()) },
            step = step.copy(before = step.before?.toList(), expected = step.expected?.toList(), after = step.after?.toList()))
        total++
        saved.addLast(Entry(total, frozen))
        if (saved.size > capacity) saved.removeFirst()
    }
}
