package ch.solarwatch.complication

import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import ch.solarwatch.data.Metric
import ch.solarwatch.data.SettingsStore
import ch.solarwatch.data.SolarManagerApi
import ch.solarwatch.data.shortText

/**
 * Zifferblatt-Complication: der in den Einstellungen gewählte Wert,
 * z.B. "PV 2.4k", "Akku 85%", "Wasser 52°".
 * (Klassenname bleibt aus Kompatibilität mit bestehenden Zifferblättern.)
 */
class PvComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        val m = SettingsStore.display.value.complication
        val sample = when (m) {
            Metric.BATTERY_SOC -> "85%"
            Metric.OUTSIDE_TEMP -> "8°"
            Metric.WATER_TEMP -> "52°"
            Metric.HP_STATE -> "Heizen"
            Metric.TODAY_PV -> "12.4k"
            else -> "2.4k"
        }
        return shortText(m, sample)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return null
        val m = SettingsStore.display.value.complication
        val data = runCatching { SolarManagerApi.fetchStream() }.getOrNull() ?: SolarManagerApi.lastData
        val today = if (m == Metric.TODAY_PV) runCatching { SolarManagerApi.todayPvWh() }.getOrNull() else null
        return shortText(m, m.shortText(data, today))
    }

    private fun shortText(m: Metric, value: String): ComplicationData {
        val icon = SettingsStore.display.value.iconFor(m)
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(value).build(),
            contentDescription = PlainComplicationText.Builder("${m.label} $value").build(),
        )
            .setTitle(PlainComplicationText.Builder(m.short).build())
            .setMonochromaticImage(
                MonochromaticImage.Builder(Icon.createWithResource(this, icon.res)).build(),
            )
            .build()
    }
}
