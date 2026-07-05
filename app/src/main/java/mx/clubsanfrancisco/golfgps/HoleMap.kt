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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import kotlin.random.Random

// ---- Cartoon palette (fixed for both themes: it's terrain) ----
private val Rough = Color(0xFF14382A)
private val RoughDots = Color(0xFF1B4634)
private val Fairway = Color(0xFF3E8E5E)
private val FairwayStripe = Color(0xFF4BA06C)
private val GreenFringe = Color(0xFF57B87B)
private val GreenTurf = Color(0xFF7FD69E)
private val Sand = Color(0xFFEAD08F)
private val SandShadow = Color(0xFFD9BC74)
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
            .height(250.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Rough)
    ) {
        Canvas(Modifier.fillMaxWidth().height(250.dp)) {
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

    // ---- Projection: lat/lng -> local meters -> rotate so green is up -> screen ----
    val lat0 = (hole.teeLat + hole.greenLat) / 2
    val lng0 = (hole.teeLng + hole.greenLng) / 2
    fun local(lat: Double, lng: Double): Offset {
        val x = ((lng - lng0) * cos(Math.toRadians(lat0)) * 111320.0).toFloat()
        val y = (-(lat - lat0) * 110540.0).toFloat() // screen y grows downward
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

    val teeR = rotate(teeL)     // -> (0, +L/2)
    val greenR = rotate(greenL) // -> (0, -L/2)
    val lengthM = (teeR.y - greenR.y)

    val greenY = h * 0.20f
    val teeY = h * 0.86f
    val scale = (teeY - greenY) / lengthM
    val cx = w / 2f
    val cy = (teeY + greenY) / 2f
    fun toScreen(p: Offset): Offset {
        val r = rotate(p)
        return Offset(cx + r.x * scale, cy + r.y * scale)
    }

    val teeP = Offset(cx, teeY)
    val greenP = Offset(cx, greenY)

    // ---- Rough texture: sparse cartoon grass dots ----
    val rnd = Random(hole.number * 97)
    repeat(46) {
        val p = Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)
        drawCircle(RoughDots, radius = 2.5f + rnd.nextFloat() * 2f, center = p)
    }

    // ---- Fairway: fat rounded band with mow stripes ----
    val fairwayW = w * 0.30f
    drawLine(Fairway, teeP, Offset(cx, greenY + h * 0.06f),
        strokeWidth = fairwayW, cap = StrokeCap.Round)
    // mowing stripes (alternating lighter bands)
    val bands = 6
    for (i in 0 until bands step 2) {
        val y1 = teeY - (teeY - greenY) * (i / bands.toFloat())
        val y2 = teeY - (teeY - greenY) * ((i + 1) / bands.toFloat())
        drawLine(FairwayStripe,
            Offset(cx, y1), Offset(cx, y2),
            strokeWidth = fairwayW * 0.92f, cap = StrokeCap.Butt)
    }

    // ---- Trees along the sides (deterministic per hole) ----
    val treeRnd = Random(hole.number * 31 + 7)
    val treeSlots = listOf(0.22f, 0.42f, 0.62f, 0.80f)
    treeSlots.forEachIndexed { i, t ->
        val side = if ((i + hole.number) % 2 == 0) 1f else -1f
        val ty = teeY - (teeY - greenY) * t
        val tx = cx + side * (fairwayW / 2f + w * (0.14f + treeRnd.nextFloat() * 0.06f))
        val r = w * (0.045f + treeRnd.nextFloat() * 0.015f)
        if (tx - r > 4f && tx + r < w - 4f) {
            drawLine(Trunk, Offset(tx, ty + r * 0.4f), Offset(tx, ty + r * 1.1f), strokeWidth = r * 0.35f, cap = StrokeCap.Round)
            drawCircle(TreeDark, radius = r, center = Offset(tx, ty))
            drawCircle(TreeLight, radius = r * 0.55f, center = Offset(tx - r * 0.3f, ty - r * 0.3f))
        }
    }

    // ---- Bunker: sand blob near the green ----
    val gr = min(w, h) * 0.135f
    val bunkerC = Offset(cx + gr * 1.7f, greenY + gr * 1.5f)
    drawOval(SandShadow,
        topLeft = Offset(bunkerC.x - gr * 0.85f, bunkerC.y - gr * 0.48f),
        size = androidx.compose.ui.geometry.Size(gr * 1.7f, gr * 0.96f))
    drawOval(Sand,
        topLeft = Offset(bunkerC.x - gr * 0.8f, bunkerC.y - gr * 0.5f),
        size = androidx.compose.ui.geometry.Size(gr * 1.6f, gr * 0.9f))

    // ---- Green: fringe + turf + flag ----
    drawCircle(GreenFringe, radius = gr * 1.18f, center = greenP)
    drawCircle(GreenTurf, radius = gr, center = greenP)
    // flagstick
    val poleTop = Offset(greenP.x, greenP.y - gr * 1.55f)
    drawLine(Pole, greenP, poleTop, strokeWidth = 4f, cap = StrokeCap.Round)
    val flagPath = androidx.compose.ui.graphics.Path().apply {
        moveTo(poleTop.x, poleTop.y)
        lineTo(poleTop.x + gr * 0.75f, poleTop.y + gr * 0.28f)
        lineTo(poleTop.x, poleTop.y + gr * 0.56f)
        close()
    }
    drawPath(flagPath, FlagRed)
    drawCircle(Color(0x33000000), radius = 5f, center = Offset(greenP.x, greenP.y + 2f))
    drawCircle(Pole, radius = 3.5f, center = greenP) // cup

    // ---- Tee box: two cartoon tee markers ----
    drawCircle(Pole, radius = 7f, center = Offset(teeP.x - 14f, teeP.y))
    drawCircle(Pole, radius = 7f, center = Offset(teeP.x + 14f, teeP.y))
    drawCircle(FlagRed, radius = 3.5f, center = Offset(teeP.x - 14f, teeP.y))
    drawCircle(FlagRed, radius = 3.5f, center = Offset(teeP.x + 14f, teeP.y))

    // ---- Player position + dashed line + live distance ----
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

        // player dot with halo
        drawCircle(PlayerBlue.copy(alpha = 0.28f), radius = 20f, center = user)
        drawCircle(Pole, radius = 10f, center = user)
        drawCircle(PlayerBlue, radius = 7f, center = user)

        // distance label at midpoint of the line
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
            canvas.nativeCanvas.drawText(label, mid.x + 26f, mid.y, paint)
            if (offMap) {
                paint.textSize = labelPx * 0.8f
                canvas.nativeCanvas.drawText("(you are off the map)", user.x, user.y + 34f, paint)
            }
        }
    }
}
