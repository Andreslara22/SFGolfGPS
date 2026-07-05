package mx.clubsanfrancisco.golfgps

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private enum class Screen(val label: String, val emoji: String) {
    GPS("GPS", "📍"),
    SCORECARD("Tarjeta", "🗒️"),
    PLAYERS("Jugadores", "👥"),
    SETTINGS("Ajustes", "⚙️")
}

@Composable
fun GolfApp(vm: GolfViewModel, onRequestPermission: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf(Screen.GPS.name) }
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
                        label = { Text(s.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (current) {
                Screen.GPS -> GpsScreen(vm, onRequestPermission)
                Screen.SCORECARD -> ScorecardScreen(vm)
                Screen.PLAYERS -> PlayersScreen(vm)
                Screen.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
}

// ---------------------------------------------------------------- GPS

@Composable
private fun GpsScreen(vm: GolfViewModel, onRequestPermission: () -> Unit) {
    val hole = vm.currentHole
    val distM = vm.distanceToGreenMeters()
    val yards = vm.units == Units.YARDS

    val distValue: Int? = distM?.let {
        if (yards) metersToYards(it).roundToInt() else it.roundToInt()
    }
    val unitLabel = if (yards) "yardas" else "metros"
    val refDist = if (yards) metersToYards(hole.referenceMeters).roundToInt()
                  else hole.referenceMeters.roundToInt()
    val clubYards = distM?.let { metersToYards(it) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            // Encabezado del hoyo
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "HOYO ${hole.number}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "PAR ${hole.par}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                if (vm.autoDetect) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            "AUTO",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                "Tee → green: $refDist $unitLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            // Distancia gigante
            if (!vm.hasLocationPermission) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Se necesita permiso de ubicación para medir la distancia al green.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRequestPermission) { Text("Activar GPS") }
                Spacer(Modifier.height(24.dp))
            } else {
                Text(
                    text = distValue?.toString() ?: "– – –",
                    fontSize = 112.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = 112.sp
                )
                Text(
                    "$unitLabel al centro del green",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    vm.gpsAccuracyM?.let { "Precisión GPS: ±${it.roundToInt()} m" }
                        ?: "Buscando señal GPS…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            // Palo sugerido
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "PALO SUGERIDO",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        clubYards?.let { recommendedClub(it) } ?: "Esperando GPS",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Navegación de hoyos
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { vm.previousHole() }, modifier = Modifier.weight(1f)) {
                    Text("◀ Anterior")
                }
                OutlinedButton(
                    onClick = { vm.enableAutoDetect() },
                    modifier = Modifier.weight(1f),
                    enabled = !vm.autoDetect
                ) { Text("AUTO") }
                OutlinedButton(onClick = { vm.nextHole() }, modifier = Modifier.weight(1f)) {
                    Text("Siguiente ▶")
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "GOLPES · HOYO ${hole.number}",
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
                onAdd = { vm.addStroke(i) },
                onRemove = { vm.removeStroke(i) }
            )
            Spacer(Modifier.height(8.dp))
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun StrokeRow(name: String, strokes: Int, par: Int, onAdd: () -> Unit, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                modifier = Modifier.size(44.dp)
            ) { Text("+", fontSize = 22.sp) }
        }
    }
}

private fun scoreName(diff: Int): String = when {
    diff <= -3 -> "Albatros"
    diff == -2 -> "Eagle"
    diff == -1 -> "Birdie"
    diff == 0 -> "Par"
    diff == 1 -> "Bogey"
    diff == 2 -> "Doble bogey"
    else -> "+$diff"
}

@Composable
private fun scoreColor(diff: Int): Color = when {
    diff < 0 -> MaterialTheme.colorScheme.primary
    diff == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.error
}

// ---------------------------------------------------------------- Tarjeta

@Composable
private fun ScorecardScreen(vm: GolfViewModel) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Text(
                "Tarjeta de puntuación",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${CourseData.CLUB_NAME} · Par ${CourseData.totalPar}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            HeaderRow(vm)
            Divider()
        }
        items(CourseData.holes) { hole ->
            HoleRow(vm, hole)
            if (hole.number == 9) {
                TotalsRow(vm, "OUT", 0 until 9)
                Divider()
            }
        }
        item {
            TotalsRow(vm, "IN", 9 until 18)
            Divider(thickness = 2.dp)
            TotalsRow(vm, "TOTAL", 0 until 18, bold = true)
            RelativeRow(vm)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeaderRow(vm: GolfViewModel) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("Hoyo", Modifier.width(46.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else Color.Transparent
            )
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            hole.number.toString(),
            Modifier.width(46.dp),
            fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Normal
        )
        Text(hole.par.toString(), Modifier.width(36.dp), textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        vm.players.forEach { p ->
            val s = p.strokes[hole.number - 1]
            Text(
                if (s > 0) s.toString() else "·",
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                color = if (s > 0) scoreColor(s - hole.par) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TotalsRow(vm: GolfViewModel, label: String, range: IntRange, bold: Boolean = false) {
    val parSum = range.sumOf { CourseData.holes[it].par }
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
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
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
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

// ---------------------------------------------------------------- Jugadores

@Composable
private fun PlayersScreen(vm: GolfViewModel) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Jugadores", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Hasta 5 jugadores por ronda",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
        }
        itemsIndexed(vm.players) { i, player ->
            var name by remember(player) { mutableStateOf(player.name) }
            Card(
                Modifier.fillMaxWidth(),
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
                        label = { Text("Jugador ${i + 1}") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (vm.players.size > 1) {
                        TextButton(onClick = { vm.removePlayer(i) }) {
                            Text("Quitar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        item {
            if (vm.players.size < 5) {
                Button(onClick = { vm.addPlayer() }, modifier = Modifier.fillMaxWidth()) {
                    Text("+ Agregar jugador")
                }
            } else {
                Text(
                    "Máximo de 5 jugadores alcanzado",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Ajustes

@Composable
private fun SettingsScreen(vm: GolfViewModel) {
    var showResetDialog by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Ajustes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))

            Text("Unidades", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceButton("Yardas", vm.units == Units.YARDS) { vm.setUnitsAndSave(Units.YARDS) }
                ChoiceButton("Metros", vm.units == Units.METERS) { vm.setUnitsAndSave(Units.METERS) }
            }

            Spacer(Modifier.height(22.dp))
            Text("Tema", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceButton("Sistema", vm.themeMode == ThemeMode.SYSTEM) { vm.setThemeAndSave(ThemeMode.SYSTEM) }
                ChoiceButton("Claro", vm.themeMode == ThemeMode.LIGHT) { vm.setThemeAndSave(ThemeMode.LIGHT) }
                ChoiceButton("Oscuro", vm.themeMode == ThemeMode.DARK) { vm.setThemeAndSave(ThemeMode.DARK) }
            }

            Spacer(Modifier.height(22.dp))
            Text("Ronda", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { showResetDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) { Text("Reiniciar ronda (borrar golpes)") }

            Spacer(Modifier.height(28.dp))
            Divider()
            Spacer(Modifier.height(14.dp))
            Text(
                CourseData.CLUB_NAME,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "${CourseData.CITY} · 18 hoyos · Par ${CourseData.totalPar}\n" +
                "La pantalla permanece encendida durante la ronda.\n" +
                "Distancias calculadas con GPS (fórmula de Haversine) al centro del green.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("¿Reiniciar ronda?") },
            text = { Text("Se borrarán los golpes de todos los jugadores en los 18 hoyos.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetRound()
                    showResetDialog = false
                }) { Text("Sí, reiniciar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}
