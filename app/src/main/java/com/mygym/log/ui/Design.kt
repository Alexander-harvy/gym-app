package com.mygym.log.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mygym.log.data.WorkoutSet
import com.mygym.log.data.Preferences
import com.mygym.log.domain.Weight
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val Yellow = Color(0xFFF3D447)
@Composable
fun GymTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val colors = if (dark) darkColorScheme(
        primary = Yellow, onPrimary = Color(0xFF24200A), primaryContainer = Color(0xFF39331B), onPrimaryContainer = Color(0xFFFFE783),
        secondary = Color(0xFFD1C999), onSecondary = Color(0xFF302D16), secondaryContainer = Color(0xFF343226),
        background = Color(0xFF101112), onBackground = Color(0xFFF3F3EF), surface = Color(0xFF1B1D1F), onSurface = Color(0xFFF3F3EF),
        surfaceVariant = Color(0xFF292B2D), onSurfaceVariant = Color(0xFFB7B9B8), outline = Color(0xFF5D605F), outlineVariant = Color(0xFF343739)
    ) else lightColorScheme(
        primary = Color(0xFF6D5900), onPrimary = Color.White, primaryContainer = Color(0xFFFFE783), onPrimaryContainer = Color(0xFF24200A),
        secondary = Color(0xFF655E3F), background = Color(0xFFF7F7F2), surface = Color.White, surfaceVariant = Color(0xFFEAEAE2),
        onSurfaceVariant = Color(0xFF55564F), outlineVariant = Color(0xFFDDDED4)
    )
    MaterialTheme(colorScheme = colors, shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)
    ), content = content)
}

@Composable fun Eyebrow(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}
@Composable fun Badge(text: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
@Composable fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (action != null) TextButton(onAction) { Text(action) }
    }
}
@Composable fun EmptyState(title: String, detail: String, icon: ImageVector = Icons.AutoMirrored.Filled.List) {
    Panel {
        Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

data class MenuAction(val label: String, val destructive: Boolean = false, val action: () -> Unit)
@Composable fun MoreMenu(label: String, busy: Boolean, actions: List<MenuAction>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton({ expanded = true }, enabled = !busy) { Icon(Icons.Default.MoreVert, contentDescription = label) }
        DropdownMenu(expanded, { expanded = false }) {
            actions.forEach { item -> DropdownMenuItem(text = {
                Text(item.label, color = if (item.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            }, onClick = { expanded = false; item.action() }) }
        }
    }
}

@Composable fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable fun SetRow(index: Int, set: WorkoutSet, prefs: Preferences, busy: Boolean, edit: () -> Unit, complete: (Boolean) -> Unit) {
    Surface(color = if (set.completed) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f) else MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, if (set.completed) MaterialTheme.colorScheme.primary.copy(alpha = .3f) else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(index.toString(), Modifier.width(26.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(edit, Modifier.weight(1f).heightIn(min = 60.dp).semantics {
                contentDescription = "Edit set $index, ${number(Weight.display(set.weightKg, prefs.unit))} ${prefs.unit}, ${set.reps} reps, ${if (set.warmup) "warm-up" else "working"}"
            }, enabled = !busy) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${number(Weight.display(set.weightKg, prefs.unit))} ${prefs.unit}  ×  ${set.reps} reps", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                    Text(if (set.warmup) "Warm-up" else "Working set", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
            IconToggleButton(set.completed, complete, enabled = !busy, modifier = Modifier.size(48.dp)) {
                Surface(shape = RoundedCornerShape(10.dp), color = if (set.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
                    Icon(Icons.Default.Check, if (set.completed) "Mark set $index incomplete" else "Complete set $index", Modifier.padding(7.dp).size(20.dp),
                        tint = if (set.completed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable fun WeekBars(weeks: Map<LocalDate, Int>) {
    val monday = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    val dates = (5L downTo 0L).map { monday.minusWeeks(it) }
    val maximum = dates.maxOf { weeks[it] ?: 0 }.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        dates.forEach { day ->
            val count = weeks[day] ?: 0
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {
                contentDescription = "Week of $day, $count completed workouts"
            }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(count.toString(), style = MaterialTheme.typography.labelMedium)
                Box(Modifier.fillMaxWidth().height(80.dp).background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp)), contentAlignment = Alignment.BottomCenter) {
                    if (count > 0) Box(Modifier.fillMaxWidth().fillMaxHeight(count.toFloat() / maximum).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)))
                }
                Text(day.format(DateTimeFormatter.ofPattern("M/d")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
