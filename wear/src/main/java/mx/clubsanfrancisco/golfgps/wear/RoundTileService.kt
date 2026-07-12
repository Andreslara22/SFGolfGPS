package mx.clubsanfrancisco.golfgps.wear

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.ColorBuilders
import androidx.wear.tiles.DimensionBuilders
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.ModifiersBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders
import com.google.common.util.concurrent.ListenableFuture

/**
 * Tile de la carátula: resumen rápido (hoyo actual y golpes del jugador
 * activo, leídos de las prefs sincronizadas) y un toque abre la app.
 */
class RoundTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> = CallbackToFutureAdapter.getFuture { completer ->
        val prefs = getSharedPreferences("wear", MODE_PRIVATE)
        val hole = prefs.getInt("hole", 0).coerceIn(0, 17) + 1
        val active = prefs.getInt("active", 0)
        val name = prefs.getString("names", null)?.split(SEP_TILE)?.getOrNull(active) ?: ""
        val allStrokes = prefs.getString("scores", null)?.split(SEP_TILE)?.getOrNull(active)
            ?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
        val strokes = allStrokes.getOrNull(hole - 1) ?: 0
        // Resumen de la ronda del jugador activo (OUT/IN/TOTAL vs par).
        val out = (0..8).sumOf { allStrokes.getOrNull(it) ?: 0 }
        val inn = (9..17).sumOf { allStrokes.getOrNull(it) ?: 0 }
        val rel = allStrokes.withIndex().sumOf { (i, v) ->
            if (v > 0) v - WearCourse.holes[i].par else 0
        }
        val relTxt = if (rel == 0) "E" else if (rel > 0) "+$rel" else "$rel"
        // Distancia al green del último fix (solo si es reciente).
        val distM = prefs.getInt("dist", -1)
        val freshDist = System.currentTimeMillis() - prefs.getLong("distTs", 0L) < 15 * 60_000L
        val yards = prefs.getString("units", "YARDS") != "METERS"
        val distTxt = if (distM >= 0 && freshDist) {
            if (yards) "${(distM * 1.09361).toInt()} yd al green" else "$distM m al green"
        } else null

        val openApp = ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName("mx.clubsanfrancisco.golfgps.wear.MainActivity")
                            .build()
                    )
                    .build()
            )
            .build()

        fun text(t: String, sizeSp: Float, color: Int, bold: Boolean) =
            LayoutElementBuilders.Text.Builder()
                .setText(t)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setSize(DimensionBuilders.sp(sizeSp))
                        .setColor(ColorBuilders.argb(color))
                        .setWeight(
                            if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD
                            else LayoutElementBuilders.FONT_WEIGHT_NORMAL
                        )
                        .build()
                )
                .build()

        val column = LayoutElementBuilders.Column.Builder()
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(openApp).build())
            .addContent(text("SF GOLF", 14f, 0xFF7ADFA8.toInt(), true))
            .addContent(text("Hoyo $hole", 30f, 0xFFFFFFFF.toInt(), true))
            .addContent(
                text(
                    if (name.isNotEmpty()) "$strokes golpes · $name" else "$strokes golpes",
                    14f, 0xFF9BB8A8.toInt(), false
                )
            )
            .apply {
                if (distTxt != null)
                    addContent(text(distTxt, 16f, 0xFFFFFFFF.toInt(), true))
                if (out + inn > 0)
                    addContent(
                        text(
                            "OUT $out · IN $inn · TOTAL ${out + inn} ($relTxt)",
                            13f, 0xFF9BB8A8.toInt(), false
                        )
                    )
            }
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setFreshnessIntervalMillis(60_000)
            .setTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(column)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
        completer.set(tile)
        "tileRequest"
    }

    override fun onResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> = CallbackToFutureAdapter.getFuture { completer ->
        completer.set(ResourceBuilders.Resources.Builder().setVersion("1").build())
        "resourcesRequest"
    }

    companion object {
        private const val SEP_TILE = ""
    }
}
