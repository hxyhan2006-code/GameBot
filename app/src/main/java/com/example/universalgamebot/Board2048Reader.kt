package com.example.universalgamebot

import kotlin.math.abs

/** Pixel-only recognizer for the pinned tpcstld offline fixture's default palette.
 * No OCR guess or game-state IPC. Any missing, ambiguous or unsupported tile rejects the frame. */
class Board2048Reader {
    companion object {
        val PALETTE = linkedMapOf(0 to 0xd6cdc4, 2 to 0xeee4da, 4 to 0xede0c8, 8 to 0xf2b179,
            16 to 0xf59563, 32 to 0xf67c5f, 64 to 0xf65e3b, 128 to 0xedcf72,
            256 to 0xedcc61, 512 to 0xedc850, 1024 to 0xedc53f, 2048 to 0xedc22e)
        private fun rule(c: Int) = ColorRule { r,g,b -> abs(r-(c shr 16 and 255)) <= 6 && abs(g-(c shr 8 and 255)) <= 6 && abs(b-(c and 255)) <= 6 }
    }
    private val detectors = PALETTE.map { (value,c) -> ColorDetector("2048:$value",value.toString(),rule(c),minCells=80,maxObjects=40) }
    private val background = ColorDetector("2048:board","board",rule(0xbbada0),minCells=400,maxObjects=12)
    data class Board(val cells: List<Int>, val bounds: Box) {
        fun rows() = cells.chunked(4).joinToString("\n") { row -> row.joinToString("  ") { if(it==0) "." else it.toString() } }
    }
    fun read(frame: SampledFrame): Board? {
        val boardRegions = background.detect(frame).filter { abs(it.box.width-it.box.height) < it.box.width*0.08f && it.box.width > frame.width*frame.step*0.45f }
        if (boardRegions.size != 1) return null
        val region = boardRegions.single().box
        val side = region.width
        val tiles = detectors.flatMap { it.detect(frame) }.filter {
            val b=it.box
            b.width in side*0.17f..side*0.25f && b.height in side*0.17f..side*0.25f &&
                abs(b.width-b.height)<side*0.025f && region.contains(it.cx,it.cy)
        }
        if(tiles.size!=16) return null
        val sorted=tiles.sortedBy { it.cy }.chunked(4).map { row -> row.sortedBy { it.cx } }
        val width=tiles.map { it.box.width }.average().toFloat()
        for(row in 0..3) for(col in 0..3) {
            val tile=sorted[row][col]
            if(abs(tile.box.width-width)>width*0.08f || abs(tile.box.height-width)>width*0.08f) return null
            if(abs(tile.cy-sorted[row][0].cy)>width*0.08f) return null
            if(abs(tile.cx-sorted[0][col].cx)>width*0.08f) return null
            if(col>0 && (tile.cx-sorted[row][col-1].cx) !in width*1.03f..width*1.5f) return null
            if(row>0 && (tile.cy-sorted[row-1][col].cy) !in width*1.03f..width*1.5f) return null
        }
        val cells=sorted.flatten().map { it.label.toInt() }
        if(cells.count { it!=0 }<2) return null
        return Board(cells,region)
    }
}

/** Require three matching frames spanning 400ms; never expose a guess as a live reading. */
class Stable2048Board {
    private var candidate: Board2048Reader.Board?=null
    private var since=-1L
    private var lastAt=-1L
    private var count=0
    fun invalidate() { candidate=null; since=-1L; count=0 }
    fun observe(board: Board2048Reader.Board?,at:Long): Board2048Reader.Board? {
        if(at<=lastAt) { invalidate(); return null }
        val previousAt=lastAt; lastAt=at
        if(board==null) { candidate=null; count=0; return null }
        val old=candidate
        val same=old!=null && old.cells==board.cells && abs(old.bounds.left-board.bounds.left)<8 && abs(old.bounds.top-board.bounds.top)<8 && abs(old.bounds.width-board.bounds.width)<8
        if(!same || at-previousAt>750) { candidate=board; since=at; count=1; return null }
        count++
        return board.takeIf { count>=3 && at-since>=400 }
    }
}
