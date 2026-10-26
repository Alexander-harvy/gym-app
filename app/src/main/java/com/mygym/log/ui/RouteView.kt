package com.mygym.log.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mygym.log.data.RunPoint
import kotlin.math.*

/** Local north-up projection. Rendered geometry is never sent to a tile or map provider. */
@Composable fun RouteView(points: List<RunPoint>) {
    if(points.isEmpty()) { EmptyState("Waiting for a route","Move outdoors to acquire GPS.");return }
    var zoom by remember{mutableFloatStateOf(1f)}
    var pan by remember{mutableStateOf(Offset.Zero)}
    val gestures=rememberTransformableState { z,p,_ -> zoom=(zoom*z).coerceIn(1f,8f);pan+=p }
    val projected=remember(points) {
        val first=points.first()
        val cosine=cos(Math.toRadians(first.latitude)).coerceAtLeast(.000001)
        points.map { point ->
            val deltaLon=((point.longitude-first.longitude+540)%360)-180
            Triple(point.segment,Math.toRadians(deltaLon)*6371008.8*cosine,Math.toRadians(point.latitude-first.latitude)*6371008.8)
        }
    }
    val minX=projected.minOf{it.second};val maxX=projected.maxOf{it.second}
    val minY=projected.minOf{it.third};val maxY=projected.maxOf{it.third}
    val cx=(minX+maxX)/2;val cy=(minY+maxY)/2
    val primary=MaterialTheme.colorScheme.primary
    val foreground=MaterialTheme.colorScheme.onSurface
    val grid=MaterialTheme.colorScheme.outlineVariant
    val background=MaterialTheme.colorScheme.surface
    val padding=with(LocalDensity.current){40.dp.toPx()}
    val scaleBarTarget=with(LocalDensity.current){80.dp.toPx()}
    Panel {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Offline GPS route",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            TextButton({zoom=1f;pan=Offset.Zero}){Text("Fit route")}
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.background)
            .semantics { contentDescription="Offline route with ${points.size} recorded GPS points. North is up. Pinch to zoom and drag to pan. No street map." }) {
            val scale=min((constraints.maxWidth-padding*2)/max(50.0,maxX-minX),(constraints.maxHeight-padding*2)/max(50.0,maxY-minY)).coerceAtLeast(.000001)*zoom
            val target=scaleBarTarget/scale
            val power=10.0.pow(floor(log10(target)))
            val barMeters=listOf(1.0,2.0,5.0,10.0).map{it*power}.lastOrNull{it<=target} ?: power
            Canvas(Modifier.matchParentSize().transformable(gestures)) {
                val step=40.dp.toPx()
                var x=0f;while(x<size.width){drawLine(grid.copy(alpha=.45f),Offset(x,0f),Offset(x,size.height));x+=step}
                var y=0f;while(y<size.height){drawLine(grid.copy(alpha=.45f),Offset(0f,y),Offset(size.width,y));y+=step}
                fun at(p: Triple<Int,Double,Double>)=Offset((size.width/2+(p.second-cx)*scale).toFloat()+pan.x,(size.height/2-(p.third-cy)*scale).toFloat()+pan.y)
                projected.groupBy{it.first}.values.forEach { segment ->
                    val stride=(segment.size/3000).coerceAtLeast(1)
                    val path=Path()
                    segment.filterIndexed{i,_->i%stride==0 || i==segment.lastIndex}.forEachIndexed{i,p ->val pos=at(p);if(i==0)path.moveTo(pos.x,pos.y)else path.lineTo(pos.x,pos.y)}
                    drawPath(path,primary,style=Stroke(width=3.dp.toPx()))
                }
                drawCircle(background,8.dp.toPx(),at(projected.first()))
                drawCircle(primary,6.dp.toPx(),at(projected.first()))
                val end=at(projected.last());val side=10.dp.toPx()
                drawRect(foreground,Offset(end.x-side/2,end.y-side/2),Size(side,side))
                val barWidth=(barMeters*scale).toFloat()
                drawLine(foreground,Offset(16.dp.toPx(),size.height-16.dp.toPx()),Offset(16.dp.toPx()+barWidth,size.height-16.dp.toPx()),2.dp.toPx())
            }
            Text("N ↑",Modifier.align(Alignment.TopEnd).padding(12.dp),color=foreground,style=MaterialTheme.typography.labelLarge)
            Text(if(barMeters>=1000)"${number(barMeters/1000)} km" else "${number(barMeters)} m",Modifier.align(Alignment.BottomStart).padding(start=16.dp,bottom=22.dp),color=foreground,style=MaterialTheme.typography.labelSmall)
        }
        Text("● Start    ■ Current / end",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
