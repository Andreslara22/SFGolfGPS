package mx.clubsanfrancisco.golfgps

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ---- Marca Yunari Studio (branding del estudio que publica la app) ----
// Paleta propia del estudio, distinta al verde del campo, para que la marca
// se distinga. Fondo casi negro con acento en degradado violeta → cian.
private val YunariBg = Color(0xFF0B0B14)
private val YunariBadge = Color(0xFF15162A)
private val YunariViolet = Color(0xFF8B5CF6)
private val YunariCyan = Color(0xFF22D3EE)
private val YunariInk = Color(0xFFF2F3F7)
private val YunariMuted = Color(0xFF8A8CA3)

/**
 * Pantalla de inicio de marca: muestra "Yunari Studio" un par de segundos al
 * abrir la app y luego llama a [onDone]. Se puede tocar para saltarla.
 */
@Composable
fun YunariSplash(onDone: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(700), label = "alpha")
    val scale by animateFloatAsState(if (shown) 1f else 0.82f, tween(700), label = "scale")

    LaunchedEffect(Unit) {
        shown = true
        delay(2200)
        onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(YunariBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDone() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(alpha)
                .scale(scale)
        ) {
            YunariLogo(Modifier.size(112.dp))
            Spacer(Modifier.height(22.dp))
            Text(
                "YUNARI",
                color = YunariInk,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 8.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "S T U D I O",
                color = YunariMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 6.sp
            )
        }

        Text(
            "Diseño y desarrollo de apps",
            color = YunariMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .alpha(alpha)
        )
    }
}

/** Logotipo de Yunari: monograma "Y" geométrico en degradado dentro de un
 *  badge redondeado. Dibujado como vector (nítido a cualquier tamaño). */
@Composable
fun YunariLogo(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val grad = Brush.linearGradient(
            colors = listOf(YunariViolet, YunariCyan),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )
        val corner = CornerRadius(w * 0.30f, w * 0.30f)
        // Badge de fondo + borde en degradado.
        drawRoundRect(color = YunariBadge, cornerRadius = corner)
        drawRoundRect(
            brush = grad,
            cornerRadius = corner,
            style = Stroke(width = w * 0.035f)
        )
        // Monograma "Y".
        val sw = w * 0.095f
        val topL = Offset(w * 0.30f, h * 0.28f)
        val topR = Offset(w * 0.70f, h * 0.28f)
        val mid = Offset(w * 0.50f, h * 0.55f)
        val bot = Offset(w * 0.50f, h * 0.74f)
        drawLine(grad, topL, mid, strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(grad, topR, mid, strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(grad, mid, bot, strokeWidth = sw, cap = StrokeCap.Round)
    }
}
