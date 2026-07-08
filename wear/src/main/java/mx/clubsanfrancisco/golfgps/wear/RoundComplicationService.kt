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
        listener.onComplicationData(build(request.complicationType, hole, strokes))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, 7, 3)

    private fun build(type: ComplicationType, hole: Int, strokes: Int): ComplicationData? {
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val desc = PlainComplicationText.Builder("Hoyo $hole, $strokes golpes").build()
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(
                    PlainComplicationText.Builder("H$hole").build(), desc
                )
                    .setTitle(PlainComplicationText.Builder("⛳$strokes").build())
                    .setTapAction(tap)
                    .build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(
                    PlainComplicationText.Builder("Hoyo $hole · $strokes golpes").build(), desc
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
