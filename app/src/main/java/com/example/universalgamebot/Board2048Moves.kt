package com.example.universalgamebot

import kotlin.math.abs

/** Rules and a small local heuristic. Uses only the sixteen values read from pixels. */
object Board2048Moves {
    enum class Direction { LEFT, UP, RIGHT, DOWN }
    data class Move(val direction: Direction, val before: List<Int>, val shifted: List<Int>, val scoreGain: Int)
    fun valid(cells: List<Int>) = cells.size == 16 && cells.all { it == 0 || (it in 2..2048 && it and (it - 1) == 0) }
    fun slide(cells: List<Int>, direction: Direction): Move {
        require(valid(cells))
        val out = MutableList(16) { 0 }
        var score = 0
        for (line in 0..3) {
            val indices = (0..3).map { pos -> when (direction) {
                Direction.LEFT -> line * 4 + pos
                Direction.RIGHT -> line * 4 + 3 - pos
                Direction.UP -> pos * 4 + line
                Direction.DOWN -> (3 - pos) * 4 + line
            } }
            val numbers = indices.map { cells[it] }.filter { it != 0 }
            var i = 0; var target = 0
            while (i < numbers.size) {
                var value = numbers[i++]
                if (i < numbers.size && value == numbers[i]) { value *= 2; score += value; i++ }
                out[indices[target++]] = value
            }
        }
        return Move(direction, cells.toList(), out, score)
    }
    fun choose(cells: List<Int>): Move? {
        if (!valid(cells)) return null
        return Direction.entries.map { slide(cells, it) }
            .filter { it.shifted != cells && valid(it.shifted) }
            .maxByOrNull { move ->
                val logs = move.shifted.map { if (it == 0) 0 else Integer.numberOfTrailingZeros(it) }
                val smooth = (0..15).sumOf { i ->
                    (if (i % 4 < 3) abs(logs[i] - logs[i + 1]) else 0) +
                        (if (i < 12) abs(logs[i] - logs[i + 4]) else 0)
                }
                val corner = listOf(0, 3, 12, 15).maxOf { logs[it] }
                move.shifted.count { it == 0 } * 100 + corner * 12 + move.scoreGain.coerceAtMost(2048) / 4 - smooth
            }
    }
    /** Exactly one new 2 or 4 in a previously empty cell after a legal slide. */
    fun verifies(move: Move, observed: List<Int>): Boolean {
        if (!valid(observed) || move.before == move.shifted) return false
        val changed = observed.indices.filter { observed[it] != move.shifted[it] }
        return changed.size == 1 && changed.single().let { move.shifted[it] == 0 && observed[it] in listOf(2, 4) }
    }
}
