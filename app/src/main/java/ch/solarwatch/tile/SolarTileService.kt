package ch.solarwatch.tile

import androidx.annotation.DrawableRes
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
import ch.solarwatch.R
import ch.solarwatch.data.SolarData
import ch.solarwatch.data.SolarManagerApi
import ch.solarwatch.data.formatEnergy
import ch.solarwatch.data.formatPower
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import java.util.Locale

private const val RESOURCES_VERSION = "2"

// Farben wie in der App
private const val YELLOW = 0xFFFFC107.toInt()
private const val BLUE = 0xFF64B5F6.toInt()
private const val ORANGE = 0xFFFF8A65.toInt()
private const val RED = 0xFFEF5350.toInt()
private const val INK = 0xFFEEEEEE.toInt()
private const val MUTED = 0xFF9E9E9E.toInt()

/** Symbol-IDs -> Drawables */
private val ICONS: Map<String, Int> = mapOf(
    "sun" to R.drawable.ic_sunny,
    "home" to R.drawable.ic_home,
    "today" to R.drawable.ic_today,
    "thermo" to R.drawable.ic_device_thermostat,
    "drop" to R.drawable.ic_water_drop,
    "fire" to R.drawable.ic_local_fire_department,
    "shower" to R.drawable.ic_shower,
    "heatpump" to R.drawable.ic_heat_pump,
)

/**
 * Tile:
 *   ☀ PV jetzt   ⌂ Verbrauch jetzt
 *   📅 Produktion heute
 *   🌡 Aussen   [Zustand WP]   💧 Warmwasser
 * Tippen öffnet die App.
 */
class SolarTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> = scope.future {
        val data = runCatching { SolarManagerApi.fetchStream() }.getOrNull() ?: SolarManagerApi.lastData
        val today = runCatching { SolarManagerApi.todayPvWh() }.getOrNull()

        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(5 * 60 * 1000L)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(data, today)))
            .build()
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> = scope.future {
        val res = ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION)
        ICONS.forEach { (id, drawable) ->
            res.addIdToImageMapping(
                id,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(drawable)
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

    private fun layout(d: SolarData?, todayWh: Double?): LayoutElement {
        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

        if (d == null) {
            column.addContent(text("Keine Daten", 16f, RED))
        } else {
            // Jetzt: Produktion + Verbrauch
            column.addContent(
                row(
                    iconValue("sun", YELLOW, formatPower(d.pvW), 20f, bold = true),
                    spacerW(12f),
                    iconValue("home", BLUE, formatPower(d.consumptionW), 20f, bold = true),
                ),
            )
            column.addContent(spacerH(6f))
            // Heute
            column.addContent(
                row(
                    iconValue("today", YELLOW, todayWh?.let { formatEnergy(it) } ?: "–", 16f),
                    text(" heute", 13f, MUTED),
                ),
            )
            // Wärmepumpe
            d.heatPump?.let { hp ->
                column.addContent(spacerH(12f))
                val (stateIcon, stateColor) = when (hp.operationState) {
                    2 -> "fire" to RED
                    3 -> "shower" to ORANGE
                    else -> "heatpump" to MUTED
                }
                val stateElems = mutableListOf<LayoutElement>(icon(stateIcon, stateColor, 22f))
                if (hp.operationState != null && hp.operationState !in setOf(2, 3)) {
                    stateElems += text("${hp.operationState}", 12f, MUTED)
                }
                column.addContent(
                    row(
                        iconValue("thermo", INK, temp(hp.outsideTempC), 16f),
                        spacerW(10f),
                        *stateElems.toTypedArray(),
                        spacerW(10f),
                        iconValue("drop", ORANGE, temp(hp.waterTempC), 16f, bold = true),
                    ),
                )
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

    private fun temp(c: Double?): String = c?.let { String.format(Locale.US, "%.1f°", it) } ?: "–"

    private fun row(vararg elems: LayoutElement): LayoutElement {
        val r = LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        elems.forEach { r.addContent(it) }
        return r.build()
    }

    private fun iconValue(iconId: String, tint: Int, value: String, size: Float, bold: Boolean = false): LayoutElement =
        row(icon(iconId, tint, size + 2f), spacerW(3f), text(value, size, INK, bold))

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
