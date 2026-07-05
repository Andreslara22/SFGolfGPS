package mx.clubsanfrancisco.golfgps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ---- Paleta caricatura (fija en ambos temas: es terreno) ----
private val Rough = Color(0xFF14382A)
private val RoughDots = Color(0xFF1B4634)
private val Fairway = Color(0xFF3E8E5E)
private val FairwayStripe = Color(0xFF4BA06C)
private val GreenFringe = Color(0xFF57B87B)
private val GreenTurf = Color(0xFF7FD69E)
private val Sand = Color(0xFFEAD08F)
private val SandShadow = Color(0xFFC9A75E)
private val WaterDeep = Color(0xFF2C5F94)
private val WaterBlue = Color(0xFF3D7FC1)
private val Ripple = Color(0xFF7FB2E3)
private val TreeDark = Color(0xFF1F5C3C)
private val TreeLight = Color(0xFF2E7A50)
private val Trunk = Color(0xFF6B4B2A)
private val FlagRed = Color(0xFFE85D4A)
private val Pole = Color(0xFFF4F1E8)
private val PlayerBlue = Color(0xFF5AB0FF)

@Composable
fun HoleMapCard(hole: Hole, userLat: Double?, userLng: Double?, units: Units) {
    val density = LocalDensity.current
    val labelPx = with(density) { 13.dp.toPx() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Rough)
    ) {
        Canvas(Modifier.fillMaxWidth().height(280.dp)) {
            drawHoleMap(hole, userLat, userLng, units, labelPx)
        }
    }
}

private fun DrawScope.drawHoleMap(
    hole: Hole,
    userLat: Double?,
    userLng: Double?,
    units: Units,
    labelPx: Float
) {
    val w = size.width
    val h = size.height
    val f = holeFeatures[hole.number] ?: HoleFeatures(0f, emptyList())

    // ---- Proyección lat/lng -> metros locales -> rotación (green arriba) ----
    val lat0 = (hole.teeLat + hole.greenLat) / 2
    val lng0 = (hole.teeLng + hole.greenLng) / 2
    fun local(lat: Double, lng: Double): Offset {
        val x = ((lng - lng0) * cos(Math.toRadians(lat0)) * 111320.0).toFloat()
        val y = (-(lat - lat0) * 110540.0).toFloat()
        return Offset(x, y)
    }
    val teeL = local(hole.teeLat, hole.teeLng)
    val greenL = local(hole.greenLat, hole.greenLng)
    val angle = atan2((greenL.y - teeL.y), (greenL.x - teeL.x))
    val rot = (-PI / 2 - angle).toFloat()
    fun rotate(p: Offset): Offset {
        val c = cos(rot); val s = sin(rot)
        return Offset(p.x * c - p.y * s, p.x * s + p.y * c)
    }
    val lengthM = (rotate(teeL).y - rotate(greenL).y)
    val greenY = h * 0.19f
    val teeY = h * 0.87f
    val scale = (teeY - greenY) / lengthM
    val cx = w / 2f
    val cy = (teeY + greenY) / 2f
    fun toScreen(p: Offset): Offset {
        val r = rotate(p)
        return Offset(cx + r.x * scale, cy + r.y * scale)
    }

    val teeP = Offset(cx, teeY)
    val greenP = Offset(cx, greenY)
    val gr = min(w, h) * 0.125f

    // ---- Fairway curvo (dogleg) como Bézier cuadrática ----
    val p0 = teeP
    val p2 = Offset(greenP.x, greenP.y + gr * 1.4f)
    val p1 = Offset(cx + f.dogleg * w * 0.38f, (teeY + greenY) / 2f)
    fun bez(t: Float): Offset {
        val u = 1 - t
        return Offset(
            u * u * p0.x + 2 * u * t * p1.x + t * t * p2.x,
            u * u * p0.y + 2 * u * t * p1.y + t * t * p2.y
        )
    }
    fun perp(t: Float): Offset {
        val tx = 2 * (1 - t) * (p1.x - p0.x) + 2 * t * (p2.x - p1.x)
        val ty = 2 * (1 - t) * (p1.y - p0.y) + 2 * t * (p2.y - p1.y)
        val len = sqrt(tx * tx + ty * ty).coerceAtLeast(0.001f)
        return Offset(-ty / len, tx / len) // +1 = derecha del jugador
    }

    // ---- Rough: pasto punteado + número de hoyo de fondo ----
    val rnd = Random(hole.number * 97)
    repeat(54) {
        val p = Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)
        drawCircle(RoughDots, radius = 2f + rnd.nextFloat() * 2.5f, center = p)
    }
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(26, 255, 255, 255)
            textSize = h * 0.42f
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.RIGHT
            isAntiAlias = true
        }
        canvas.nativeCanvas.drawText("${hole.number}", w - 14f, h * 0.42f, paint)
    }

    // ---- Fairway base + franjas de podado ----
    val fairwayW = if (hole.par == 3) w * 0.20f else w * 0.30f
    val samples = 28
    val basePath = Path().apply {
        moveTo(p0.x, p0.y)
        for (i in 1..samples) { val q = bez(i / samples.toFloat()); lineTo(q.x, q.y) }
    }
    drawPath(basePath, Fairway, style = Stroke(width = fairwayW, cap = StrokeCap.Round))
    val bands = 10
    for (k in 0 until bands step 2) {
        val seg = Path().apply {
            val t0 = k / bands.toFloat(); val t1 = (k + 1) / bands.toFloat()
            val q0 = bez(t0); moveTo(q0.x, q0.y)
            for (i in 1..4) { val q = bez(t0 + (t1 - t0) * i / 4f); lineTo(q.x, q.y) }
        }
        drawPath(seg, FairwayStripe, style = Stroke(width = fairwayW * 0.9f, cap = StrokeCap.Butt))
    }

    // ---- Agua (si el hoyo tiene) ----
    f.water?.let { wa ->
        val c = bez(wa.t) + perp(wa.t) * wa.side * (fairwayW * 0.5f + w * 0.10f * wa.w)
        val pw = w * 0.15f * wa.w
        val ph = w * 0.105f * wa.h
        drawOval(WaterDeep, topLeft = Offset(c.x - pw, c.y - ph + 5f), size = Size(pw * 2, ph * 2))
        drawOval(WaterBlue, topLeft = Offset(c.x - pw, c.y - ph), size = Size(pw * 2, ph * 2))
        // ondas
        for (i in 0..1) {
            drawArc(
                Ripple, startAngle = 200f, sweepAngle = 120f, useCenter = false,
                topLeft = Offset(c.x - pw * (0.45f + i * 0.25f), c.y - ph * (0.35f + i * 0.25f)),
                size = Size(pw * (0.9f + i * 0.5f), ph * (0.7f + i * 0.5f)),
                style = Stroke(width = 2.5f, cap = StrokeCap.Round)
            )
        }
    }

    // ---- Bunkers: blobs de arena irregulares ----
    f.bunkers.forEach { b ->
        val c = bez(b.t) + perp(b.t) * b.side * (fairwayW * 0.5f + w * 0.045f * b.size)
        val br = w * 0.052f * b.size
        drawOval(SandShadow, topLeft = Offset(c.x - br, c.y - br * 0.55f + 4f), size = Size(br * 2f, br * 1.1f))
        drawOval(Sand, topLeft = Offset(c.x - br, c.y - br * 0.55f), size = Size(br * 2f, br * 1.1f))
        drawOval(Sand, topLeft = Offset(c.x - br * 0.5f, c.y - br * 0.85f), size = Size(br * 1.3f, br * 0.9f))
    }

    // ---- Árboles: racimos por hoyo ----
    val treeRnd = Random(hole.number * 31 + 7)
    repeat(f.trees) { i ->
        val t = 0.12f + treeRnd.nextFloat() * 0.72f
        val side = if ((i + hole.number) % 2 == 0) 1f else -1f
        val c = bez(t) + perp(t) * side * (fairwayW * 0.5f + w * (0.11f + treeRnd.nextFloat() * 0.08f))
        val r = w * (0.040f + treeRnd.nextFloat() * 0.017f)
        if (c.x - r > 4f && c.x + r < w - 4f && c.y - r > 4f && c.y + r < h - 4f) {
            drawLine(Trunk, Offset(c.x, c.y + r * 0.4f), Offset(c.x, c.y + r * 1.15f),
                strokeWidth = r * 0.35f, cap = StrokeCap.Round)
            drawCircle(TreeDark, radius = r, center = c)
            drawCircle(TreeDark, radius = r * 0.7f, center = Offset(c.x + r * 0.55f, c.y + r * 0.15f))
            drawCircle(TreeLight, radius = r * 0.55f, center = Offset(c.x - r * 0.3f, c.y - r * 0.3f))
        }
    }

    // ---- Green: sombra + fringe + pasto + bandera + hoyo ----
    drawCircle(Color(0x40000000), radius = gr * 1.18f, center = Offset(greenP.x + 3f, greenP.y + 5f))
    drawCircle(GreenFringe, radius = gr * 1.18f, center = greenP)
    drawCircle(GreenTurf, radius = gr, center = greenP)
    val poleTop = Offset(greenP.x, greenP.y - gr * 1.55f)
    drawLine(Pole, greenP, poleTop, strokeWidth = 4f, cap = StrokeCap.Round)
    val flagPath = Path().apply {
        moveTo(poleTop.x, poleTop.y)
        lineTo(poleTop.x + gr * 0.75f, poleTop.y + gr * 0.28f)
        lineTo(poleTop.x, poleTop.y + gr * 0.56f)
        close()
    }
    drawPath(flagPath, FlagRed)
    drawCircle(Color(0x33000000), radius = 5f, center = Offset(greenP.x, greenP.y + 2f))
    drawCircle(Pole, radius = 3.5f, center = greenP)

    // ---- Salida: caja de tee con dos marcas ----
    drawOval(Fairway, topLeft = Offset(teeP.x - 30f, teeP.y - 12f), size = Size(60f, 24f))
    drawCircle(Pole, radius = 6.5f, center = Offset(teeP.x - 14f, teeP.y))
    drawCircle(Pole, radius = 6.5f, center = Offset(teeP.x + 14f, teeP.y))
    drawCircle(FlagRed, radius = 3.2f, center = Offset(teeP.x - 14f, teeP.y))
    drawCircle(FlagRed, radius = 3.2f, center = Offset(teeP.x + 14f, teeP.y))

    // ---- Jugador + línea punteada + distancia en vivo ----
    if (userLat != null && userLng != null) {
        val raw = toScreen(local(userLat, userLng))
        val pad = 18f
        val user = Offset(raw.x.coerceIn(pad, w - pad), raw.y.coerceIn(pad, h - pad))
        val offMap = raw != user

        drawLine(
            Pole.copy(alpha = 0.85f), user, greenP,
            strokeWidth = 3.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))
        )
        drawCircle(PlayerBlue.copy(alpha = 0.28f), radius = 20f, center = user)
        drawCircle(Pole, radius = 10f, center = user)
        drawCircle(PlayerBlue, radius = 7f, center = user)

        val distM = haversineMeters(userLat, userLng, hole.greenLat, hole.greenLng)
        val label = if (units == Units.YARDS)
            "${metersToYards(distM).roundToInt()} yd" else "${distM.roundToInt()} m"
        val mid = Offset((user.x + greenP.x) / 2f, (user.y + greenP.y) / 2f)
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = labelPx
                isFakeBoldText = true
                textAlign = android.graphics.Paint.Align.CENTER
                setShadowLayer(6f, 0f, 2f, android.graphics.Color.argb(180, 0, 0, 0))
                isAntiAlias = true
            }
            canvas.nativeCanvas.drawText(label, mid.x + 28f, mid.y, paint)
            if (offMap) {
                paint.textSize = labelPx * 0.8f
                canvas.nativeCanvas.drawText("(you are off the map)", user.x, user.y + 34f, paint)
            }
        }
    }
}
