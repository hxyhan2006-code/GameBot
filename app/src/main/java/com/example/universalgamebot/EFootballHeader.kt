package com.example.universalgamebot

/** A phone clock is not evidence of a match. Ambiguous header clocks still prohibit input. */
internal data class EFootballHeader(val match:Boolean,val obstructed:Boolean,val text:String) {
    companion object {
        fun read(words:List<EFootballWord>,w:Int,h:Int):EFootballHeader {
            val header=words.filter{it.x in 0f..w*.55f && it.y in 0f..h*.18f}
            fun compact(word:EFootballWord)=word.text.replace(" ","")
            val clocks=header.filter{Regex("[0-9]{1,3}:[0-9]{2}(?:\\+[0-9]+)?").matches(compact(it))}
            val match=clocks.any{clock->
                val scores=header.filter{it!==clock && it.x in w*.02f..clock.x && kotlin.math.abs(it.y-clock.y)<h*.035f}
                scores.count{Regex("[0-9]{1,2}").matches(compact(it))}>=2 ||
                    scores.any{Regex("[0-9]{1,2}[-:][0-9]{1,2}").matches(compact(it))}
            }
            val scoreOnly=header.any{Regex("[0-9]{1,2}[-:][0-9]{1,2}").matches(compact(it))}
            return EFootballHeader(match,(clocks.isNotEmpty() || scoreOnly) && !match,header.take(12).joinToString("|"){it.text})
        }
    }
}
