package com.example.universalgamebot

/** OCR boxes are in full captured-screen pixels and belong only to their source image. */
data class EFootballWord(val text:String,val x:Float,val y:Float)
data class EFootballHud(val attack:Boolean,val buttons:Map<String,EFootballScreenPoint>,val canShoot:Boolean=true) {
    companion object {
        fun read(words:List<EFootballWord>,w:Int,h:Int):EFootballHud? {
            val buttons=HashMap<String,EFootballScreenPoint>()
            for(word in words) {
                if(word.x<w*.70f || word.y<h*.35f || word.x>=w || word.y>=h*.96f)continue
                val normalized=word.text.lowercase().filter {it.isLetter()}
                // Observed on-device: faint Dash read as "Da". Only this fixed corner alias;
                // the other three labels and the Standard arrangement must still be present.
                val key=if(normalized in setOf("da","das") && word.x in w*.85f..w*.99f && word.y in h*.70f..h*.94f)"dash" else normalized
                if(key in setOf("pass","shoot","through","dash","switch","matchup","tackle","pressure","clear")) {
                    if(buttons.containsKey(key)) return null
                    buttons[key]=EFootballScreenPoint(word.x,word.y)
                }
            }
            val hasShot=("shoot" in buttons) xor ("clear" in buttons)
            val attack=listOf("pass","through","dash").all {it in buttons} && hasShot
            val defend=listOf("switch","matchup","tackle").all {it in buttons} && ("dash" in buttons || "pressure" in buttons)
            if(attack==defend)return null
            // Only owner's Standard, four-button arrangement. Reject reordered or collapsed controls.
            val pass=buttons[if(attack)"pass" else "switch"]!!
            val through=buttons[if(attack)"through" else "matchup"]!!
            val shoot=buttons[if(!attack)"tackle" else if("shoot" in buttons)"shoot" else "clear"]!!
            val dash=buttons["dash"] ?: buttons["pressure"]!!
            if(!(pass.x<dash.x && through.x<shoot.x && pass.y>through.y && dash.y>shoot.y))return null
            return EFootballHud(attack,buttons,"shoot" in buttons)
        }
    }
}
