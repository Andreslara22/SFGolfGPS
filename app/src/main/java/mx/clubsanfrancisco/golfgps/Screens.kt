package mx.clubsanfrancisco.golfgps

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class Screen(val emoji: String) {
    RANGE("⛳"), SCORECARD("📋"), STATS("📊"), PLAYERS("🏌️"), SETTINGS("⚙️")
}

@Composable
fun GolfApp(vm: GolfViewModel, onRequestPermission: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf(Screen.RANGE.name) }
    val current = Screen.valueOf(screen)
    val strings = if (vm.language == AppLanguage.EN) StringsEn else StringsEs

    CompositionLocalProvider(LocalStrings provides strings) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                Screen.values().forEach { s ->
                    NavigationBarItem(
                        selected = current == s,
                        onClick = { screen = s.name },
                        icon = { Text(s.emoji, fontSize = 20.sp) },
                        label = {
                            Text(
                                when (s) {
                                    Screen.RANGE -> strings.tabGps
                                    Screen.SCORECARD -> strings.tabCard
                                    Screen.STATS -> strings.tabStats
                                    Screen.PLAYERS -> strings.tabPlayers
                                    Screen.SETTINGS -> strings.tabSettings
                                },
                                maxLines = 1
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (current) {
                Screen.RANGE -> RangeScreen(vm, onRequestPermission)
                Screen.SCORECARD -> ScorecardScreen(vm)
                Screen.STATS -> StatsScreen(vm)
                Screen.PLAYERS -> PlayersScreen(vm)
                Screen.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
    }
}

// ---------------------------------------------------------------- Range (GPS)
// Rediseño "map-first" (referencia: Hole19/18Birdies): el mapa es la pantalla,
// las distancias van superpuestas y la anotación es una barra compacta.

@Composable
private fun RangeScreen(vm: GolfViewModel, onRequestPermission: () -> Unit) {
    val str = LocalStrings.current
    val hole = vm.currentHole
    val distM = vm.distanceToGreenMeters()
    val yards = vm.units == Units.YARDS
    val haptics = LocalHapticFeedback.current

    // El pin del día desplaza el objetivo según la profundidad de este green:
    // rojo (frente) ≈ -depth/4 · azul (fondo) ≈ +depth/4
    val flag = vm.flags[vm.currentHoleIndex]
    val pinShiftM = hole.greenDepthM / 4.0
    val flagOffsetM = when (flag) { 0 -> -pinShiftM; 2 -> pinShiftM; else -> 0.0 }
    val distAdjM = distM?.plus(flagOffsetM)?.coerceAtLeast(0.0)

    val distValue: Int? = distAdjM?.let {
        if (yards) metersToYards(it).roundToInt() else it.roundToInt()
    }
    val unitShort = if (yards) "yd" else "m"

    // "Juega como": distancia efectiva por elevación (autocalibrada en greens).
    val elevDeltaM = vm.elevationDeltaM()
    val playsLikeAdjM = vm.playsLikeMeters()?.plus(flagOffsetM)?.coerceAtLeast(0.0)

    // El palo sugerido usa la distancia efectiva cuando existe.
    val clubYards = (playsLikeAdjM ?: distAdjM)?.let { metersToYards(it) }

    var showAllPlayers by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total <= -70f) vm.nextHole()
                        else if (total >= 70f) vm.previousHole()
                    },
                    onHorizontalDrag = { _, dx -> total += dx }
                )
            }
    ) {
        item {
            Spacer(Modifier.height(8.dp))

            // ---- Header compacto: ‹ HOYO n · PAR p › + AUTO + pin del día ----
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "‹", fontSize = 30.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { vm.previousHole() }
                        .padding(horizontal = 10.dp)
                )
                Text(
                    "${str.holeWord} ${hole.number}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "· PAR ${hole.par}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "›", fontSize = 30.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { vm.nextHole() }
                        .padding(horizontal = 10.dp)
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (vm.autoDetect) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { vm.toggleAutoDetect() }
                ) {
                    Text(
                        "AUTO",
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = if (vm.autoDetect) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                FlagChip(flag) {
                    val next = when (flag) { -1 -> 0; 0 -> 1; 1 -> 2; else -> -1 }
                    if (next == -1) vm.clearFlags() else vm.setFlagRotation(vm.currentHoleIndex, next)
                }
            }

            // ---- Estado del GPS: puntito de color + valor ----
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 8.dp)
            ) {
                val acc = vm.gpsAccuracyM
                val dotColor = when {
                    !vm.hasLocationPermission || acc == null ->
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    acc <= 5f -> Color(0xFF4ADE80)
                    acc <= 15f -> Color(0xFFF3B61F)
                    else -> Color(0xFFE85D4A)
                }
                Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        !vm.hasLocationPermission -> str.gpsOff
                        acc == null -> str.gpsSearching
                        else -> "GPS ±${acc.roundToInt()} m"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!vm.hasLocationPermission) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            str.permissionMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onRequestPermission) { Text(str.enableGps) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // ---- Mapa héroe con distancias superpuestas (B/C/F, como el reloj) ----
            Box(Modifier.fillMaxWidth()) {
                HoleMapCard(hole, vm.userLat, vm.userLng, vm.units, flag)
                if (vm.hasLocationPermission) {
                    val mapShadow = Shadow(Color(0xCC1E2A1E), Offset(0f, 3f), 10f)
                    Column(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 16.dp, top = 14.dp)
                    ) {
                        if (distAdjM != null && distValue != null) {
                            val half = hole.greenDepthM / 2.0
                            val fM = (distAdjM - half).coerceAtLeast(0.0)
                            val bM = distAdjM + half
                            val fV = if (yards) metersToYards(fM).roundToInt() else fM.roundToInt()
                            val bV = if (yards) metersToYards(bM).roundToInt() else bM.roundToInt()
                            OverlayFcb("B", bV, mapShadow)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "$distValue",
                                    fontSize = 58.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    lineHeight = 58.sp,
                                    style = TextStyle(shadow = mapShadow)
                                )
                                Text(
                                    " $unitShort",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF7ADFA8),
                                    style = TextStyle(shadow = mapShadow),
                                    modifier = Modifier.padding(bottom = 9.dp)
                                )
                            }
                            OverlayFcb("F", fV, mapShadow)
                            if (playsLikeAdjM != null && elevDeltaM != null) {
                                val plV = if (yards) metersToYards(playsLikeAdjM).roundToInt()
                                          else playsLikeAdjM.roundToInt()
                                val up = elevDeltaM > 0
                                Spacer(Modifier.height(6.dp))
                                Surface(shape = RoundedCornerShape(50), color = Color(0xB3061E14)) {
                                    Text(
                                        "${str.playsLike} $plV ${if (up) "▲" else "▼"}",
                                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (up) Color(0xFFFF8A7A) else Color(0xFF7CC4FF)
                                    )
                                }
                            }
                        } else {
                            Text(
                                "– –",
                                fontSize = 58.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                style = TextStyle(shadow = mapShadow)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---- Herramientas: palo sugerido + medir golpe, en una fila ----
            if (vm.hasLocationPermission && vm.shotClubIdx >= 0) {
                ShotMeasureCard(vm)
            } else {
                val activeP = vm.players.getOrNull(vm.activePlayerIndex) ?: vm.players.first()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🏌️", fontSize = 22.sp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    str.suggestedClub,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    clubYards?.let { clubForDistance(it, activeP.clubYards) } ?: str.waitingGps,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    if (vm.hasLocationPermission) {
                        Button(
                            enabled = vm.userLat != null,
                            onClick = {
                                val idx = if (clubYards != null)
                                    clubIndexForDistance(clubYards, activeP.clubYards) else 0
                                vm.markShot(idx)
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxHeight()
                        ) { Text(str.measureBtn, maxLines = 1) }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---- Barra de anotación del jugador activo ----
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    if (vm.players.size > 1) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            vm.players.forEachIndexed { i, p ->
                                MiniChip(p.name.take(8), i == vm.activePlayerIndex) {
                                    vm.setActivePlayer(i)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    val p = vm.players.getOrNull(vm.activePlayerIndex) ?: vm.players.first()
                    val strokes = p.strokes[vm.currentHoleIndex]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { vm.removeStroke(vm.activePlayerIndex) },
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(42.dp)
                        ) { Text("−", fontSize = 20.sp) }
                        Column(
                            Modifier.width(64.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                strokes.toString(),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (strokes > 0) {
                                Text(
                                    scoreName(strokes - hole.par, str),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.addStroke(vm.activePlayerIndex)
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(42.dp)
                        ) { Text("+", fontSize = 20.sp) }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "PUTTS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "−",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { vm.removePutt(vm.activePlayerIndex) }
                                .padding(horizontal = 9.dp, vertical = 2.dp)
                        )
                        Text(
                            p.putts[vm.currentHoleIndex].toString(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.width(22.dp),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "+",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { vm.addPutt(vm.activePlayerIndex) }
                                .padding(horizontal = 9.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // ---- Expandible: detalle de todos los jugadores ----
            TextButton(
                onClick = { showAllPlayers = !showAllPlayers },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (showAllPlayers) str.hidePlayers else str.allPlayers,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showAllPlayers) {
            itemsIndexed(vm.players) { i, player ->
                StrokeRow(
                    name = player.name,
                    strokes = player.strokes[vm.currentHoleIndex],
                    par = hole.par,
                    onAdd = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.addStroke(i)
                    },
                    onRemove = { vm.removeStroke(i) },
                    putts = player.putts[vm.currentHoleIndex],
                    onPuttAdd = { vm.addPutt(i) },
                    onPuttRemove = { vm.removePutt(i) },
                    fir = player.fir[vm.currentHoleIndex],
                    onFirCycle = { vm.cycleFir(i) },
                    showFir = hole.par >= 4
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

/** Fila B/F del overlay de distancias sobre el mapa. */
@Composable
private fun OverlayFcb(letter: String, value: Int, shadow: Shadow) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            letter,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFFC9F5DC),
            style = TextStyle(shadow = shadow),
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            value.toString(),
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            style = TextStyle(shadow = shadow)
        )
    }
}

/**
 * Golpe en curso (medición 📏): distancia desde la marca, palo corregible
 * con ‹ › y guardar/cancelar. Solo se muestra mientras hay un golpe marcado;
 * el botón "Medir" de la fila de herramientas es quien lo inicia.
 */
@Composable
private fun ShotMeasureCard(vm: GolfViewModel) {
    val str = LocalStrings.current
    val yards = vm.units == Units.YARDS
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            val distM = vm.shotDistanceM() ?: 0.0
            val shown = if (yards) metersToYards(distM).roundToInt() else distM.roundToInt()
            val measuredYd = metersToYards(distM).roundToInt()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        str.shotInProgress,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "‹",
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { vm.changeShotClub(-1) }
                                .padding(horizontal = 8.dp)
                        )
                        Text(
                            clubNames.getOrElse(vm.shotClubIdx) { "?" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "›",
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { vm.changeShotClub(1) }
                                .padding(horizontal = 8.dp)
                        )
                    }
                    Text(
                        "$shown ${if (yards) "yd" else "m"} ${str.fromMark}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        str.walkAndSave,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Button(
                        enabled = measuredYd in 30..350,
                        onClick = { vm.saveShotToClub() },
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(str.save, maxLines = 1) }
                    TextButton(onClick = { vm.cancelShot() }) {
                        Text(str.cancel, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            }
        }
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
private fun StatBadge(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        Text(value, fontWeight = FontWeight.Black, fontSize = 14.sp)
    }
}

/** Botón compacto del pin del día: círculo con la bandera del color elegido.
 *  Toca para ciclar: sin pin → frente (rojo) → medio (blanco) → fondo (azul). */
@Composable
private fun FlagChip(flag: Int, onClick: () -> Unit) {
    val color = when (flag) {
        0 -> Color(0xFFE85D4A)   // frente
        1 -> Color(0xFFF4F1E8)   // medio
        2 -> Color(0xFF5AB0FF)   // fondo
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("⚑", fontSize = 17.sp, color = color)
        }
    }
}

@Composable
private fun Pill(text: String, bg: Color, fg: Color) {
    Surface(shape = RoundedCornerShape(50), color = bg) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            color = fg
        )
    }
}

@Composable
private fun StrokeRow(
    name: String, strokes: Int, par: Int,
    onAdd: () -> Unit, onRemove: () -> Unit,
    putts: Int = 0, onPuttAdd: () -> Unit = {}, onPuttRemove: () -> Unit = {},
    fir: Int = -1, onFirCycle: () -> Unit = {}, showFir: Boolean = false
) {
    val str = LocalStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.SemiBold)
                    if (strokes > 0) {
                        val diff = strokes - par
                        Text(
                            scoreName(diff, str),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Button(
                    onClick = onAdd,
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(44.dp)
                ) { Text("+", fontSize = 22.sp) }
            }
            // Fila de stats: putts + fairway (fairway solo en par 4/5)
            Row(
                Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PUTTS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "−",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onPuttRemove)
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                )
                Text(
                    putts.toString(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.width(24.dp),
                    textAlign = TextAlign.Center
                )
                Text(
                    "+",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onPuttAdd)
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                )
                if (showFir) {
                    Spacer(Modifier.weight(1f))
                    val (label, bg, fg) = when (fir) {
                        1 -> Triple("FAIRWAY ✓", MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.onPrimaryContainer)
                        0 -> Triple("FAIRWAY ✗", MaterialTheme.colorScheme.errorContainer,
                                    MaterialTheme.colorScheme.onErrorContainer)
                        else -> Triple("FAIRWAY —", MaterialTheme.colorScheme.surfaceVariant,
                                       MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = bg,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onFirCycle)
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = fg
                        )
                    }
                }
            }
        }
    }
}

private fun scoreName(diff: Int, str: AppStrings): String = when {
    diff <= -3 -> "Albatross 🦅"
    diff == -2 -> "Eagle 🦅"
    diff == -1 -> "Birdie 🐦"
    diff == 0 -> "Par"
    diff == 1 -> "Bogey"
    diff == 2 -> str.doubleBogey
    else -> "+$diff"
}


// ---------------------------------------------------------------- Scorecard

@Composable
private fun ScorecardScreen(vm: GolfViewModel) {
    val str = LocalStrings.current
    var showFinishDialog by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("MMM d · h:mm a", Locale.US) }
    val context = LocalContext.current
    val anyScores = vm.players.any { it.playedHoles() > 0 }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Text(str.cardTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
                str.legend,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))

            // ---- Stats de la ronda actual ----
            if (vm.players.any { it.playedHoles() > 0 }) {
                Text(str.roundStats, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        vm.players.filter { it.playedHoles() > 0 }.forEach { p ->
                            val (fh, fa) = p.firStats()
                            val girT = p.girTracked()
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    p.name.take(10),
                                    Modifier.weight(1f),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                StatBadge("FIR", if (fa > 0) "$fh/$fa" else "—")
                                Spacer(Modifier.width(10.dp))
                                StatBadge("GIR", if (girT > 0) "${p.girCount()}/$girT" else "—")
                                Spacer(Modifier.width(10.dp))
                                StatBadge("PUTTS", if (p.totalPutts() > 0) "${p.totalPutts()}" else "—")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // ---- Juegos: Skins + Match Play ----
            if (vm.players.size >= 2) {
                Text(str.games, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        val (skins, pot) = vm.skinsStandings()
                        Text(
                            "SKINS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (skins.all { it == 0 } && pot == 0) {
                            Text(
                                str.skinsEmpty,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val leaderSkins = skins.max()
                            vm.players.forEachIndexed { i, p ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        (if (skins[i] == leaderSkins && leaderSkins > 0) "👑 " else "") + p.name.take(12),
                                        Modifier.weight(1f),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "${skins[i]} skin${if (skins[i] == 1) "" else "s"}",
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (pot > 0) {
                                Text(
                                    str.carriedSkins(pot),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        vm.matchPlayStatus(vm.language == AppLanguage.EN)?.let { status ->
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "MATCH PLAY",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                status,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // ---- Stableford con handicap: puntos netos por hoyo ----
                        if (vm.players.any { it.playedHoles() > 0 }) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                str.stablefordTitle,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val pts = vm.players.map { it.stablefordPoints() }
                            val best = pts.max()
                            vm.players.forEachIndexed { i, p ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        (if (pts[i] == best && best > 0) "👑 " else "") +
                                            p.name.take(12) + "  · hcp ${p.hcp}",
                                        Modifier.weight(1f),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "${pts[i]} pts",
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (vm.players.all { it.hcp == 0 }) {
                                Text(
                                    str.stablefordHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            Button(
                onClick = { showFinishDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) { Text(str.finishBtn) }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    ScorecardImage.shareIntent(context, vm)?.let {
                        context.startActivity(Intent.createChooser(it, str.shareChooser))
                    }
                },
                enabled = anyScores,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) { Text(str.shareBtn) }

            Spacer(Modifier.height(22.dp))
            Text(str.historyTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (vm.history.isEmpty()) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        str.historyEmpty,
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
            title = { Text(str.finishDialogTitle) },
            text = { Text(str.finishDialogText) },
            confirmButton = {
                TextButton(onClick = {
                    vm.finishRound()
                    showFinishDialog = false
                }) { Text(str.saveReset) }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) { Text(str.cancel) }
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
                    color = if (p.playedHoles() > 0) MaterialTheme.colorScheme.onSurface
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
    val str = LocalStrings.current
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
                        "${e.name}: ${e.strokes} ${str.strokesWord} ($relLabel · ${e.holes}/18 ${str.holesWord})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val bits = buildList {
                        if (e.firAtt > 0) add("FIR ${e.firHit}/${e.firAtt}")
                        if (e.girTracked > 0) add("GIR ${e.gir}/${e.girTracked}")
                        if (e.putts > 0) add("${e.putts} putts")
                        if (e.points > 0) add("${e.points} pts" + if (e.hcp > 0) " (hcp ${e.hcp})" else "")
                    }
                    if (bits.isNotEmpty()) {
                        Text(
                            "    " + bits.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            TextButton(onClick = onDelete) {
                Text(str.delete, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun HeaderRow(vm: GolfViewModel) {
    val str = LocalStrings.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp)) {
        Text(str.holeCol, Modifier.width(46.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
    // Aro/cuadro neutros: tinta en tema claro, blanco en oscuro (igual que el número).
    val frame = if (diff == 0) Color.Transparent else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.size(27.dp).border(1.6.dp, frame, shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            strokes.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TotalsRow(vm: GolfViewModel, label: String, range: IntRange, bold: Boolean = false) {
    val parSum = range.sumOf { CourseData.holes[it].par }
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(label, Modifier.width(52.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp,
            maxLines = 1)
        Text(parSum.toString(), Modifier.width(30.dp), textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                color = if (p.playedHoles() > 0) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------- Stats

/** Nombres con historial o en la ronda actual, sin duplicar y en orden. */
private fun statNames(vm: GolfViewModel): List<String> {
    val names = LinkedHashSet<String>()
    vm.players.forEach { names.add(it.name) }
    vm.history.forEach { r -> r.entries.forEach { names.add(it.name) } }
    return names.toList()
}

@Composable
private fun StatsScreen(vm: GolfViewModel) {
    val str = LocalStrings.current
    val names = statNames(vm)
    var selected by rememberSaveable {
        mutableStateOf(vm.players.getOrNull(vm.activePlayerIndex)?.name ?: "")
    }
    if (selected !in names && names.isNotEmpty()) selected = names.first()

    // Rondas del jugador (más nueva primero); "full" = rondas completas de 18 hoyos.
    val entries = vm.history.mapNotNull { r ->
        r.entries.firstOrNull { it.name == selected }
    }
    val full = entries.filter { it.holes == 18 }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Text("📊 Stats", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                str.statsSub,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            if (names.size > 1) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    names.forEach { n ->
                        MiniChip(n.take(10), n == selected) { selected = n }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (entries.isEmpty()) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        str.statsEmpty,
                        Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(24.dp))
                return@item
            }

            // ---- Resumen ----
            val scores = full.map { it.strokes }
            val puttRounds = full.filter { it.putts > 0 }
            val girHit = entries.sumOf { it.gir }
            val girAtt = entries.sumOf { it.girTracked }
            val firHit = entries.sumOf { it.firHit }
            val firAtt = entries.sumOf { it.firAtt }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(str.tileRounds, "${entries.size}", Modifier.weight(1f))
                StatTile(str.tileAverage, if (scores.isNotEmpty()) "%.1f".format(scores.average()) else "—", Modifier.weight(1f))
                StatTile(str.tileBest, if (scores.isNotEmpty()) "${scores.min()}" else "—", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    str.tilePuttsRound,
                    if (puttRounds.isNotEmpty()) "%.1f".format(puttRounds.map { it.putts }.average()) else "—",
                    Modifier.weight(1f)
                )
                StatTile("GIR", if (girAtt > 0) "${(girHit * 100.0 / girAtt).roundToInt()}%" else "—", Modifier.weight(1f))
                StatTile("FIR", if (firAtt > 0) "${(firHit * 100.0 / firAtt).roundToInt()}%" else "—", Modifier.weight(1f))
            }
            if (scores.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    str.fullRoundsNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(14.dp))

            // ---- Handicap index (WHS) ----
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "HANDICAP INDEX (WHS)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val idx = vm.handicapIndex(selected)
                    if (idx != null) {
                        Text(
                            "%.1f".format(idx),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
str.hcpDetail(full.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            str.hcpNeed(full.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            // ---- Tendencia: últimas rondas completas (izquierda = más vieja) ----
            val trend = full.take(10).reversed()
            if (trend.size >= 2) {
                Text(str.trendTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    str.trendSub(trend.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    val minS = trend.minOf { it.strokes }
                    val maxS = trend.maxOf { it.strokes }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        trend.forEach { e ->
                            val frac = if (maxS == minS) 0.5f
                                       else (e.strokes - minS).toFloat() / (maxS - minS)
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "${e.strokes}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (e.strokes == minS) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height((30 + 42 * frac).dp)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(
                                            if (e.strokes == minS) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                        )
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // ---- Promedio por hoyo: dónde pierdes golpes ----
            val holed = full.filter { it.holeStrokes.size == 18 }
            Text(str.perHoleTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (holed.isEmpty()) {
                Text(
                    str.perHoleEmpty,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
            } else {
                Text(
                    str.worstNote(holed.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                val avgs = (0 until 18).map { h ->
                    val vals = holed.mapNotNull { e -> e.holeStrokes[h].takeIf { it > 0 } }
                    if (vals.isEmpty()) null else vals.average()
                }
                val worst = avgs.mapIndexedNotNull { h, a ->
                    a?.let { h to it - CourseData.holes[h].par }
                }.filter { it.second > 0 }.sortedByDescending { it.second }.take(3).map { it.first }.toSet()
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        CourseData.holes.forEachIndexed { h, hole ->
                            val avg = avgs[h]
                            val diff = avg?.minus(hole.par)
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "H${hole.number}" + if (h in worst) " 🔥" else "",
                                    Modifier.width(64.dp),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "Par ${hole.par}",
                                    Modifier.width(52.dp),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    avg?.let { "%.1f".format(it) } ?: "—",
                                    Modifier.weight(1f),
                                    textAlign = TextAlign.End,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    diff?.let { if (it >= 0) "+%.1f".format(it) else "%.1f".format(it) } ?: "",
                                    Modifier.width(58.dp),
                                    textAlign = TextAlign.End,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = when {
                                        diff == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                        diff <= 0.0 -> MaterialTheme.colorScheme.primary
                                        diff < 0.5 -> MaterialTheme.colorScheme.onSurfaceVariant
                                        else -> MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(value, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- Players

@Composable
private fun PlayersScreen(vm: GolfViewModel) {
    val str = LocalStrings.current
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text(str.playersTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                str.playersSub,
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
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                name = it.take(14)
                                vm.renamePlayer(i, name)
                            },
                            label = { Text(str.playerLabel(i + 1)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        if (vm.players.size > 1) {
                            TextButton(onClick = { vm.removePlayer(i) }) {
                                Text(str.remove, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    // Handicap de juego: reparte golpes de ventaja en Stableford.
                    Row(
                        Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "HANDICAP",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(10.dp))
                        OutlinedButton(
                            onClick = { vm.adjustHandicap(i, -1) },
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(34.dp)
                        ) { Text("−") }
                        Text(
                            "${player.hcp}",
                            Modifier.width(44.dp),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
                        )
                        OutlinedButton(
                            onClick = { vm.adjustHandicap(i, 1) },
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(34.dp)
                        ) { Text("+") }
                        Spacer(Modifier.weight(1f))
                        vm.handicapIndex(player.name)?.let { idx ->
                            Text(
                                str.indexLabel(idx),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
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
                ) { Text(str.addPlayer) }
            } else {
                Text(
                    str.maxPlayers,
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
    val str = LocalStrings.current
    var showResetDialog by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text(str.settingsTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))

            Text(str.languageTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceButton("Español", vm.language == AppLanguage.ES) { vm.setLanguageAndSave(AppLanguage.ES) }
                ChoiceButton("English", vm.language == AppLanguage.EN) { vm.setLanguageAndSave(AppLanguage.EN) }
            }

            Spacer(Modifier.height(22.dp))
            Text(str.unitsTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceButton(str.yards, vm.units == Units.YARDS) { vm.setUnitsAndSave(Units.YARDS) }
                ChoiceButton(str.meters, vm.units == Units.METERS) { vm.setUnitsAndSave(Units.METERS) }
            }

            Spacer(Modifier.height(22.dp))
            Text(str.themeTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceButton(str.themeSystem, vm.themeMode == ThemeMode.SYSTEM) { vm.setThemeAndSave(ThemeMode.SYSTEM) }
                ChoiceButton(str.themeLight, vm.themeMode == ThemeMode.LIGHT) { vm.setThemeAndSave(ThemeMode.LIGHT) }
                ChoiceButton(str.themeDark, vm.themeMode == ThemeMode.DARK) { vm.setThemeAndSave(ThemeMode.DARK) }
            }

            Spacer(Modifier.height(22.dp))
            Text(str.elevTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                str.elevDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(
                    str.calibrated(vm.calibratedGreens),
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(10.dp))
                if (vm.calibratedGreens > 0) {
                    Text(
                        str.resetSmall,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable { vm.resetElevations() }
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(str.clubsTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                str.clubsDesc,
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
                        vm.setActivePlayer(i)
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
                        Text(str.resetDefaults)
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            AccountSection(vm)

            Spacer(Modifier.height(22.dp))
            Text(str.roundTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { showResetDialog = true },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) { Text(str.clearStrokes) }

            Spacer(Modifier.height(28.dp))
            Divider()
            Spacer(Modifier.height(14.dp))
            Text(
                "⛳ ${CourseData.CLUB_NAME}",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
str.aboutText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(str.clearDialogTitle) },
            text = { Text(str.clearDialogText) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearStrokes()
                    showResetDialog = false
                }) { Text(str.yesClear, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(str.cancel) }
            }
        )
    }
}

/**
 * Cuenta opcional + respaldo en la nube. Solo aparece si el proyecto tiene
 * Firebase configurado (google-services.json); si no, la app es 100% local.
 */
@Composable
private fun AccountSection(vm: GolfViewModel) {
    val context = LocalContext.current
    if (!Cloud.isConfigured(context)) return

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var signedEmail by remember { mutableStateOf(Cloud.currentEmail()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Text("Cuenta y respaldo (opcional)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(
        "Con una cuenta, tus jugadores, palos e historial de rondas se respaldan " +
        "en la nube y se restauran si cambias de teléfono. Sin cuenta, todo sigue " +
        "funcionando igual, guardado en este dispositivo.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp)) {
            val current = signedEmail
            if (current == null) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy && email.isNotBlank() && password.length >= 6,
                        onClick = {
                            busy = true; status = null
                            Cloud.signIn(email, password) { err ->
                                busy = false
                                if (err == null) { signedEmail = Cloud.currentEmail(); status = "Sesión iniciada" }
                                else status = err
                            }
                        }
                    ) { Text("Iniciar sesión") }
                    OutlinedButton(
                        enabled = !busy && email.isNotBlank() && password.length >= 6,
                        onClick = {
                            busy = true; status = null
                            Cloud.signUp(email, password) { err ->
                                busy = false
                                if (err == null) { signedEmail = Cloud.currentEmail(); status = "Cuenta creada" }
                                else status = err
                            }
                        }
                    ) { Text("Crear cuenta") }
                }
                TextButton(
                    enabled = !busy && email.isNotBlank(),
                    onClick = {
                        busy = true; status = null
                        Cloud.sendPasswordReset(email) { err ->
                            busy = false
                            status = err ?: "Te enviamos un correo para restablecerla"
                        }
                    }
                ) { Text("¿Olvidaste tu contraseña?") }
                if (password.isNotEmpty() && password.length < 6) {
                    Text(
                        "La contraseña debe tener al menos 6 caracteres",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text("Sesión: $current", fontWeight = FontWeight.SemiBold)
                Text(
                    "El respaldo se sube solo al cerrar cada ronda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy,
                        onClick = {
                            busy = true; status = null
                            Cloud.backup(vm.cloudBackupData()) { err ->
                                busy = false
                                status = err ?: "Respaldo guardado ✓"
                            }
                        }
                    ) { Text("Respaldar ahora") }
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            busy = true; status = null
                            Cloud.restore { data, err ->
                                busy = false
                                if (data != null) { vm.applyCloudData(data); status = "Datos restaurados ✓" }
                                else status = err
                            }
                        }
                    ) { Text("Restaurar") }
                }
                TextButton(onClick = {
                    Cloud.signOut()
                    signedEmail = null
                    status = "Sesión cerrada (tus datos locales siguen aquí)"
                }) { Text("Cerrar sesión") }
            }
            status?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
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
