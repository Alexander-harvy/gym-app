package com.mygym.log.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mygym.log.data.*
import com.mygym.log.domain.*
import java.time.*
import kotlin.math.max

@Composable fun BodyScreen(vm: GymViewModel,data: Snapshot,prefs: Preferences,busy: Boolean) {
    var editing by remember{mutableStateOf<BodyMeasurement?>(null)}
    var showForm by remember{mutableStateOf(false)}
    var deleting by remember{mutableStateOf<BodyMeasurement?>(null)}
    var showAll by rememberSaveable{mutableStateOf(false)}
    val entries=data.bodyMeasurements.sortedByDescending{it.recordedAt}
    val weight=BodyProgress.latestWeight(entries)
    val height=BodyProgress.latestHeight(entries)
    Page("Body progress","Your measurements, stored only on this device.") {
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            StatTile(weight?.weightKg?.let{"${number(Weight.display(it,prefs.unit))} ${prefs.unit}"} ?: "—","Latest body weight",Modifier.weight(1f))
            StatTile(height?.heightCm?.let{"${number(it)} cm"} ?: "—","Latest height",Modifier.weight(1f))
        }
        BodyProgress.weightChangeKg(entries)?.let { delta -> Text("Change since first weight entry: ${if(delta>0)"+" else ""}${number(Weight.display(delta,prefs.unit))} ${prefs.unit}",color=MaterialTheme.colorScheme.onSurfaceVariant) }
        Action("Log measurement",!busy){editing=null;showForm=true}
        BodyWeightTrend(entries,prefs.unit)
        if(entries.isEmpty())EmptyState("Start your measurement history","Log weight, height, or both. Body weight stays separate from exercise loads.")
        else SectionTitle("Measurement history")
        (if(showAll)entries else entries.take(10)).forEach { value -> Panel {
            Row {
                Text(Instant.ofEpochMilli(value.recordedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString(),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                MoreMenu("Measurement options for ${date(value.recordedAt)}",busy,listOf(MenuAction("Edit measurement"){editing=value;showForm=true},MenuAction("Delete measurement",true){deleting=value}))
            }
            value.weightKg?.let {Text("${number(Weight.display(it,prefs.unit))} ${prefs.unit} body weight")}
            value.heightCm?.let {Text("${number(it)} cm height")}
            if(value.notes.isNotBlank())Text(value.notes,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        if(entries.size>10)TextButton({showAll=!showAll}){Text(if(showAll)"Show recent entries" else "Show all ${entries.size} entries")}
    }
    if(showForm)BodyMeasurementForm(editing,prefs.unit,busy,{showForm=false}) { value -> vm.act(onSuccess={showForm=false}){vm.repo.bodyMeasurement(value)} }
    deleting?.let { value ->Confirm("Delete measurement?","This removes only this body measurement. Workouts and runs are kept.",busy,{deleting=null},{vm.act(onSuccess={deleting=null}){vm.repo.dao.deleteBodyMeasurement(value.id)}}) }
}

@Composable fun BodyMeasurementForm(existing: BodyMeasurement?,unit: String,busy: Boolean,dismiss: ()->Unit,save: (BodyMeasurement)->Unit) {
    val id=remember{existing?.id ?: newId()}
    val oldWeight=existing?.weightKg?.let{number(Weight.display(it,unit))} ?: ""
    val oldHeight=existing?.heightCm?.let{number(it)} ?: ""
    var weight by remember{mutableStateOf(oldWeight)}
    var height by remember{mutableStateOf(oldHeight)}
    var day by remember{mutableStateOf(existing?.recordedAt?.let{Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()}?.toString() ?: LocalDate.now().toString())}
    var notes by remember{mutableStateOf(existing?.notes ?: "")}
    var error by remember{mutableStateOf<String?>(null)}
    Form(if(existing==null)"Log body measurement" else "Edit body measurement",busy,dismiss,{
        runCatching {
            val value=BodyMeasurement(id,BodyProgress.timestamp(day,existing?.recordedAt),
                weightKg=if(weight.isBlank())null else if(weight==oldWeight && existing?.weightKg!=null)existing.weightKg else Weight.canonical(weight.toDoubleOrNull() ?: Double.NaN,unit),
                heightCm=if(height.isBlank())null else if(height==oldHeight && existing?.heightCm!=null)existing.heightCm else height.toDoubleOrNull() ?: Double.NaN,notes=notes)
            Rules.body(value);value
        }.onSuccess{error=null;save(it)}.onFailure{error=it.message ?: "Check the measurement values."}
    }) {
        Input("Body weight ($unit, optional)",weight,{weight=it},true)
        Input("Height (cm, optional)",height,{height=it},true)
        Input("Date (YYYY-MM-DD)",day,{day=it})
        Disclosure("Notes",initiallyExpanded=notes.isNotBlank()){Input("Measurement notes",notes,{notes=it},multiline=true)}
        Text("Enter weight, height, or both. This is separate from external weights lifted.",style=MaterialTheme.typography.bodySmall)
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }
}

@Composable fun BodyWeightTrend(entries: List<BodyMeasurement>,unit: String) {
    val values=entries.filter{it.weightKg!=null}.sortedBy{it.recordedAt}.takeLast(30)
    if(values.size<2)return
    val weights=values.map{Weight.display(it.weightKg!!,unit)}
    val min=weights.min();val max=weights.max();val span=max(.5,max-min)
    val color=MaterialTheme.colorScheme.primary
    Panel {
        Text("Body weight · last ${values.size} entries",style=MaterialTheme.typography.titleMedium)
        Text("${number(min)}–${number(max)} $unit",color=MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().height(140.dp).semantics{contentDescription="Body-weight trend, ${values.size} entries, range ${number(min)} to ${number(max)} $unit. Exact values are in measurement history."}) {
            val padding=8.dp.toPx();val timeSpan=(values.last().recordedAt-values.first().recordedAt).coerceAtLeast(1)
            val points=values.mapIndexed{index,value->
                val x=if(values.last().recordedAt==values.first().recordedAt)index.toFloat()/values.lastIndex else (value.recordedAt-values.first().recordedAt).toFloat()/timeSpan
                Offset(padding+x*(size.width-2*padding),(padding+(1-(weights[index]-min)/span)*(size.height-2*padding)).toFloat())
            }
            val path=Path();points.forEachIndexed{i,p->if(i==0)path.moveTo(p.x,p.y)else path.lineTo(p.x,p.y)}
            drawPath(path,color,style=Stroke(2.dp.toPx()));points.forEach{drawCircle(color,3.dp.toPx(),it)}
        }
    }
}
