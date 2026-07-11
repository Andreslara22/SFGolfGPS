package mx.clubsanfrancisco.golfgps.wear

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest

/**
 * Complicación para cualquier carátula: hoyo actual y golpes del jugador
 * activo (leídos de las prefs sincronizadas, igual que el Tile). Un toque
 * abre la app. Se refresca desde persist() en MainActivity y, como respaldo,
 * cada 10 min (UPDATE_PERIOD_SECONDS del manifest).
 */
class RoundComplicationService : ComplicationDataSourceService() {

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        val prefs = getSharedPreferences("wear", MODE_PRIVATE)
        val hole = prefs.getInt("hole", 0).coerceIn(0, 17) + 1
        val active = prefs.getInt("active", 0)
        val strokes = prefs.getString("scores", null)?.split(SEP)?.getOrNull(active)
            ?.split(",")?.mapNotNull { it.toIntOrNull() }?.getOrNull(hole - 1) ?: 0
        // Distancia al green del último fix GPS (solo si es reciente, <15 min).
        val distM = prefs.getInt("dist", -1)
        val fresh = System.currentTimeMillis() - prefs.getLong("distTs", 0L) < 15 * 60_000L
        val yards = prefs.getString("units", "YARDS") != "METERS"
        val distTxt = if (distM >= 0 && fresh) {
            if (yards) "${(distM * 1.09361).toInt()}y" else "${distM}m"
        } else null
        listener.onComplicationData(build(request.complicationType, hole, strokes, distTxt))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, 7, 3, "136m")

    private fun build(
        type: ComplicationType,
        hole: Int,
        strokes: Int,
        distTxt: String? = null
    ): ComplicationData? {
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val desc = PlainComplicationText.Builder("Hoyo $hole, $strokes golpes").build()
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(
                    // Con GPS fresco: la distancia al green manda; si no, el hoyo.
                    PlainComplicationText.Builder(distTxt ?: "H$hole").build(), desc
                )
                    .setTitle(
                        PlainComplicationText.Builder(
                            if (distTxt != null) "H$hole·⛳$strokes" else "⛳$strokes"
                        ).build()
                    )
                    .setTapAction(tap)
                    .build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(
                    PlainComplicationText.Builder(
                        if (distTxt != null) "Hoyo $hole · $distTxt al green · $strokes golpes"
                        else "Hoyo $hole · $strokes golpes"
                    ).build(), desc
                )
                    .setTapAction(tap)
                    .build()
            else -> null
        }
    }

    companion object {
        /** Mismo separador (U+0001) que usan las prefs sincronizadas del reloj. */
        private const val SEP = "\u0001"
    }
}
