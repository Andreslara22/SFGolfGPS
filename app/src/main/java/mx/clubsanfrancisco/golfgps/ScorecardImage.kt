package mx.clubsanfrancisco.golfgps

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dibuja la tarjeta de la ronda actual en un PNG y arma un Intent para
 * compartirla (WhatsApp, correo, etc.). Todo con Canvas de Android: no depende
 * de la jerarquía de Compose, así que funciona aunque la vista no esté montada.
 */
object ScorecardImage {

    // Paleta de la imagen (independiente del tema de la app, para que se vea
    // igual y legible en WhatsApp con fondo claro).
    private const val GREEN = 0xFF1B5E3F.toInt()
    private const val GREEN_SOFT = 0xFFE6F0EA.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val INK = 0xFF1A1A1A.toInt()
    private const val GRID = 0xFFD9D9D9.toInt()
    private const val MUTED = 0xFF6B6B6B.toInt()
    private const val MARK = 0xFF9BA8A0.toInt()      // aro/cuadro bajo/sobre par
    private const val STRIPE = 0xFFF3F8F5.toInt()    // franja de filas alternas

    fun shareIntent(context: Context, vm: GolfViewModel): Intent? {
        val players = vm.players.toList()
        if (players.isEmpty()) return null
        val bmp = render(vm, players)
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "tarjeta_sfgolf.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun paint(size: Float, color: Int, bold: Boolean, center: Boolean = false) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
            if (center) textAlign = Paint.Align.CENTER
        }

    private const val W = 1080
    private const val PAD = 36f
    private const val LABEL_W = 190f
    private const val ROW_H = 62f
    /** Las columnas OUT / TOTAL son más anchas que las de hoyo (no se cortan). */
    private const val SUM_COL = 1.45f
    private const val FOOTER_H = 50f

    private fun render(vm: GolfViewModel, players: List<Player>): Bitmap {
        val n = players.size
        val titleH = 168f
        val blockH = ROW_H * (2 + n)          // fila Hoyo + Par + jugadores
        val gap = 28f
        val footHeadH = 54f
        val footH = footHeadH + ROW_H * n
        // gap tras el título + entre bloques + antes del resumen = 3 gaps.
        val height = (titleH + gap + blockH + gap + blockH + gap + footH + FOOTER_H + PAD).toInt()

        val bmp = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(WHITE)

        // ---- Encabezado ----
        c.drawRect(0f, 0f, W.toFloat(), titleH, paint(1f, GREEN, false).apply { style = Paint.Style.FILL })
        c.drawText("⛳ SF Golf GPS", PAD, 74f, paint(52f, WHITE, true))
        c.drawText(CourseData.CLUB_NAME + " · Par ${CourseData.totalPar}", PAD, 116f, paint(30f, WHITE, false))
        val fecha = SimpleDateFormat("EEEE d 'de' MMMM, yyyy · h:mm a", Locale("es", "MX")).format(Date())
        c.drawText(fecha.replaceFirstChar { it.uppercase() }, PAD, 150f, paint(26f, 0xFFCDE4D6.toInt(), false))

        var y = titleH + gap
        y = drawBlock(c, players, 0 until 9, "OUT", null, y, n)
        y += gap
        y = drawBlock(c, players, 9 until 18, "IN", "TOTAL", y, n)
        y += gap

        // ---- Resumen por jugador ----
        c.drawText("Resumen", PAD, y + 36f, paint(34f, GREEN, true))
        y += footHeadH
        players.forEach { p ->
            drawSummary(c, p, y)
            y += ROW_H
        }

        // ---- Pie ----
        c.drawText(
            "⛳ SF Golf GPS · ${CourseData.CLUB_NAME}",
            W / 2f, y + FOOTER_H - 8f,
            paint(22f, MUTED, false, center = true)
        )
        return bmp
    }

    /** Un bloque de 9 hoyos (ida o vuelta) con su fila Hoyo, Par y cada jugador. */
    private fun drawBlock(
        c: Canvas, players: List<Player>, range: IntRange,
        sumLabel: String, totLabel: String?, top: Float, n: Int
    ): Float {
        val extraCols = if (totLabel != null) 2 else 1  // OUT, o IN+TOTAL
        val units = 9 + extraCols * SUM_COL
        val cellW = (W - 2 * PAD - LABEL_W) / units
        val sumW = cellW * SUM_COL
        val x0 = PAD

        // Centros de columna: hoyos 0-8, luego OUT/IN y TOTAL (más anchas).
        fun holeX(i: Int) = x0 + LABEL_W + i * cellW + cellW / 2
        val sumX = x0 + LABEL_W + 9 * cellW + sumW / 2
        val totX = x0 + LABEL_W + 9 * cellW + sumW + sumW / 2

        // Fila de hoyos (fondo verde)
        var y = top
        c.drawRect(x0, y, W - PAD, y + ROW_H, paint(1f, GREEN, false).apply { style = Paint.Style.FILL })
        c.drawText("Hoyo", x0 + 14f, y + 40f, paint(30f, WHITE, true))
        range.forEachIndexed { i, h ->
            c.drawText("${h + 1}", holeX(i), y + 40f, paint(30f, WHITE, true, center = true))
        }
        c.drawText(sumLabel, sumX, y + 40f, paint(28f, WHITE, true, center = true))
        totLabel?.let { c.drawText(it, totX, y + 40f, paint(24f, WHITE, true, center = true)) }

        // Fila de par (fondo suave)
        y += ROW_H
        c.drawRect(x0, y, W - PAD, y + ROW_H, paint(1f, GREEN_SOFT, false).apply { style = Paint.Style.FILL })
        c.drawText("Par", x0 + 14f, y + 40f, paint(28f, MUTED, true))
        range.forEachIndexed { i, h ->
            c.drawText("${CourseData.holes[h].par}", holeX(i), y + 40f, paint(28f, MUTED, true, center = true))
        }
        val parSum = range.sumOf { CourseData.holes[it].par }
        c.drawText("$parSum", sumX, y + 40f, paint(28f, MUTED, true, center = true))
        totLabel?.let { c.drawText("${CourseData.totalPar}", totX, y + 40f, paint(28f, MUTED, true, center = true)) }

        // Filas por jugador (franjas alternas para leer mejor)
        players.forEachIndexed { pi, p ->
            y += ROW_H
            if (pi % 2 == 1) {
                c.drawRect(x0, y, W - PAD, y + ROW_H, paint(1f, STRIPE, false).apply { style = Paint.Style.FILL })
            }
            c.drawText(p.name.take(11), x0 + 14f, y + 40f, paint(28f, INK, true))
            range.forEachIndexed { i, h ->
                val s = p.strokes[h]
                val cx = holeX(i)
                if (s > 0) {
                    drawScoreMark(c, cx, y + ROW_H / 2, s - CourseData.holes[h].par)
                    c.drawText("$s", cx, y + 40f, paint(30f, INK, true, center = true))
                } else {
                    c.drawText("·", cx, y + 40f, paint(30f, GRID, true, center = true))
                }
            }
            val outSum = range.sumOf { p.strokes[it] }
            c.drawText(if (outSum > 0) "$outSum" else "·", sumX, y + 40f, paint(30f, INK, true, center = true))
            totLabel?.let {
                val tot = p.total()
                c.drawText(if (tot > 0) "$tot" else "·", totX, y + 40f, paint(32f, GREEN, true, center = true))
            }
        }

        // Borde del bloque
        c.drawRect(x0, top, W - PAD, y + ROW_H, paint(1f, GRID, false).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f
        })
        return y + ROW_H
    }

    /** Círculo para bajo par, cuadro para sobre par (como en la app), en gris neutro. */
    private fun drawScoreMark(c: Canvas, cx: Float, cy: Float, diff: Int) {
        if (diff == 0) return
        val r = 24f
        val p = paint(1f, MARK, false).apply {
            style = Paint.Style.STROKE; strokeWidth = 2.5f
        }
        if (diff < 0) c.drawCircle(cx, cy, r, p)
        else c.drawRoundRect(RectF(cx - r, cy - r, cx + r, cy + r), 6f, 6f, p)
    }

    private fun drawSummary(c: Canvas, p: Player, top: Float) {
        val rel = p.relativeToPar()
        val relTxt = when {
            p.playedHoles() == 0 -> "–"
            rel == 0 -> "E"
            rel > 0 -> "+$rel"
            else -> "$rel"
        }
        val bits = buildList {
            add("${p.total()} ($relTxt)")
            val pts = p.stablefordPoints()
            if (pts > 0) add("$pts pts" + if (p.hcp > 0) " · hcp ${p.hcp}" else "")
            val (fh, fa) = p.firStats()
            if (fa > 0) add("FIR $fh/$fa")
            if (p.girTracked() > 0) add("GIR ${p.girCount()}/${p.girTracked()}")
            if (p.totalPutts() > 0) add("${p.totalPutts()} putts")
        }
        c.drawText(p.name.take(12), PAD, top + 40f, paint(30f, INK, true))
        // El texto se encoge hasta caber en el ancho disponible (no se corta).
        val text = bits.joinToString("  ·  ")
        val maxW = W - PAD - (PAD + 250f)
        val pnt = paint(26f, MUTED, false)
        while (pnt.measureText(text) > maxW && pnt.textSize > 17f) pnt.textSize -= 1f
        c.drawText(text, PAD + 250f, top + 40f, pnt)
    }
}
