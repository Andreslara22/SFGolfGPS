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
        val strokes = prefs.getString("scores", null)?.split(SEP_TILE)?.getOrNull(active)
            ?.split(",")?.mapNotNull { it.toIntOrNull() }?.getOrNull(hole - 1) ?: 0

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
