package mx.clubsanfrancisco.golfgps

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Campo numérico entero (teclado de números). [value] manda: si cambia desde
 * fuera (botones +/−) el texto se actualiza; vacío mientras se escribe no
 * dispara [onValue].
 */
@Composable
internal fun NumberField(
    value: Int,
    onValue: (Int) -> Unit,
    width: Dp,
    label: String? = null,
    maxDigits: Int = 3
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            val digits = new.filter { it.isDigit() }.take(maxDigits)
            text = digits
            digits.toIntOrNull()?.let(onValue)
        },
        label = label?.let { l -> @Composable { Text(l, fontSize = 11.sp) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TextStyle(fontWeight = FontWeight.Black, fontSize = 16.sp, textAlign = TextAlign.Center),
        modifier = Modifier.width(width)
    )
}

/** Monto sin decimales si es entero, con 2 si no (empates que no dividen exacto). */
internal fun money(v: Double): String {
    val cents = (v * 100).roundToLong()
    return if (cents % 100 == 0L) "$${cents / 100}" else "$" + String.format("%.2f", cents / 100.0)
}

private val WinColor = Color(0xFF2E9E5B)
private val LoseColor = Color(0xFFD9534F)

/** Apuesta por puntos: monto por ronda, reparto 1º/2º/3º, ganadores y neto. */
@Composable
internal fun BetsCard(vm: GolfViewModel) {
    val en = vm.language == AppLanguage.EN
    val segNames = if (en) listOf("Front 9", "Back 9", "Overall") else listOf("Front 9", "Back 9", "General")
    val n = vm.players.size

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                if (en) "💰 POINTS BET" else "💰 APUESTA POR PUNTOS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))

            // ---- Monto por jugador por ronda ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (en) "Each player bets per round  $" else "Cada quien apuesta por ronda  $",
                    Modifier.weight(1f),
                    fontSize = 14.sp
                )
                NumberField(vm.betAmount, { vm.updateBetAmount(it) }, 96.dp, maxDigits = 6)
            }
            if (vm.betAmount == 0) {
                Text(
                    if (en) "Set an amount to start the bet (e.g. 100 per round = 300 total: front, back and overall)."
                    else "Pon un monto para activar la apuesta (ej. 100 por ronda = 300 en total: front, back y general).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            Text(
                (if (en) "Total per player: " else "Total por jugador: ") + money(3.0 * vm.betAmount) +
                    (if (en) " · Pot per round: " else " · Bolsa por ronda: ") + money(1.0 * vm.betAmount * n),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ---- Reparto 1º / 2º / 3º ----
            Spacer(Modifier.height(10.dp))
            Text(
                if (en) "PAYOUT PER ROUND" else "REPARTO POR RONDA",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    Triple(100, 0, 0) to (if (en) "Winner takes all" else "Todo al 1º"),
                    Triple(70, 30, 0) to "70/30",
                    Triple(60, 30, 10) to "60/30/10",
                    Triple(50, 30, 20) to "50/30/20"
                ).forEach { (p, label) ->
                    val selected = vm.betPayout[0] == p.first && vm.betPayout[1] == p.second && vm.betPayout[2] == p.third
                    SmallToggle(label, selected) { vm.setBetPayoutPreset(p.first, p.second, p.third) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("1º %", "2º %", "3º %").forEachIndexed { place, label ->
                    NumberField(vm.betPayout[place], { vm.setBetPayout(place, it) }, 78.dp, label = label)
                }
            }
            val pctSum = vm.betPayout.sum()
            if (pctSum != 100) {
                Text(
                    if (en) "Percentages add up to $pctSum% — they are rescaled so the whole pot is paid."
                    else "Los % suman $pctSum% — se reescalan para repartir la bolsa completa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LoseColor
                )
            }

            // ---- Puntos y premios por ronda ----
            val results = Bets.all(vm)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(if (en) "PLAYER" else "JUGADOR", Modifier.weight(1.4f), fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf("FRONT", "BACK", if (en) "OVERALL" else "GENERAL").forEach {
                    Text(it, Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Divider(Modifier.padding(vertical = 4.dp))
            vm.players.forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1.4f)) {
                        Text(p.name.take(12), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("hcp ${p.hcp}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    results.forEach { r ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                (if (r.places[i] == 1 && r.points[i] > 0) "👑" else "") + "${r.points[i]}",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (r.prizes[i] > 0) {
                                Text(money(r.prizes[i]), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WinColor)
                            }
                        }
                    }
                }
            }

            // ---- Ganador de cada ronda: automático o elegido ----
            Spacer(Modifier.height(10.dp))
            Text(
                if (en) "WHO GETS EACH ROUND'S PRIZE" else "¿QUIÉN SE LLEVA EL PREMIO DE CADA RONDA?",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            results.forEachIndexed { seg, r ->
                Text(
                    "${segNames[seg]} · " + (if (en) "pot " else "bolsa ") + money(r.pot.toDouble()),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SmallToggle(if (en) "Auto (points)" else "Auto (puntos)", vm.betWinners[seg] == -1) {
                        vm.setBetWinner(seg, -1)
                    }
                    vm.players.forEachIndexed { i, p ->
                        SmallToggle(p.name.take(10), vm.betWinners[seg] == i) { vm.setBetWinner(seg, i) }
                    }
                }
            }

            // ---- Neto por jugador ----
            val net = Bets.net(vm, results)
            Spacer(Modifier.height(10.dp))
            Divider()
            Text(
                if (en) "NET (winnings − bet of ${money(3.0 * vm.betAmount)})"
                else "NETO (lo que cobra − su apuesta de ${money(3.0 * vm.betAmount)})",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            vm.players.forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(p.name.take(14), Modifier.weight(1f), fontSize = 14.sp)
                    val v = net[i]
                    Text(
                        when {
                            abs(v) < 0.005 -> "$0"
                            v > 0 -> "+" + money(v)
                            else -> "−" + money(-v)
                        },
                        fontWeight = FontWeight.Black,
                        color = when {
                            abs(v) < 0.005 -> MaterialTheme.colorScheme.onSurfaceVariant
                            v > 0 -> WinColor
                            else -> LoseColor
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    val pad = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    if (selected) {
        Button(onClick = onClick, shape = RoundedCornerShape(50), contentPadding = pad,
            modifier = Modifier.height(32.dp)) { Text(label, fontSize = 12.sp) }
    } else {
        OutlinedButton(onClick = onClick, shape = RoundedCornerShape(50), contentPadding = pad,
            modifier = Modifier.height(32.dp)) { Text(label, fontSize = 12.sp) }
    }
}
