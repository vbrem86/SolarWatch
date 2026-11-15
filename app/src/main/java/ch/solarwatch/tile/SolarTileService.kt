package ch.solarwatch.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import ch.solarwatch.MainActivity
import ch.solarwatch.data.AppIcon
import ch.solarwatch.data.DisplaySettings
import ch.solarwatch.data.Metric
import ch.solarwatch.data.MetricView
import ch.solarwatch.data.Palette
import ch.solarwatch.data.SettingsStore
import ch.solarwatch.data.SolarData
import ch.solarwatch.data.SolarManagerApi
import ch.solarwatch.data.isAvailable
import ch.solarwatch.data.view
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future

private const val RESOURCES_VERSION = "3"

/**
 * Kachel mit den in den Einstellungen gewählten Werten (3 Zeilen: 2 / 1 / 3).
 * Tippen öffnet die App.
 */
class SolarTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> = scope.future {
        val data = runCatching { SolarManagerApi.fetchStream() }.getOrNull() ?: SolarManagerApi.lastData
        val settings = SettingsStore.display.value
        val needsToday = settings.tile.rows.flatten().contains(Metric.TODAY_PV)
        val today = if (needsToday) runCatching { SolarManagerApi.todayPvWh() }.getOrNull() else null

        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(5 * 60 * 1000L)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(data, today, settings)))
            .build()
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> = scope.future {
        val res = ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION)
        AppIcon.entries.forEach { icon ->
            res.addIdToImageMapping(
                icon.name,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(icon.res)
                            .build(),
                    )
                    .build(),
            )
        }
        res.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ------------------------------------------------------------------ layout

    private fun layout(d: SolarData?, todayWh: Double?, settings: DisplaySettings): LayoutElement {
        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

        if (d == null) {
            val msg = if (SolarManagerApi.isConfigured) "Keine Daten" else "API-Zugang fehlt"
            column.addContent(text(msg, 16f, Palette.HEATING_RED))
        } else {
            // Zeile: Schriftgrösse, fett, Abstand davor
            val styles = listOf(Triple(20f, true, 0f), Triple(16f, false, 6f), Triple(16f, false, 12f))
            var first = true
            settings.tile.rows.forEachIndexed { r, slots ->
                val metrics = slots.filterNotNull().filter { it.isAvailable(d) }
                if (metrics.isEmpty()) return@forEachIndexed
                val (size, bold, gap) = styles[r]
                if (!first) column.addContent(spacerH(gap))
                first = false
                val elems = mutableListOf<LayoutElement>()
                metrics.forEachIndexed { i, m ->
                    if (i > 0) elems += spacerW(if (metrics.size > 2) 10f else 12f)
                    elems += metricElement(m.view(d, todayWh, settings), size, bold, m == Metric.HP_STATE)
                }
                column.addContent(row(*elems.toTypedArray()))
            }
        }

        val openApp = ModifiersBuilders.Clickable.Builder()
            .setId("open_app")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(MainActivity::class.java.name)
                            .build(),
                    )
                    .build(),
            )
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(openApp).build())
            .addContent(column.build())
            .build()
    }

    private fun row(vararg elems: LayoutElement): LayoutElement {
        val r = LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        elems.forEach { r.addContent(it) }
        return r.build()
    }

    private fun metricElement(v: MetricView, size: Float, bold: Boolean, stateIcon: Boolean): LayoutElement {
        val elems = mutableListOf(icon(v.icon.name, v.tint, size + if (stateIcon) 6f else 2f))
        if (v.text.isNotEmpty()) {
            elems += spacerW(3f)
            elems += text(v.text, size, Palette.INK, bold)
        }
        v.suffix?.let { elems += text(it, size - 3f, Palette.MUTED) }
        return row(*elems.toTypedArray())
    }

    private fun icon(id: String, tint: Int, sizeDp: Float): LayoutElement =
        LayoutElementBuilders.Image.Builder()
            .setResourceId(id)
            .setWidth(dp(sizeDp))
            .setHeight(dp(sizeDp))
            .setColorFilter(
                LayoutElementBuilders.ColorFilter.Builder().setTint(argb(tint)).build(),
            )
            .build()

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): LayoutElement {
        val font = LayoutElementBuilders.FontStyle.Builder()
            .setSize(sp(size))
            .setColor(argb(color))
        if (bold) font.setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
        return LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setFontStyle(font.build())
            .build()
    }

    private fun spacerW(w: Float): LayoutElement =
        LayoutElementBuilders.Spacer.Builder().setWidth(dp(w)).build()

    private fun spacerH(h: Float): LayoutElement =
        LayoutElementBuilders.Spacer.Builder().setHeight(dp(h)).build()
}
