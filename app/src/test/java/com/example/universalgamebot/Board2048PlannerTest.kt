package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test

class Board2048PlannerTest {
    @Test fun optimizedTransitionsMatchIndependentArrayRulesAndRespectInput() {
        val random=java.util.Random(731)
        repeat(1000) {
            val cells=List(16) { val rank=random.nextInt(11); if(rank==0)0 else 1 shl rank }
            val b=Board2048Planner.encode(cells)
            for(d in Board2048Moves.Direction.entries) {
                val expected=Board2048Moves.slide(cells,d).shifted
                if(Board2048Moves.valid(expected)) assertEquals(Board2048Planner.encode(expected),Board2048Planner.shift(b,d))
            }
        }
    }
    @Test fun invalidAndTerminalBoardsHaveNoActionAndBudgetsStayBounded() {
        assertNull(Board2048Planner.choose(List(16){3}).move)
        val goal=List(16){if(it==0 || it==1)2048 else 0}
        Board2048Planner.choose(goal).move?.let { assertTrue(Board2048Moves.valid(it.shifted)) }
        assertNull(Board2048Planner.choose(List(16){if((it/4+it%4)%2==0)2 else 4}).move)
        val cells=List(16){if(it==15 || it==0)2 else 0}
        val d=Board2048Planner.choose(cells,maxNodes=2000,maxNanos=Long.MAX_VALUE)
        assertNotNull(d.move); assertNotEquals(cells,d.move!!.shifted)
        assertTrue(d.nodes<=2001); assertTrue(d.depth>=1)
    }
    @Test fun pairedSeedBenchmark() {
        data class Result(val tile:Int,val moves:Int,val score:Int,val nanos:Long)
        fun game(seed:Long,planner:Boolean):Result {
            val random=java.util.Random(seed)
            var cells=MutableList(16){0}; var score=0; var moves=0; var nanos=0L
            fun spawn() { val empty=cells.indices.filter { cells[it]==0 }; if(empty.isNotEmpty()) cells[empty[random.nextInt(empty.size)]]=if(random.nextDouble()<0.9)2 else 4 }
            spawn(); spawn()
            while(moves<2500 && cells.max()<2048) {
                val started=System.nanoTime()
                val move=if(planner) Board2048Planner.choose(cells,maxNodes=24_000,maxNanos=Long.MAX_VALUE).move else Board2048Moves.choose(cells)
                nanos+=System.nanoTime()-started
                if(move==null) break
                cells=move.shifted.toMutableList(); score+=move.scoreGain; moves++; spawn()
            }
            return Result(cells.max(),moves,score,nanos)
        }
        val baseline=mutableListOf<Result>(); val improved=mutableListOf<Result>()
        for(seed in 1101L..1108L) {
            val old=game(seed,false); val new=game(seed,true)
            baseline+=old; improved+=new
            println("BENCH seed=$seed baseline=$old expectimax=$new")
        }
        for((name,results) in listOf("baseline" to baseline,"expectimax" to improved)) println("BENCH SUMMARY $name games=${results.size} meanMax=${results.map { it.tile }.average()} meanScore=${results.map { it.score }.average()} 512=${results.count { it.tile>=512 }} 1024=${results.count { it.tile>=1024 }} 2048=${results.count { it.tile>=2048 }} meanThinkMs=${results.sumOf { it.nanos }.toDouble()/results.sumOf { it.moves }.coerceAtLeast(1)/1e6}")
        assertTrue("paired benchmark must improve max tile",improved.sumOf { it.tile }>baseline.sumOf { it.tile })
    }
}
