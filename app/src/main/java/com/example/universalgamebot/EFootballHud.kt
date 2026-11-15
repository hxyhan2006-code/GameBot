package com.example.universalgamebot

/** OCR boxes are in full captured-screen pixels and belong only to their source image. */
data class EFootballWord(val text:String,val x:Float,val y:Float)
data class EFootballHud(val attack:Boolean,val buttons:Map<String,EFootballScreenPoint>) {
    companion object {
        fun read(words:List<EFootballWord>,w:Int,h:Int):EFootballHud? {
            val buttons=HashMap<String,EFootballScreenPoint>()
            for(word in words) {
                if(word.x<w*.70f || word.y<h*.35f || word.x>=w || word.y>=h*.96f)continue
                val key=word.text.lowercase().filter {it.isLetter()}
                if(key in setOf("pass","shoot","through","dash","switch","matchup","tackle","pressure","clear")) {
                    if(buttons.containsKey(key)) return null
                    buttons[key]=EFootballScreenPoint(word.x,word.y)
                }
            }
            val attack=listOf("pass","shoot","through","dash").all {it in buttons}
            val defend=listOf("switch","matchup","tackle").all {it in buttons} && ("dash" in buttons || "pressure" in buttons)
            if(attack==defend)return null
            // Only owner's Standard, four-button arrangement. Reject reordered or collapsed controls.
            val pass=buttons[if(attack)"pass" else "switch"]!!
            val through=buttons[if(attack)"through" else "matchup"]!!
            val shoot=buttons[if(attack)"shoot" else "tackle"]!!
            val dash=buttons["dash"] ?: buttons["pressure"]!!
            if(!(pass.x<dash.x && through.x<shoot.x && pass.y>through.y && dash.y>shoot.y))return null
            return EFootballHud(attack,buttons)
        }
    }
}
