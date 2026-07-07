package mx.clubsanfrancisco.golfgps.wear

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Paleta pensada para fundirse sobre el negro del reloj (más viva que la del teléfono).
private val Rough = Color(0xFF26402B)
private val Fairway = Color(0xFF4F9350)
private val FairwayStripe = Color(0xFF579A57)
private val GreenFringe = Color(0xFF6FB85E)
private val GreenTurf = Color(0xFF9AD77A)
private val Sand = Color(0xFFEAD9A6)
private val SandShadow = Color(0xFFCBB77E)
private val WaterDeep = Color(0xFF3C79AE)
private val WaterBlue = Color(0xFF4F92C9)
private val FlagRed = Color(0xFFE0584A)
private val Pole = Color(0xFFFFFFFF)
private val PlayerBlue = Color(0xFF3D8BFF)
private val LineLt = Color(0xFFE6F2D6)

/**
 * Dibuja el hoyo (green arriba) ocupando la mitad derecha del lienzo. El borde
 * izquierdo se difumina a transparente con un degradado para fundirse con el
 * negro del UI (sin línea dura en medio). Pinta fairway, green, bandera, tee,
 * bunkers, agua y el punto GPS del jugador con línea al green.
 */
fun DrawScope.drawMiniHole(hole: WHole, feat: WFeatures, userLat: Double?, userLng: Double?) {
    val w = size.width
    val h = size.height

    // Base de terreno: transparente en la izquierda -> Rough hacia la derecha.
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.40f to Color.Transparent,
            0.60f to Rough,
            1f to Rough
        )
    )

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
    val angle = atan2(greenL.y - teeL.y, greenL.x - teeL.x)
    val rot = (-PI / 2 - angle).toFloat()
    fun rotate(p: Offset): Offset {
        val c = cos(rot); val s = sin(rot)
        return Offset(p.x * c - p.y * s, p.x * s + p.y * c)
    }
    val lengthM = rotate(teeL).y - rotate(greenL).y
    val greenY = h * 0.17f
    val teeY = h * 0.90f
    val scale = (teeY - greenY) / lengthM
    val cx = w * 0.70f          // hoyo centrado en la mitad derecha
    val cy = (teeY + greenY) / 2f
    fun toScreen(p: Offset): Offset {
        val r = rotate(p)
        return Offset(cx + r.x * scale, cy + r.y * scale)
    }
    val teeP = Offset(cx, teeY)
    val greenP = Offset(cx, greenY)
    val gr = min(w, h) * 0.13f

    // ---- Fairway curvo (dogleg) como Bézier cuadrática ----
    val p0 = teeP
    val p2 = Offset(greenP.x, greenP.y + gr * 1.4f)
    val p1 = Offset(cx + feat.dogleg * w * 0.30f, (teeY + greenY) / 2f)
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
        return Offset(-ty / len, tx / len)
    }
    val fairwayW = if (hole.par == 3) w * 0.24f else w * 0.32f
    val samples = 26
    val seed = hole.number * 1.7f
    fun widthAt(t: Float): Float {
        val base = 0.62f + 0.38f * sin(PI.toFloat() * (0.15f + 0.85f * t))
        val wobble = 1f + 0.12f * sin(t * 9.4f + seed)
        return fairwayW * 0.5f * base * wobble
    }
    fun blobPath(scaleW: Float): Path {
        val left = ArrayList<Offset>(samples + 1)
        val right = ArrayList<Offset>(samples + 1)
        for (i in 0..samples) {
            val t = i / samples.toFloat()
            val c = bez(t); val n = perp(t); val ww = widthAt(t) * scaleW
            left.add(c + n * -ww); right.add(c + n * ww)
        }
        return Path().apply {
            moveTo(left[0].x, left[0].y)
            for (q in left) lineTo(q.x, q.y)
            for (q in right.reversed()) lineTo(q.x, q.y)
            close()
        }
    }
    val blob = blobPath(1f)
    drawPath(blob, Fairway)
    drawCircle(Fairway, radius = widthAt(0f), center = p0)
    drawCircle(Fairway, radius = widthAt(1f), center = p2)
    // franjas de podado diagonales (recortadas al fairway)
    clipPath(blob) {
        val stripeW = w * 0.085f
        var x = -h * 0.7f
        var k = 0
        while (x < w + h * 0.7f) {
            if (k % 2 == 0) {
                drawLine(
                    FairwayStripe,
                    Offset(x, h + 10f), Offset(x + h * 0.7f, -10f),
                    strokeWidth = stripeW
                )
            }
            x += stripeW; k++
        }
    }

    // ---- Agua ----
    feat.water?.let { wa ->
        val c = bez(wa.t) + perp(wa.t) * wa.side * (fairwayW * 0.5f + w * 0.09f * wa.w)
        val pw = w * 0.13f * wa.w
        val ph = w * 0.095f * wa.h
        drawOval(WaterDeep, topLeft = Offset(c.x - pw, c.y - ph + 4f), size = Size(pw * 2, ph * 2))
        drawOval(WaterBlue, topLeft = Offset(c.x - pw, c.y - ph), size = Size(pw * 2, ph * 2))
    }

    // ---- Bunkers ----
    feat.bunkers.forEach { b ->
        val c = bez(b.t) + perp(b.t) * b.side * (fairwayW * 0.5f + w * 0.05f * b.size)
        val br = w * 0.055f * b.size
        drawOval(SandShadow, topLeft = Offset(c.x - br, c.y - br * 0.55f + 3f), size = Size(br * 2f, br * 1.1f))
        drawOval(Sand, topLeft = Offset(c.x - br, c.y - br * 0.55f), size = Size(br * 2f, br * 1.1f))
    }

    // ---- Green + bandera ----
    val greenSeed = hole.number * 2.3f
    fun greenBlob(radius: Float): Path {
        val pts = 24
        return Path().apply {
            for (i in 0..pts) {
                val a = (i / pts.toFloat()) * 2f * PI.toFloat()
                val rr = radius * (1f + 0.10f * sin(3f * a + greenSeed))
                val p = Offset(greenP.x + rr * cos(a), greenP.y + rr * sin(a))
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
    }
    drawPath(greenBlob(gr * 1.22f), GreenFringe)
    drawPath(greenBlob(gr), GreenTurf)
    val poleTop = Offset(greenP.x, greenP.y - gr * 1.55f)
    drawLine(Pole, greenP, poleTop, strokeWidth = 3f, cap = StrokeCap.Round)
    val flagPath = Path().apply {
        moveTo(poleTop.x, poleTop.y)
        lineTo(poleTop.x + gr * 0.72f, poleTop.y + gr * 0.26f)
        lineTo(poleTop.x, poleTop.y + gr * 0.52f)
        close()
    }
    drawPath(flagPath, FlagRed)
    drawCircle(Pole, radius = 3f, center = greenP)

    // ---- Tee ----
    drawOval(Fairway, topLeft = Offset(teeP.x - 22f, teeP.y - 8f), size = Size(44f, 16f))

    // ---- Jugador (GPS) + línea punteada al green ----
    if (userLat != null && userLng != null) {
        val pad = 10f
        val raw = toScreen(local(userLat, userLng))
        val user = Offset(raw.x.coerceIn(pad, w - pad), raw.y.coerceIn(pad, h - pad))
        drawLine(
            LineLt.copy(alpha = 0.75f), user, greenP, strokeWidth = 3f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f))
        )
        drawCircle(PlayerBlue.copy(alpha = 0.28f), radius = 15f, center = user)
        drawCircle(Pole, radius = 8f, center = user)
        drawCircle(PlayerBlue, radius = 5.5f, center = user)
    }
}
