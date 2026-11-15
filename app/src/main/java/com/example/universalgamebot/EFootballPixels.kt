package com.example.universalgamebot

import kotlin.math.*

/** Pixel heuristics, NOT calibrated probabilities. No hidden game state or game text tree. */
class EFootballPixels {
    data class Blob(val x: Float,val y: Float,val w:Int,val h:Int,val count:Int)
    data class Anchors(val training:Boolean,val grass:Float,val selected:Blob?,val radarSelected:Blob?,
        val ball:Blob?,val radar:Box,val red:List<Blob>,val green:List<Blob>,val axes:Axes?)
    data class Axes(val horizontalSlope:Float,val verticalSlope:Float,val verticalEstimated:Boolean=false) {
        fun bearing(dx:Float,dy:Float):Pair<Float,Float> = (dx*1.5f + dy*verticalSlope*.7f) to (dx*1.5f*horizontalSlope + dy*.7f)
    }
    fun read(f:SampledFrame):Anchors {
        require(f.step==1 && f.originX==0 && f.originY==0)
        val w=f.width;val h=f.height
        fun c(x:Int,y:Int)=f.rgb[y*w+x]
        val radar=Box(w*.443f,h*.801f,w*.557f,h*.948f)
        var grass=0;var samples=0
        for(y in (h*.18f).toInt() until (h*.75f).toInt() step 8) for(x in (w*.05f).toInt() until (w*.70f).toInt() step 8) {
            val v=c(x,y);val r=v shr 16 and 255;val g=v shr 8 and 255;val b=v and 255
            if(g>r*1.05f && g>b*1.2f && g>35)grass++;samples++
        }
        val pitch=grass.toFloat()/samples.coerceAtLeast(1)
        // Training's coloured Home/Away swap icon inside its white rounded button.
        var cyan=0;var pink=0;var light=0;var n=0
        for(y in (h*.01f).toInt() until (h*.16f).toInt() step 2) for(x in (w*.80f).toInt() until (w*.875f).toInt() step 2) {
            val v=c(x,y);val r=v shr 16 and 255;val g=v shr 8 and 255;val b=v and 255
            if(g>110 && b>100 && r<g*.65f)cyan++
            if(r>150 && r>g*1.3f && r>b*1.05f)pink++
            if(minOf(r,g,b)>150 && maxOf(r,g,b)-minOf(r,g,b)<65)light++;n++
        }
        var yellow=0;var scoreSamples=0
        for(y in (h*.015f).toInt() until (h*.085f).toInt() step 2) for(x in (w*.02f).toInt() until (w*.43f).toInt() step 2) {
            val v=c(x,y);val r=v shr 16 and 255;val g=v shr 8 and 255;val b=v and 255
            if(r>160 && g>160 && b<100)yellow++;scoreSamples++
        }
        // Current KONAMI scoreboard: reject even if its Home/Away icon resembles Free Training.
        val scoreboard=yellow>scoreSamples*.025f
        val training=!scoreboard && pitch>.40f && cyan>=3 && pink>=3 && light>n*.12f
        if(!training)return Anchors(false,pitch,null,null,null,radar,emptyList(),emptyList(),null)
        val selected=blobs(f,Box(w*.08f,h*.17f,w*.90f,h*.76f),::cyan)
            .filter { it.w in (w*.003f).toInt().coerceAtLeast(3)..(w*.035f).toInt() &&
                it.h in (h*.006f).toInt().coerceAtLeast(3)..(h*.06f).toInt() && it.count>it.w*it.h*.4f }
            .singleOrNull()
        val selectedRadar=mergeIcon(blobs(f,radar,::cyan).filter { it.count>=3 },w*.014f,h*.034f)
        val gold=mergeIcon(blobs(f,radar) { v -> val r=v shr 16 and 255;val g=v shr 8 and 255;val b=v and 255
            r>150 && g>115 && b<g*.70f && r>g*.95f }
            .filter { it.count>=2 && it.w<=w*.012f && it.h<=h*.025f },w*.012f,h*.028f)
        val red=blobs(f,radar) { v ->val r=v shr 16 and 255;val g=v shr 8 and 255;val b=v and 255
            r>65 && r>g*1.25f && r>b*1.05f }.filter { it.count>=2 && it.w<=w*.012f && it.h<=h*.025f }
        val green=blobs(f,radar) { v ->val r=v shr 16 and 255;val g=v shr 8 and 255;val b=v and 255
            g>140 && g>r*1.15f && g>b*1.1f }.filter { it.count>=2 && it.w<=w*.012f && it.h<=h*.025f }
        return Anchors(training,pitch,selected,selectedRadar,gold,radar,red,green,axes(f))
    }
    /** Anti-aliased rings often fragment into several small islands. Only merge one tight cloud. */
    private fun mergeIcon(parts:List<Blob>,maxW:Float,maxH:Float):Blob? {
        if(parts.isEmpty())return null
        val l=parts.minOf {it.x-it.w/2f};val r=parts.maxOf {it.x+it.w/2f}
        val t=parts.minOf {it.y-it.h/2f};val b=parts.maxOf {it.y+it.h/2f}
        if(r-l>maxW || b-t>maxH)return null
        val n=parts.sumOf {it.count}
        return Blob(parts.sumOf {(it.x*it.count).toDouble()}.toFloat()/n,
            parts.sumOf {(it.y*it.count).toDouble()}.toFloat()/n,(r-l).toInt()+1,(b-t).toInt()+1,n)
    }
    private fun cyan(v:Int):Boolean { val r=v shr 16 and 255;val g=v shr 8 and 255;val b=v and 255
        return g>140 && b>130 && r<min(g,b)*.70f && abs(g-b)<100 }

    private fun blobs(f:SampledFrame,zone:Box,predicate:(Int)->Boolean):List<Blob> {
        val left=zone.left.toInt().coerceAtLeast(0);val top=zone.top.toInt().coerceAtLeast(0)
        val right=zone.right.toInt().coerceAtMost(f.width);val bottom=zone.bottom.toInt().coerceAtMost(f.height)
        val w=right-left;val h=bottom-top;if(w<=0||h<=0)return emptyList()
        val mask=BooleanArray(w*h) { i->predicate(f.rgb[(top+i/w)*f.width+left+i%w]) }
        val queue=IntArray(w*h);val out=ArrayList<Blob>()
        for(i in mask.indices) if(mask[i]) {
            var take=0;var put=1;queue[0]=i;mask[i]=false
            var minX=w;var maxX=0;var minY=h;var maxY=0;var sx=0L;var sy=0L
            while(take<put) {
                val at=queue[take++];val x=at%w;val y=at/w
                minX=min(minX,x);maxX=max(maxX,x);minY=min(minY,y);maxY=max(maxY,y);sx+=x;sy+=y
                for(dy in -1..1) for(dx in -1..1) {val nx=x+dx;val ny=y+dy
                    if(nx in 0 until w && ny in 0 until h) {val ni=ny*w+nx;if(mask[ni]){mask[ni]=false;queue[put++]=ni}}
                }
            }
            out+=Blob(left+sx.toFloat()/put,top+sy.toFloat()/put,maxX-minX+1,maxY-minY+1,put)
        }
        return out
    }
    /** Estimate two pitch-line directions; refuse a camera bearing when no long line exists. */
    private fun axes(f:SampledFrame):Axes? {
        val pts=ArrayList<Pair<Int,Int>>()
        val w=f.width;val h=f.height
        fun grass(x:Int,y:Int):Boolean {
            if(x !in 0 until w || y !in 0 until h)return false
            val c=f.rgb[y*w+x];val r=c shr 16 and 255;val g=c shr 8 and 255;val b=c and 255
            return g>r*1.05f && g>b*1.2f && g>35
        }
        fun luma(x:Int,y:Int):Int {val c=f.rgb[y*w+x];return ((c shr 16 and 255)*77+(c shr 8 and 255)*150+(c and 255)*29)/256}
        for(y in (h*.14f).toInt() until (h*.76f).toInt() step 3) for(x in (w*.02f).toInt() until (w*.98f).toInt() step 3) {
            if((x<w*.35f && y>h*.55f) || (x>w*.70f && y>h*.35f))continue
            val c=f.rgb[y*w+x];val r=c shr 16 and 255;val g=c shr 8 and 255;val b=c and 255
            // Pitch paint takes on green/yellow shadows. Contrast to BOTH opposite grass
            // neighbours distinguishes paint from grass/shadow edges without requiring pure white.
            val lum=luma(x,y)
            val across=grass(x-8,y) && grass(x+8,y) && lum>max(luma(x-8,y),luma(x+8,y))+18
            val down=grass(x,y-8) && grass(x,y+8) && lum>max(luma(x,y-8),luma(x,y+8))+18
            if(r>90 && g>110 && b>60 && (across || down))pts+=x to y
        }
        fun slope(vertical:Boolean):Float? {
            var best=0;var chosen:Float?=null
            for(deg in (if(vertical)-54 else -24)..(if(vertical)54 else 24) step 2) {
                val s=tan(deg*PI/180).toFloat();val bins=HashMap<Int,MutableList<Pair<Int,Int>>>()
                for(p in pts) {val intercept=if(vertical)p.first-s*p.second else p.second-s*p.first
                    bins.getOrPut((intercept/4).roundToInt()){ArrayList()}.add(p) }
                for(line in bins.values) if(line.size>best) {
                    val span=if(vertical)line.maxOf{it.second}-line.minOf{it.second} else line.maxOf{it.first}-line.minOf{it.first}
                    val occupied=line.map{(if(vertical)it.second else it.first)/6}.distinct().size
                    val totalBins=span/6+1
                    if(span>(if(vertical)h*.22f else w*.22f) && line.size>24 && occupied>totalBins*.65f){best=line.size;chosen=s}
                }
            }
            return chosen
        }
        val x=slope(false) ?: return null
        val y=slope(true)
        // Dynamic Wide preset: if vertical lines are outside the viewport, use a perpendicular
        // estimate. This is approximate steering, not a homography or calibrated world projection.
        val vertical=y ?: -x
        if(abs(1-x*vertical)<.5f)return null
        return Axes(x,vertical,y==null)
    }
}
