package ch.solarwatch.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import ch.solarwatch.data.SolarManagerApi
import ch.solarwatch.data.formatPowerShort

/** Zifferblatt-Complication: aktuelle PV-Leistung, z.B. "PV 2.4k". */
class PvComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.SHORT_TEXT) shortText("2.4k") else null

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return null
        val data = runCatching { SolarManagerApi.fetchStream() }.getOrNull()
            ?: SolarManagerApi.lastData
        return shortText(data?.let { formatPowerShort(it.pvW) } ?: "--")
    }

    private fun shortText(value: String) = ShortTextComplicationData.Builder(
        text = PlainComplicationText.Builder(value).build(),
        contentDescription = PlainComplicationText.Builder("PV-Leistung $value Watt").build(),
    )
        .setTitle(PlainComplicationText.Builder("PV").build())
        .build()
}
