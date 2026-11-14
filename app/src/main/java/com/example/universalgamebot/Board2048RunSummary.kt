package com.example.universalgamebot

/** Presentation/report data; game outcome and action verification are deliberately independent. */
data class Board2048RunSummary(
    val number:Long,val phase:Board2048Batch.Phase,val evidence:Board2048Batch.EndEvidence,
    val highestConfirmed:Int,val elapsedMs:Long,val requests:Int,val verified:Int
) {
    val time:String get() {
        val seconds=elapsedMs.coerceAtLeast(0)/1000
        return "${seconds/60}:${(seconds%60).toString().padStart(2,'0')}"
    }
    companion object {
        fun recent(entries:List<Board2048History.Entry>):List<Board2048RunSummary> = entries.mapNotNull { entry ->
            entry.result.batch?.takeIf { it.limit==Board2048Batch.UNTIL_END && it.requests>0 }?.let {
                Board2048RunSummary(entry.number,it.phase,it.evidence,it.highestTile,it.elapsedMs,it.requests,it.verified)
            }
        }.takeLast(3)
        fun overlayEnded(batch:Board2048Batch.Report?) = batch?.evidence in listOf(Board2048Batch.EndEvidence.WIN_LABEL,Board2048Batch.EndEvidence.GAME_OVER_LABEL)
        fun actionStatus(step:Board2048SingleStep.Report,batch:Board2048Batch.Report?,finalTurn:Boolean=true):String =
            if(finalTurn && overlayEnded(batch) && step.phase==Board2048SingleStep.Phase.STOPPED) "ENDED_BY_GAME_SCREEN" else step.phase.name
    }
}
