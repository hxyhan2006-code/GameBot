package com.example.universalgamebot

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow

/** Local expectimax over observed ranks. Chance nodes enumerate uniform empty cells, 90% 2 / 10% 4. */
object Board2048Planner {
    data class Decision(val move:Board2048Moves.Move?,val depth:Int,val nodes:Int,val elapsedMs:Long)
    private val left=IntArray(65536)
    private val rowValue=DoubleArray(65536)
    init {
        for(row in 0..65535) {
            val ranks=IntArray(4) { (row ushr (it*4)) and 15 }
            val compact=ranks.filter { it!=0 }; val out=IntArray(4)
            var at=0; var i=0
            while(i<compact.size) {
                var r=compact[i++]
                if(i<compact.size && compact[i]==r) { r=min(15,r+1); i++ }
                out[at++]=r
            }
            left[row]=out.indices.sumOf { out[it] shl (4*it) }
            var up=0.0; var down=0.0; var merges=0
            for(j in 0..2) {
                val difference=ranks[j].toDouble().pow(4)-ranks[j+1].toDouble().pow(4)
                if(difference>0) up+=difference else down-=difference
            }
            for(j in 1 until compact.size) if(compact[j]==compact[j-1]) merges++
            rowValue[row]=270.0*ranks.count { it==0 }+700.0*merges-47.0*min(up,down)-11.0*ranks.sumOf { it.toDouble().pow(3.5) }
        }
    }
    fun warmUp() {}
    fun encode(cells:List<Int>):Long {
        require(Board2048Moves.valid(cells))
        var result=0L
        cells.forEachIndexed { i,v -> if(v!=0) result=result or (Integer.numberOfTrailingZeros(v).toLong() shl (4*i)) }
        return result
    }
    private fun reverse(row:Int)=((row and 15) shl 12) or ((row and 240) shl 4) or ((row ushr 4) and 240) or ((row ushr 12) and 15)
    private fun transpose(b:Long):Long {
        var result=0L
        for(r in 0..3) for(c in 0..3) result=result or (((b ushr ((r*4+c)*4)) and 15L) shl ((c*4+r)*4))
        return result
    }
    fun shift(board:Long,d:Board2048Moves.Direction):Long {
        val vertical=d==Board2048Moves.Direction.UP || d==Board2048Moves.Direction.DOWN
        val input=if(vertical) transpose(board) else board
        val backward=d==Board2048Moves.Direction.RIGHT || d==Board2048Moves.Direction.DOWN
        var out=0L
        for(r in 0..3) {
            val row=((input ushr (r*16)) and 65535).toInt()
            val moved=if(backward) reverse(left[reverse(row)]) else left[row]
            out=out or (moved.toLong() shl (r*16))
        }
        return if(vertical) transpose(out) else out
    }
    private fun evaluate(board:Long):Double {
        val transposed=transpose(board)
        var value=0.0
        for(r in 0..3) value+=rowValue[((board ushr (r*16)) and 65535).toInt()]+rowValue[((transposed ushr (r*16)) and 65535).toInt()]
        return value
    }
    fun choose(cells:List<Int>,maxNodes:Int=24_000,maxNanos:Long=60_000_000):Decision {
        if(!Board2048Moves.valid(cells)) return Decision(null,0,0,0)
        val started=System.nanoTime()
        val board=encode(cells)
        val legal=Board2048Moves.Direction.entries.filter { shift(board,it)!=board && Board2048Moves.valid(Board2048Moves.slide(cells,it).shifted) }
        if(legal.isEmpty()) return Decision(null,0,0,0)
        var best=legal.first(); var completedDepth=0; var nodes=0
        class Budget:RuntimeException(null,null,false,false)
        fun check() {
            nodes++
            if(nodes>maxNodes || (nodes and 127==0 && System.nanoTime()-started>maxNanos)) throw Budget()
        }
        fun search(b:Long,depth:Int,chance:Boolean):Double {
            check()
            if(chance) {
                val empty=(0..15).filter { ((b ushr (it*4)) and 15L)==0L }
                if(empty.isEmpty()) return evaluate(b)
                var total=0.0
                for(i in empty) {
                    val two=b or (1L shl (4*i)); val four=b or (2L shl (4*i))
                    total+=0.9*(if(depth==0) evaluate(two) else search(two,depth,false))
                    total+=0.1*(if(depth==0) evaluate(four) else search(four,depth,false))
                }
                return total/empty.size
            }
            var bestValue=Double.NEGATIVE_INFINITY
            for(d in Board2048Moves.Direction.entries) {
                val moved=shift(b,d)
                if(moved!=b) bestValue=maxOf(bestValue,search(moved,depth-1,true))
            }
            return if(bestValue==Double.NEGATIVE_INFINITY) -1e12 else bestValue
        }
        // A completed shallow iteration is retained if a deeper one exhausts either budget.
        for(depth in 1..4) {
            try {
                var value=Double.NEGATIVE_INFINITY; var direction=best
                for(d in legal) {
                    val score=search(shift(board,d),depth-1,true)
                    if(score>value) { value=score; direction=d }
                }
                best=direction; completedDepth=depth
            } catch(_:Budget) { break }
        }
        val move=Board2048Moves.slide(cells,best).takeIf { Board2048Moves.valid(it.shifted) }
        return Decision(move,completedDepth,nodes,(System.nanoTime()-started)/1_000_000)
    }
}
