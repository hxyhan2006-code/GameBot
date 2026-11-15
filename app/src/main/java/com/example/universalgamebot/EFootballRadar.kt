package com.example.universalgamebot

import kotlin.math.roundToInt

/** Local radar glyphs. Missing/ambiguous ball evidence stays missing; never substitute the player. */
internal object EFootballRadar {
    data class Result(val selected:EFootballPixels.Blob?,val ball:EFootballPixels.Blob?,
        val red:List<EFootballPixels.Blob>,val green:List<EFootballPixels.Blob>,val diagnostic:String)
    fun region(w:Int,h:Int)=Box((w*.443f).toInt().toFloat(),(h*.801f).toInt().toFloat(),
        (w*.557f).toInt().toFloat(),(h*.948f).toInt().toFloat())
    fun read(f:SampledFrame,zone:Box):Result {
        val scale=zone.width/164f
        fun parts(predicate:(Int)->Boolean)=EFootballComponents.read(f,zone,predicate)
        fun groups(p:List<EFootballPixels.Blob>,gap:Float,min:Int)=EFootballComponents.groups(p,gap*scale,
            zone.width*.10f,zone.height*.20f,(min*scale*scale).roundToInt().coerceAtLeast(2))
        val cyan=groups(parts(EFootballComponents::cyan),8f,3)
        val gold=groups(parts{v->val r=v shr 16 and 255;val g=v shr 8 and 255;val b=v and 255
            r>150 && g>115 && b<g*.70f && r>g*.95f},2.5f,4)
        val red=groups(parts{v->val r=v shr 16 and 255;val g=v shr 8 and 255;val b=v and 255
            r>65 && r>g*1.25f && r>b*1.05f},1f,2)
        val green=groups(parts{v->val r=v shr 16 and 255;val g=v shr 8 and 255;val b=v and 255
            g>140 && g>r*1.15f && g>b*1.1f},1f,2)
        return Result(cyan.singleOrNull(),gold.singleOrNull(),red,green,
            "radar=${zone.width.toInt()}x${zone.height.toInt()} cyanGlyphs=${cyan.size} goldGlyphs=${gold.size}")
    }
}
