package com.example.universalgamebot

import kotlin.math.*

/** Connected colour components retain real bounds; a triangle's centroid is not its box centre. */
internal object EFootballComponents {
    fun read(f:SampledFrame,zone:Box,predicate:(Int)->Boolean):List<EFootballPixels.Blob> {
        val left=zone.left.toInt().coerceAtLeast(0);val top=zone.top.toInt().coerceAtLeast(0)
        val right=zone.right.toInt().coerceAtMost(f.width);val bottom=zone.bottom.toInt().coerceAtMost(f.height)
        val w=right-left;val h=bottom-top;if(w<=0||h<=0)return emptyList()
        val mask=BooleanArray(w*h){i->predicate(f.rgb[(top+i/w)*f.width+left+i%w])}
        val queue=IntArray(w*h);val out=ArrayList<EFootballPixels.Blob>()
        for(i in mask.indices)if(mask[i]){
            var take=0;var put=1;queue[0]=i;mask[i]=false
            var minX=w;var maxX=0;var minY=h;var maxY=0;var sx=0L;var sy=0L
            while(take<put){
                val at=queue[take++];val x=at%w;val y=at/w
                minX=min(minX,x);maxX=max(maxX,x);minY=min(minY,y);maxY=max(maxY,y);sx+=x;sy+=y
                for(dy in -1..1)for(dx in -1..1){val nx=x+dx;val ny=y+dy
                    if(nx in 0 until w && ny in 0 until h){val ni=ny*w+nx;if(mask[ni]){mask[ni]=false;queue[put++]=ni}}
                }
            }
            out+=EFootballPixels.Blob(left+sx.toFloat()/put,top+sy.toFloat()/put,maxX-minX+1,maxY-minY+1,put,left+minX,top+minY)
        }
        return out
    }
    fun cyan(v:Int):Boolean {
        val r=v shr 16 and 255;val g=v shr 8 and 255;val b=v and 255
        return g>140 && b>130 && r<min(g,b)*.70f && abs(g-b)<100
    }
    /** Form local glyphs; distant speckles never join every icon into one fictitious object. */
    fun groups(parts:List<EFootballPixels.Blob>,gap:Float,maxW:Float,maxH:Float,minCount:Int):List<EFootballPixels.Blob> {
        val remaining=parts.toMutableList();val groups=ArrayList<EFootballPixels.Blob>()
        while(remaining.isNotEmpty()){
            val cloud=arrayListOf(remaining.removeAt(remaining.lastIndex));var at=0
            while(at<cloud.size){
                val a=cloud[at++]
                val connected=remaining.filter{b->
                    maxOf(a.left-b.left-b.w,b.left-a.left-a.w,0)<=gap &&
                        maxOf(a.top-b.top-b.h,b.top-a.top-a.h,0)<=gap}
                cloud+=connected;remaining.removeAll(connected.toSet())
            }
            val l=cloud.minOf{it.left};val t=cloud.minOf{it.top}
            val w=cloud.maxOf{it.left+it.w}-l;val h=cloud.maxOf{it.top+it.h}-t;val count=cloud.sumOf{it.count}
            if(w>maxW || h>maxH || count<minCount)continue
            groups+=EFootballPixels.Blob(cloud.sumOf{(it.x*it.count).toDouble()}.toFloat()/count,
                cloud.sumOf{(it.y*it.count).toDouble()}.toFloat()/count,w,h,count,l,t)
        }
        return groups
    }
}
