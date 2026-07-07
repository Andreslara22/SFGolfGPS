package mx.clubsanfrancisco.golfgps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class Screen(val label: String, val emoji: String) {
    RANGE("Range", "⛳"),
    SCORECARD("Scorecard", "📋"),
    PLAYERS("Players", "🏌️"),
    SETTINGS("Settings", "⚙️")
}

@Composable
fun GolfApp(vm: GolfViewModel, onRequestPermission: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf(Screen.RANGE.name) }
    val current = Screen.valueOf(screen)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                Screen.values().forEach { s ->
                    NavigationBarItem(
                        selected = current == s,
                        onClick = { screen = s.name },
                        icon = { Text(s.emoji, fontSize = 20.sp) },
                        label = { Text(s.label, maxLines = 1) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (current) {
                Screen.RANGE -> RangeScreen(vm, onRequestPermission)
                Screen.SCORECARD -> ScorecardScreen(vm)
                Screen.PLAYERS -> PlayersScreen(vm)
                Screen.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
}

// ---------------------------------------------------------------- Range (GPS)

@Composable
private fun RangeScreen(vm: GolfViewModel, onRequestPermission: () -> Unit) {
    val hole = vm.currentHole
    val distM = vm.distanceToGreenMeters()
    val yards = vm.units == Units.YARDS
    val haptics = LocalHapticFeedback.current

    // Pin position shifts the target proportionally to this green's depth:
    // red (front) ≈ -depth/4 · blue (back) ≈ +depth/4
    val flag = vm.flags[vm.currentHoleIndex]
    val pinShiftM = hole.greenDepthM / 4.0
    val flagOffsetM = when (flag) { 0 -> -pinShiftM; 2 -> pinShiftM; else -> 0.0 }
    val distAdjM = distM?.plus(flagOffsetM)?.coerceAtLeast(0.0)

    val distValue: Int? = distAdjM?.let {
        if (yards) metersToYards(it).roundToInt() else it.roundToInt()
    }
    val unitShort = if (yards) "yd" else "m"
    val refDist = if (yards) metersToYards(hole.referenceMeters).roundToInt()
                  else hole.referenceMeters.roundToInt()

    // "Plays like": distancia efectiva por elevación (autocalibrada en greens).
    val elevDeltaM = vm.elevationDeltaM()
    val playsLikeAdjM = vm.playsLikeMeters()?.plus(flagOffsetM)?.coerceAtLeast(0.0)

    // El palo sugerido usa la distancia efectiva cuando existe.
    val clubYards = (playsLikeAdjM ?: distAdjM)?.let { metersToYards(it) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "⛳ HOLE ${hole.number}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Pill("PAR ${hole.par}", MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer)
                if (vm.autoDetect) {
                    Spacer(Modifier.width(6.dp))
                    Pill("AUTO", MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Text(
                "Tee → green: $refDist $unitShort",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "PIN",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                PinDot(Color(0xFFE85D4A), flag == 0) { vm.setFlagRotation(vm.currentHoleIndex, 0) }
                Spacer(Modifier.width(8.dp))
                PinDot(Color(0xFFF4F1E8), flag == 1) { vm.setFlagRotation(vm.currentHoleIndex, 1) }
                Spacer(Modifier.width(8.dp))
                PinDot(Color(0xFF5AB0FF), flag == 2) { vm.setFlagRotation(vm.currentHoleIndex, 2) }
                if (flag >= 0) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "✕ clear",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { vm.clearFlags() }
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            if (!vm.hasLocationPermission) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Location permission is needed to measure your distance to the green.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRequestPermission) { Text("Enable GPS") }
                Spacer(Modifier.height(24.dp))
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = distValue?.toString() ?: "– – –",
                        fontSize = 108.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 108.sp
                    )
                    Text(
                        " $unitShort",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 18.dp)
                    )
                }
                Text(
                    when (flag) {
                        0 -> "to RED pin (front)"
                        1 -> "to WHITE pin (middle)"
                        2 -> "to BLUE pin (back)"
                        else -> "to center of green"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = when (flag) {
                        0 -> Color(0xFFE85D4A)
                        2 -> Color(0xFF5AB0FF)
                        else -> MaterialTheme.colorScheme.primary
                    },
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    vm.gpsAccuracyM?.let { "🛰️ GPS accuracy: ±${it.roundToInt()} m" }
                        ?: "🛰️ Searching for GPS signal…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // "Plays like" por elevación (aparece tras pisar este green una vez)
                if (playsLikeAdjM != null && elevDeltaM != null) {
                    val plV = if (yards) metersToYards(playsLikeAdjM).roundToInt()
                              else playsLikeAdjM.roundToInt()
                    val up = elevDeltaM > 0
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "PLAYS LIKE ",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "$plV $unitShort",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = if (up) Color(0xFFE85D4A) else Color(0xFF5AB0FF)
                        )
                        Text(
                            if (up) "  ▲ +${elevDeltaM.roundToInt()} m"
                            else "  ▼ ${elevDeltaM.roundToInt()} m",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (up) Color(0xFFE85D4A) else Color(0xFF5AB0FF)
                        )
                    }
                }
                if (distM != null) {
                    // Front/back reales usando la profundidad de este green
                    val half = hole.greenDepthM / 2.0
                    val fM = (distM - half).coerceAtLeast(0.0)
                    val bM = distM + half
                    val fV = if (yards) metersToYards(fM).roundToInt() else fM.roundToInt()
                    val bV = if (yards) metersToYards(bM).roundToInt() else bM.roundToInt()
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        FcbLabel("F", fV)
                        FcbLabel("C", distValue ?: 0)
                        FcbLabel("B", bV)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            val activeP = vm.players.getOrNull(vm.activePlayerIndex) ?: vm.players.first()
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏌️", fontSize = 30.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "SUGGESTED CLUB · ${activeP.name.uppercase().take(12)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                clubYards?.let { clubForDistance(it, activeP.clubYards) } ?: "Waiting for GPS",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (vm.players.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            vm.players.forEachIndexed { i, p ->
                                MiniChip(p.name.take(8), i == vm.activePlayerIndex) {
                                    vm.activePlayerIndex = i
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            HoleMapCard(hole, vm.userLat, vm.userLng, vm.units)

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { vm.previousHole() },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) { Text("◀ Prev", maxLines = 1) }
                OutlinedButton(
                    onClick = { vm.toggleAutoDetect() },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    colors = if (vm.autoDetect)
                        ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer)
                    else ButtonDefaults.outlinedButtonColors()
                ) { Text("AUTO", maxLines = 1) }
                OutlinedButton(
                    onClick = { vm.nextHole() },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) { Text("Next ▶", maxLines = 1) }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "STROKES · HOLE ${hole.number}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
        }

        itemsIndexed(vm.players) { i, player ->
            StrokeRow(
                name = player.name,
                strokes = player.strokes[vm.currentHoleIndex],
                par = hole.par,
                onAdd = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.addStroke(i)
                },
                onRemove = { vm.removeStroke(i) }
            )
            Spacer(Modifier.height(8.dp))
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PinDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

@Composable
private fun FcbLabel(letter: String, value: Int) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            letter,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(3.dp))
        Text(
            value.toString(),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color) {
    Surface(shape = RoundedCornerShape(50), color = bg) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = fg
        )
    }
}

@Composable
private fun StrokeRow(name: String, strokes: Int, par: Int, onAdd: () -> Unit, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold)
                if (strokes > 0) {
                    val diff = strokes - par
                    Text(
                        scoreName(diff),
                        style = MaterialTheme.typography.bodySmall,
                        color = scoreColor(diff)
                    )
                }
            }
            OutlinedButton(
                onClick = onRemove,
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(44.dp)
            ) { Text("−", fontSize = 22.sp) }
            Text(
                strokes.toString(),
                modifier = Modifier.width(52.dp),
                textAlign = TextAlign.Center,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black
            )
            Button(
                onClick = onAdd,
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(44.dp)
            ) { Text("+", fontSize = 22.sp) }
        }
    }
}

private fun scoreName(diff: Int): String = when {
    diff <= -3 -> "Albatross 🦅"
    diff == -2 -> "Eagle 🦅"
    diff == -1 -> "Birdie 🐦"
    diff == 0 -> "Par"
    diff == 1 -> "Bogey"
    diff == 2 -> "Double bogey"
    else -> "+$diff"
}

@Composable
private fun scoreColor(diff: Int): Color = when {
    diff < 0 -> MaterialTheme.colorScheme.primary
    diff == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.error
}

// ---------------------------------------------------------------- Scorecard

@Composable
private fun ScorecardScreen(vm: GolfViewModel) {
    var showFinishDialog by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("MMM d · h:mm a", Locale.US) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Text("📋 Scorecard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "${CourseData.CLUB_NAME} · Par ${CourseData.totalPar}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            // Per-player summary chips
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                vm.players.forEach { p ->
                    SummaryChip(p)
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer)) {
                        HeaderRow(vm)
                    }
                    CourseData.holes.forEach { hole ->
                        HoleRow(vm, hole)
                        if (hole.number == 9) {
                            Box(Modifier.fillMaxWidth().background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                                TotalsRow(vm, "OUT", 0 until 9)
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                        TotalsRow(vm, "IN", 9 until 18)
                    }
                    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer)) {
                        Column {
                            TotalsRow(vm, "TOTAL", 0 until 18, bold = true)
                            RelativeRow(vm)
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "⭕ under par · ⬜ over par",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))

            Button(
                onClick = { showFinishDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) { Text("🏁 Finish round & save") }

            Spacer(Modifier.height(22.dp))
            Text("Round history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (vm.history.isEmpty()) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        "No saved rounds yet. Finish a round to keep it here. 🌱",
                        Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        itemsIndexed(vm.history) { i, round ->
            RoundHistoryCard(round, dateFmt) { vm.deleteRound(i) }
            Spacer(Modifier.height(8.dp))
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text("Finish this round?") },
            text = { Text("Scores will be saved to your history and the scorecard will reset to hole 1.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.finishRound()
                    showFinishDialog = false
                }) { Text("Save & reset") }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SummaryChip(p: Player) {
    val rel = p.relativeToPar()
    val relLabel = when {
        p.playedHoles() == 0 -> "–"
        rel == 0 -> "E"
        rel > 0 -> "+$rel"
        else -> "$rel"
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(p.name.take(10), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${p.total()}", fontWeight = FontWeight.Black, fontSize = 20.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    relLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (p.playedHoles() > 0) scoreColor(rel.coerceIn(-1, 1))
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "${p.playedHoles()}/18",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun RoundHistoryCard(round: SavedRound, fmt: SimpleDateFormat, onDelete: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "🏆 " + fmt.format(Date(round.date)),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                round.entries.forEach { e ->
                    val relLabel = when {
                        e.relative == 0 -> "E"
                        e.relative > 0 -> "+${e.relative}"
                        else -> "${e.relative}"
                    }
                    Text(
                        "${e.name}: ${e.strokes} strokes ($relLabel · ${e.holes}/18 holes)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun HeaderRow(vm: GolfViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp)) {
        Text("Hole", Modifier.width(46.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("Par", Modifier.width(36.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center)
        vm.players.forEach { p ->
            Text(
                p.name.take(6),
                Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HoleRow(vm: GolfViewModel, hole: Hole) {
    val isCurrent = vm.currentHoleIndex == hole.number - 1
    val stripe = hole.number % 2 == 0
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                when {
                    isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    stripe -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    else -> Color.Transparent
                }
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            (if (isCurrent) "⛳" else "") + hole.number.toString(),
            Modifier.width(46.dp),
            fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Normal
        )
        Text(hole.par.toString(), Modifier.width(36.dp), textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        vm.players.forEach { p ->
            val s = p.strokes[hole.number - 1]
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ScoreCell(s, hole.par)
            }
        }
    }
}

@Composable
private fun ScoreCell(strokes: Int, par: Int) {
    if (strokes <= 0) {
        Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val diff = strokes - par
    val shape = if (diff < 0) CircleShape else RoundedCornerShape(5.dp)
    val frame = when {
        diff < 0 -> MaterialTheme.colorScheme.primary
        diff > 0 -> MaterialTheme.colorScheme.error
        else -> Color.Transparent
    }
    Box(
        Modifier.size(27.dp).border(1.6.dp, frame, shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            strokes.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = scoreColor(diff)
        )
    }
}

@Composable
private fun TotalsRow(vm: GolfViewModel, label: String, range: IntRange, bold: Boolean = false) {
    val parSum = range.sumOf { CourseData.holes[it].par }
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(label, Modifier.width(46.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(parSum.toString(), Modifier.width(36.dp), textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold, fontSize = 13.sp)
        vm.players.forEach { p ->
            val sum = range.sumOf { p.strokes[it] }
            Text(
                if (sum > 0) sum.toString() else "·",
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontWeight = if (bold) FontWeight.Black else FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RelativeRow(vm: GolfViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text("vs Par", Modifier.width(82.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        vm.players.forEach { p ->
            val rel = p.relativeToPar()
            val label = when {
                p.playedHoles() == 0 -> "·"
                rel == 0 -> "E"
                rel > 0 -> "+$rel"
                else -> rel.toString()
            }
            Text(
                label,
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Black,
                color = if (p.playedHoles() > 0) scoreColor(rel.coerceIn(-1, 1))
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------- Players

@Composable
private fun PlayersScreen(vm: GolfViewModel) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("🏌️ Players", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Up to 5 players per round",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
        }
        itemsIndexed(vm.players) { i, player ->
            var name by remember(player) { mutableStateOf(player.name) }
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it.take(14)
                            vm.renamePlayer(i, name)
                        },
                        label = { Text("Player ${i + 1}") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (vm.players.size > 1) {
                        TextButton(onClick = { vm.removePlayer(i) }) {
                            Text("Remove", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        item {
            if (vm.players.size < 5) {
                Button(
                    onClick = { vm.addPlayer() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("＋ Add player") }
            } else {
                Text(
                    "Maximum of 5 players reached",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Settings

@Composable
private fun SettingsScreen(vm: GolfViewModel) {
    var showResetDialog by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("⚙️ Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))

            Text("Units", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceButton("Yards", vm.units == Units.YARDS) { vm.setUnitsAndSave(Units.YARDS) }
                ChoiceButton("Meters", vm.units == Units.METERS) { vm.setUnitsAndSave(Units.METERS) }
            }

            Spacer(Modifier.height(22.dp))
            Text("Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceButton("System", vm.themeMode == ThemeMode.SYSTEM) { vm.setThemeAndSave(ThemeMode.SYSTEM) }
                ChoiceButton("Light", vm.themeMode == ThemeMode.LIGHT) { vm.setThemeAndSave(ThemeMode.LIGHT) }
                ChoiceButton("Dark", vm.themeMode == ThemeMode.DARK) { vm.setThemeAndSave(ThemeMode.DARK) }
            }

            Spacer(Modifier.height(22.dp))
            Text("Elevation (\"plays like\")", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "The app learns each green's elevation when you walk onto it with GPS on. " +
                "After one round, distances adjust automatically for uphill/downhill shots.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(
                    "${vm.calibratedGreens}/18 greens calibrated",
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(10.dp))
                if (vm.calibratedGreens > 0) {
                    Text(
                        "✕ reset",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable { vm.resetElevations() }
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            Text("Club distances (yd)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Used for the suggested club, per player",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                vm.players.forEachIndexed { i, p ->
                    MiniChip(p.name.take(8), i == vm.activePlayerIndex) {
                        vm.activePlayerIndex = i
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val cp = vm.players.getOrNull(vm.activePlayerIndex) ?: vm.players.first()
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    clubNames.forEachIndexed { ci, clubName ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(clubName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            OutlinedButton(
                                onClick = { vm.adjustClub(vm.activePlayerIndex, ci, -5) },
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(36.dp)
                            ) { Text("−") }
                            Text(
                                "${cp.clubYards[ci]}",
                                Modifier.width(52.dp),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            OutlinedButton(
                                onClick = { vm.adjustClub(vm.activePlayerIndex, ci, 5) },
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(36.dp)
                            ) { Text("+") }
                        }
                    }
                    TextButton(onClick = { vm.resetClubs(vm.activePlayerIndex) }) {
                        Text("Reset to defaults")
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Text("Round", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { showResetDialog = true },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) { Text("Clear strokes (without saving)") }

            Spacer(Modifier.height(28.dp))
            Divider()
            Spacer(Modifier.height(14.dp))
            Text(
                "⛳ ${CourseData.CLUB_NAME}",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "${CourseData.CITY} · 18 holes · Par ${CourseData.totalPar}\n" +
                "Screen stays awake during your round.\n" +
                "Distances measured by GPS (Haversine) to the center of the green.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Clear all strokes?") },
            text = { Text("This wipes the current scorecard for every player without saving it to history.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearStrokes()
                    showResetDialog = false
                }) { Text("Yes, clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun MiniChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary
             else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
             else MaterialTheme.colorScheme.onSurface
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
            containerColor = bg, contentColor = fg
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)
    ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}
