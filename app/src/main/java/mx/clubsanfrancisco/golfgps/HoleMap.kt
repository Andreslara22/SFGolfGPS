package mx.clubsanfrancisco.golfgps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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

// ---- Paleta ilustración flat (referencia: aerial vector, desierto Chihuahua) ----
private val Waste = Color(0xFFEBDFC6)        // terreno desértico
private val WasteDots = Color(0xFFD9C8A5)    // matorral / textura
private val WasteScrub = Color(0xFFC9B78E)   // arbustos secos
private val RoughBand = Color(0xFF9EB47B)    // semi-rough alrededor del fairway
private val Fairway = Color(0xFF74A257)
private val FairwayStripe = Color(0xFF7FAD61) // franja de podado diagonal
private val GreenFringe = Color(0xFF93C06E)
private val GreenTurf = Color(0xFFAFD489)
private val Sand = Color(0xFFF4EAD2)
private val SandShadow = Color(0xFFD8C69E)
private val WaterDeep = Color(0xFF4C80B4)
private val WaterBlue = Color(0xFF6FA9DA)
private val Ripple = Color(0xFFA9CBEA)
private val TreeDark = Color(0xFF3F6C3C)
private val TreeLight = Color(0xFF548549)
private val TreeShadow = Color(0x2E2A4526)
private val Trunk = Color(0xFF6B4B2A)
private val FlagRed = Color(0xFFE0584A)
private val Pole = Color(0xFFFFFDF6)
private val PlayerBlue = Color(0xFF3D8BFF)
private val LineDark = Color(0xFF4E5A42)

/**
 * Ilustración custom por hoyo. Anclajes en fracciones de la imagen
 * (medidos por pixel-scan): posición del tee y del centro del green.
 * Con esos dos puntos se calibra la transformación lat/lng -> pantalla
 * para que el punto GPS, la línea a green y el cursor caigan exactos.
 */
private data class HoleArt(
    val resId: Int,
    val teeAnchor: Offset,
    val greenAnchor: Offset,
    val aspect: Float
)

private val holeArt: Map<Int, HoleArt> = mapOf(
    1 to HoleArt(R.drawable.hole_1, Offset(0.526f, 0.884f), Offset(0.420f, 0.143f), 1000f / 890f)
)

@Composable
fun HoleMapCard(hole: Hole, userLat: Double?, userLng: Double?, units: Units, flag: Int = -1) {
    val density = LocalDensity.current
    val labelPx = with(density) { 13.dp.toPx() }

    // Cursor de medición: toca el mapa para medir a ese punto (layups).
    // Se reinicia al cambiar de hoyo. Tocar cerca del cursor lo quita.
    var tapPoint by remember(hole.number) { mutableStateOf<Offset?>(null) }

    val art = holeArt[hole.number]
    if (art != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(art.aspect),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Waste)
        ) {
            Box(Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(art.resId),
                    contentDescription = "Hoyo ${hole.number}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(hole.number) {
                            detectTapGestures { tap ->
                                val current = tapPoint
                                tapPoint = if (current != null &&
                                    (current - tap).getDistance() < 44f) null else tap
                            }
                        }
                ) {
                    drawArtOverlay(hole, art, userLat, userLng, units, labelPx, tapPoint)
                }
            }
        }
        return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Waste)
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(280.dp)
                .pointerInput(hole.number) {
                    detectTapGestures { tap ->
                        val current = tapPoint
                        tapPoint = if (current != null &&
                            (current - tap).getDistance() < 44f) null else tap
                    }
                }
        ) {
            drawHoleMap(hole, userLat, userLng, units, labelPx, tapPoint, flag)
        }
    }
}

/**
 * Overlay GPS sobre la ilustración: transformación de similitud
 * (rotación + escala + traslación) que lleva tee->teeAnchor y
 * green->greenAnchor. Todo lo geográfico cae calibrado sobre el arte.
 */
private fun DrawScope.drawArtOverlay(
    hole: Hole,
    art: HoleArt,
    userLat: Double?,
    userLng: Double?,
    units: Units,
    labelPx: Float,
    tapPoint: Offset?
) {
    val w = size.width
    val h = size.height

    val lat0 = (hole.teeLat + hole.greenLat) / 2
    val lng0 = (hole.teeLng + hole.greenLng) / 2
    fun local(lat: Double, lng: Double): Offset {
        val x = ((lng - lng0) * cos(Math.toRadians(lat0)) * 111320.0).toFloat()
        val y = (-(lat - lat0) * 110540.0).toFloat()
        return Offset(x, y)
    }
    val teeL = local(hole.teeLat, hole.teeLng)
    val greenL = local(hole.greenLat, hole.greenLng)
    val teeP = Offset(art.teeAnchor.x * w, art.teeAnchor.y * h)
    val greenP = Offset(art.greenAnchor.x * w, art.greenAnchor.y * h)

    val src = greenL - teeL
    val dst = greenP - teeP
    val ang = atan2(dst.y, dst.x) - atan2(src.y, src.x)
    val sc = dst.getDistance() / src.getDistance().coerceAtLeast(0.001f)
    val ca = cos(ang); val sa = sin(ang)
    fun toScreen(p: Offset): Offset {
        val q = p - teeL
        return Offset(
            teeP.x + (q.x * ca - q.y * sa) * sc,
            teeP.y + (q.x * sa + q.y * ca) * sc
        )
    }
    fun toLatLng(s: Offset): Pair<Double, Double> {
        val q = (s - teeP) / sc
        val px = q.x * ca + q.y * sa
        val py = -q.x * sa + q.y * ca
        val lx = px + teeL.x
        val ly = py + teeL.y
        val lat = lat0 - ly / 110540.0
        val lng = lng0 + lx / (cos(Math.toRadians(lat0)) * 111320.0)
        return lat to lng
    }

    fun fmtDist(m: Double): String = if (units == Units.YARDS)
        "${metersToYards(m).roundToInt()} yd" else "${m.roundToInt()} m"
    fun drawDistLabel(text: String, at: Offset, sizeFactor: Float = 1f) {
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = labelPx * sizeFactor
                isFakeBoldText = true
                textAlign = android.graphics.Paint.Align.CENTER
                setShadowLayer(6f, 0f, 2f, android.graphics.Color.argb(200, 46, 56, 38))
                isAntiAlias = true
            }
            canvas.nativeCanvas.drawText(text, at.x, at.y, paint)
        }
    }

    val pad = 18f
    var user: Offset? = null
    var offMap = false
    if (userLat != null && userLng != null) {
        val raw = toScreen(local(userLat, userLng))
        user = Offset(raw.x.coerceIn(pad, w - pad), raw.y.coerceIn(pad, h - pad))
        offMap = raw != user
    }

    if (tapPoint != null) {
        val cursor = Offset(tapPoint.x.coerceIn(pad, w - pad), tapPoint.y.coerceIn(pad, h - pad))
        val (tapLat, tapLng) = toLatLng(cursor)
        val origin = user ?: teeP
        val originLat = userLat ?: hole.teeLat
        val originLng = userLng ?: hole.teeLng

        val d1 = haversineMeters(originLat, originLng, tapLat, tapLng)
        val d2 = haversineMeters(tapLat, tapLng, hole.greenLat, hole.greenLng)

        drawLine(PlayerBlue, origin, cursor, strokeWidth = 4f, cap = StrokeCap.Round)
        drawLine(
            LineDark.copy(alpha = 0.85f), cursor, greenP,
            strokeWidth = 3.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))
        )
        drawCircle(Color(0x59000000), radius = 15f, center = cursor + Offset(2f, 3f))
        drawCircle(Pole, radius = 13f, center = cursor, style = Stroke(width = 4f))
        drawLine(Pole, cursor + Offset(-19f, 0f), cursor + Offset(-8f, 0f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(Pole, cursor + Offset(8f, 0f), cursor + Offset(19f, 0f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(Pole, cursor + Offset(0f, -19f), cursor + Offset(0f, -8f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(Pole, cursor + Offset(0f, 8f), cursor + Offset(0f, 19f), strokeWidth = 3f, cap = StrokeCap.Round)

        val mid1 = Offset((origin.x + cursor.x) / 2f + 30f, (origin.y + cursor.y) / 2f)
        val mid2 = Offset((cursor.x + greenP.x) / 2f + 30f, (cursor.y + greenP.y) / 2f)
        drawDistLabel(fmtDist(d1), mid1)
        drawDistLabel(fmtDist(d2), mid2)
        drawDistLabel("toca el marcador para quitarlo", Offset(w / 2f, h - 10f), 0.72f)
    } else if (user != null && userLat != null && userLng != null) {
        drawLine(
            LineDark.copy(alpha = 0.85f), user, greenP,
            strokeWidth = 3.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))
        )
        val distM = haversineMeters(userLat, userLng, hole.greenLat, hole.greenLng)
        val mid = Offset((user.x + greenP.x) / 2f + 28f, (user.y + greenP.y) / 2f)
        drawDistLabel(fmtDist(distM), mid)
        drawDistLabel("toca el mapa para medir un layup", Offset(w / 2f, h - 10f), 0.72f)
    } else {
        drawDistLabel("toca el mapa para medir desde el tee", Offset(w / 2f, h - 10f), 0.72f)
    }

    if (user != null) {
        drawCircle(PlayerBlue.copy(alpha = 0.28f), radius = 20f, center = user)
        drawCircle(Pole, radius = 10f, center = user)
        drawCircle(PlayerBlue, radius = 7f, center = user)
        if (offMap) drawDistLabel("(fuera del mapa)", user + Offset(0f, 34f), 0.8f)
    }
}

private fun DrawScope.drawHoleMap(
    hole: Hole,
    userLat: Double?,
    userLng: Double?,
    units: Units,
    labelPx: Float,
    tapPoint: Offset? = null,
    flag: Int = -1
) {
    val flagColor = when (flag) {
        0 -> Color(0xFFE85D4A)   // frente (rojo)
        1 -> Color(0xFFF4F1E8)   // medio (blanco)
        2 -> Color(0xFF5AB0FF)   // fondo (azul)
        else -> FlagRed
    }
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
    /** Inversa de toScreen: píxel en pantalla -> lat/lng reales. */
    fun toLatLng(s: Offset): Pair<Double, Double> {
        val rx = (s.x - cx) / scale
        val ry = (s.y - cy) / scale
        val c = cos(-rot); val sn = sin(-rot)
        val px = rx * c - ry * sn
        val py = rx * sn + ry * c
        val lat = lat0 - py / 110540.0
        val lng = lng0 + px / (cos(Math.toRadians(lat0)) * 111320.0)
        return lat to lng
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

    // ---- Terreno desértico: textura de puntos + matorral + número de fondo ----
    val rnd = Random(hole.number * 97)
    repeat(46) {
        val p = Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)
        drawCircle(WasteDots, radius = 1.5f + rnd.nextFloat() * 2f, center = p)
    }
    repeat(14) {
        // arbustos secos: racimos de 3 puntitos
        val p = Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)
        val r = 2.2f + rnd.nextFloat() * 1.6f
        drawCircle(WasteScrub, radius = r, center = p)
        drawCircle(WasteScrub, radius = r * 0.8f, center = p + Offset(r * 1.3f, r * 0.4f))
        drawCircle(WasteScrub, radius = r * 0.7f, center = p + Offset(-r * 0.9f, r * 0.9f))
    }
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(30, 121, 106, 72)
            textSize = h * 0.42f
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.RIGHT
            isAntiAlias = true
        }
        canvas.nativeCanvas.drawText("${hole.number}", w - 14f, h * 0.42f, paint)
    }

    // ---- Fairway: blob orgánico de ancho variable + semi-rough alrededor ----
    val fairwayW = if (hole.par == 3) w * 0.20f else w * 0.30f
    val samples = 30
    val seed = hole.number * 1.7f
    fun widthAt(t: Float): Float {
        // más angosto en salida, panza en zona de caída, cintura antes del green
        val base = 0.62f + 0.38f * sin(PI.toFloat() * (0.15f + 0.85f * t))
        val wobble = 1f + 0.13f * sin(t * 9.4f + seed) + 0.07f * sin(t * 17.3f + seed * 2.1f)
        return fairwayW * 0.5f * base * wobble
    }
    fun blobPath(scaleW: Float): Path {
        val left = ArrayList<Offset>(samples + 1)
        val right = ArrayList<Offset>(samples + 1)
        for (i in 0..samples) {
            val t = i / samples.toFloat()
            val c = bez(t); val n = perp(t); val ww = widthAt(t) * scaleW
            left.add(c + n * -ww)
            right.add(c + n * ww)
        }
        return Path().apply {
            moveTo(left[0].x, left[0].y)
            for (q in left) lineTo(q.x, q.y)
            for (q in right.reversed()) lineTo(q.x, q.y)
            close()
        }
    }
    val roughPath = blobPath(1.55f)
    val blob = blobPath(1f)
    drawPath(roughPath, RoughBand)
    drawPath(blob, Fairway)
    // caps redondeados en tee y entrada al green
    drawCircle(RoughBand, radius = widthAt(0f) * 1.55f, center = p0)
    drawCircle(Fairway, radius = widthAt(0f), center = p0)
    drawCircle(RoughBand, radius = widthAt(1f) * 1.55f, center = p2)
    drawCircle(Fairway, radius = widthAt(1f), center = p2)
    // franjas de podado diagonales (recortadas al fairway)
    clipPath(blob) {
        val stripeW = w * 0.075f
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

    // ---- Agua (si el hoyo tiene) ----
    f.water?.let { wa ->
        val c = bez(wa.t) + perp(wa.t) * wa.side * (fairwayW * 0.5f + w * 0.10f * wa.w)
        val pw = w * 0.15f * wa.w
        val ph = w * 0.105f * wa.h
        drawOval(WaterDeep, topLeft = Offset(c.x - pw, c.y - ph + 5f), size = Size(pw * 2, ph * 2))
        drawOval(WaterBlue, topLeft = Offset(c.x - pw, c.y - ph), size = Size(pw * 2, ph * 2))
        for (i in 0..1) {
            drawArc(
                Ripple, startAngle = 200f, sweepAngle = 120f, useCenter = false,
                topLeft = Offset(c.x - pw * (0.45f + i * 0.25f), c.y - ph * (0.35f + i * 0.25f)),
                size = Size(pw * (0.9f + i * 0.5f), ph * (0.7f + i * 0.5f)),
                style = Stroke(width = 2.5f, cap = StrokeCap.Round)
            )
        }
    }

    // ---- Bunkers: blobs de arena suaves con sombra ----
    f.bunkers.forEach { b ->
        val c = bez(b.t) + perp(b.t) * b.side * (fairwayW * 0.5f + w * 0.045f * b.size)
        val br = w * 0.052f * b.size
        drawOval(SandShadow, topLeft = Offset(c.x - br, c.y - br * 0.55f + 4f), size = Size(br * 2f, br * 1.1f))
        drawOval(Sand, topLeft = Offset(c.x - br, c.y - br * 0.55f), size = Size(br * 2f, br * 1.1f))
        drawOval(Sand, topLeft = Offset(c.x - br * 0.5f, c.y - br * 0.85f), size = Size(br * 1.3f, br * 0.9f))
    }

    // ---- Árboles: flat con sombra desplazada ----
    val treeRnd = Random(hole.number * 31 + 7)
    repeat(f.trees) { i ->
        val t = 0.12f + treeRnd.nextFloat() * 0.72f
        val side = if ((i + hole.number) % 2 == 0) 1f else -1f
        val c = bez(t) + perp(t) * side * (fairwayW * 0.5f + w * (0.11f + treeRnd.nextFloat() * 0.08f))
        val r = w * (0.040f + treeRnd.nextFloat() * 0.017f)
        if (c.x - r > 4f && c.x + r < w - 4f && c.y - r > 4f && c.y + r < h - 4f) {
            drawOval(TreeShadow, topLeft = Offset(c.x - r * 0.85f + r * 0.5f, c.y - r * 0.35f + r * 0.55f),
                size = Size(r * 2.1f, r * 1.1f))
            drawLine(Trunk, Offset(c.x, c.y + r * 0.4f), Offset(c.x, c.y + r * 1.1f),
                strokeWidth = r * 0.32f, cap = StrokeCap.Round)
            drawCircle(TreeDark, radius = r, center = c)
            drawCircle(TreeDark, radius = r * 0.68f, center = Offset(c.x + r * 0.55f, c.y + r * 0.15f))
            drawCircle(TreeLight, radius = r * 0.52f, center = Offset(c.x - r * 0.3f, c.y - r * 0.3f))
        }
    }

    // ---- Green: blob orgánico + fringe + bandera ----
    val greenSeed = hole.number * 2.3f
    fun greenBlob(radius: Float, offset: Offset = Offset.Zero): Path {
        val pts = 26
        return Path().apply {
            for (i in 0..pts) {
                val a = (i / pts.toFloat()) * 2f * PI.toFloat()
                val rr = radius * (1f + 0.11f * sin(3f * a + greenSeed) + 0.05f * sin(5f * a - greenSeed))
                val p = Offset(greenP.x + offset.x + rr * cos(a), greenP.y + offset.y + rr * sin(a))
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
    }
    drawPath(greenBlob(gr * 1.22f, Offset(3f, 5f)), Color(0x26203A1E))
    drawPath(greenBlob(gr * 1.22f), GreenFringe)
    drawPath(greenBlob(gr), GreenTurf)
    val poleTop = Offset(greenP.x, greenP.y - gr * 1.55f)
    drawLine(Pole, greenP, poleTop, strokeWidth = 4f, cap = StrokeCap.Round)
    val flagPath = Path().apply {
        moveTo(poleTop.x, poleTop.y)
        lineTo(poleTop.x + gr * 0.75f, poleTop.y + gr * 0.28f)
        lineTo(poleTop.x, poleTop.y + gr * 0.56f)
        close()
    }
    drawPath(flagPath, flagColor)
    drawCircle(Color(0x33000000), radius = 5f, center = Offset(greenP.x, greenP.y + 2f))
    drawCircle(Pole, radius = 3.5f, center = greenP)

    // ---- Salida: caja de tee con dos marcas ----
    drawOval(Fairway, topLeft = Offset(teeP.x - 30f, teeP.y - 12f), size = Size(60f, 24f))
    drawCircle(Pole, radius = 6.5f, center = Offset(teeP.x - 14f, teeP.y))
    drawCircle(Pole, radius = 6.5f, center = Offset(teeP.x + 14f, teeP.y))
    drawCircle(FlagRed, radius = 3.2f, center = Offset(teeP.x - 14f, teeP.y))
    drawCircle(FlagRed, radius = 3.2f, center = Offset(teeP.x + 14f, teeP.y))

    // ---- Utilidad: etiqueta de distancia con sombra ----
    fun fmtDist(m: Double): String = if (units == Units.YARDS)
        "${metersToYards(m).roundToInt()} yd" else "${m.roundToInt()} m"
    fun drawDistLabel(text: String, at: Offset, sizeFactor: Float = 1f) {
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = labelPx * sizeFactor
                isFakeBoldText = true
                textAlign = android.graphics.Paint.Align.CENTER
                setShadowLayer(6f, 0f, 2f, android.graphics.Color.argb(200, 46, 56, 38))
                isAntiAlias = true
            }
            canvas.nativeCanvas.drawText(text, at.x, at.y, paint)
        }
    }

    // ---- Posición del jugador en pantalla (o tee si no hay GPS) ----
    val pad = 18f
    var user: Offset? = null
    var offMap = false
    if (userLat != null && userLng != null) {
        val raw = toScreen(local(userLat, userLng))
        user = Offset(raw.x.coerceIn(pad, w - pad), raw.y.coerceIn(pad, h - pad))
        offMap = raw != user
    }

    if (tapPoint != null) {
        // ---- Cursor de medición: origen -> punto tocado -> green ----
        val cursor = Offset(tapPoint.x.coerceIn(pad, w - pad), tapPoint.y.coerceIn(pad, h - pad))
        val (tapLat, tapLng) = toLatLng(cursor)
        val origin = user ?: teeP
        val originLat = userLat ?: hole.teeLat
        val originLng = userLng ?: hole.teeLng

        val d1 = haversineMeters(originLat, originLng, tapLat, tapLng)
        val d2 = haversineMeters(tapLat, tapLng, hole.greenLat, hole.greenLng)

        drawLine(PlayerBlue, origin, cursor, strokeWidth = 4f, cap = StrokeCap.Round)
        drawLine(
            LineDark.copy(alpha = 0.85f), cursor, greenP,
            strokeWidth = 3.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))
        )
        drawCircle(Color(0x59000000), radius = 15f, center = cursor + Offset(2f, 3f))
        drawCircle(Pole, radius = 13f, center = cursor, style = Stroke(width = 4f))
        drawLine(Pole, cursor + Offset(-19f, 0f), cursor + Offset(-8f, 0f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(Pole, cursor + Offset(8f, 0f), cursor + Offset(19f, 0f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(Pole, cursor + Offset(0f, -19f), cursor + Offset(0f, -8f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(Pole, cursor + Offset(0f, 8f), cursor + Offset(0f, 19f), strokeWidth = 3f, cap = StrokeCap.Round)

        val mid1 = Offset((origin.x + cursor.x) / 2f + 30f, (origin.y + cursor.y) / 2f)
        val mid2 = Offset((cursor.x + greenP.x) / 2f + 30f, (cursor.y + greenP.y) / 2f)
        drawDistLabel(fmtDist(d1), mid1)
        drawDistLabel(fmtDist(d2), mid2)
        drawDistLabel("toca el marcador para quitarlo", Offset(w / 2f, h - 10f), 0.72f)
    } else if (user != null && userLat != null && userLng != null) {
        drawLine(
            LineDark.copy(alpha = 0.85f), user, greenP,
            strokeWidth = 3.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))
        )
        val distM = haversineMeters(userLat, userLng, hole.greenLat, hole.greenLng)
        val mid = Offset((user.x + greenP.x) / 2f + 28f, (user.y + greenP.y) / 2f)
        drawDistLabel(fmtDist(distM), mid)
        drawDistLabel("toca el mapa para medir un layup", Offset(w / 2f, h - 10f), 0.72f)
    } else {
        drawDistLabel("toca el mapa para medir desde el tee", Offset(w / 2f, h - 10f), 0.72f)
    }

    // ---- Punto del jugador (siempre encima de las líneas) ----
    if (user != null) {
        drawCircle(PlayerBlue.copy(alpha = 0.28f), radius = 20f, center = user)
        drawCircle(Pole, radius = 10f, center = user)
        drawCircle(PlayerBlue, radius = 7f, center = user)
        if (offMap) drawDistLabel("(fuera del mapa)", user + Offset(0f, 34f), 0.8f)
    }
}
