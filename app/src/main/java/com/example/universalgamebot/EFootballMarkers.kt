package com.example.universalgamebot

/** Distinguish the solid downward control triangle from cyan clothing, advertising and hollow hints. */
internal object EFootballMarkers {
    data class Result(val selected:EFootballPixels.Blob?,val colourCandidates:Int,val triangles:Int)
    fun read(f:SampledFrame):Result {
        val w=f.width;val h=f.height
        val candidates=EFootballComponents.read(f,Box(w*.08f,h*.17f,w*.90f,h*.76f),EFootballComponents::cyan)
            .filter{it.w in 3..(w*.025f).toInt() && it.h in 3..(h*.06f).toInt()}
        val triangles=candidates.filter{b->
            val ratio=b.w.toFloat()/b.h;val fill=b.count.toFloat()/(b.w*b.h)
            if(ratio !in .65f..1.4f || fill !in .28f.. .85f)return@filter false
            val rows=IntArray(b.h){y->(0 until b.w).count{x->EFootballComponents.cyan(f.rgb[(b.top+y)*w+b.left+x])}}
            val strip=(b.h/3).coerceAtLeast(1)
            val upper=rows.take(strip).average();val lower=rows.takeLast(strip).average()
            if(upper<lower*1.8 || upper<b.w*.5 || lower>b.w*.5 || (b.y-b.top)/b.h>.52f)return@filter false
            fun grass(x:Int,y:Int):Boolean {
                if(x !in 0 until w || y !in 0 until h)return false
                val v=f.rgb[y*w+x];val r=v shr 16 and 255;val g=v shr 8 and 255;val blue=v and 255
                return g>r*1.05f && g>blue*1.2f && g>35
            }
            // A stadium advert can contain a cyan triangle too. Demand surrounding pitch grass.
            val xs=listOf(b.left-b.w/2,b.left+b.w+b.w/2)
            val ys=listOf(b.top-b.h/3,b.top+b.h/3,b.top+b.h,b.top+b.h+b.h/3)
            xs.sumOf{x->ys.count{y->grass(x,y)}}>=5
        }
        return Result(triangles.singleOrNull(),candidates.size,triangles.size)
    }
}
