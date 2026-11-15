package ch.solarwatch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import ch.solarwatch.data.History
import ch.solarwatch.data.formatPower
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** Text-Farben (Text nie in Serienfarbe) */
val InkPrimary = Color(0xFFEEEEEE)
val InkMuted = Color(0xFF9E9E9E)
private val GridColor = Color(0xFF2E2E2E)

/**
 * 24-h-Diagramm mit PV, Verbrauch (linke Skala, kW) und Wassertemperatur
 * (rechte Skala, °C). Die Temperatur hat bewusst eine eigene Skala –
 * Schnittpunkte mit den Leistungslinien haben keine Bedeutung.
 *
 * Kopfzeile: Legende; beim Antippen/Wischen die Werte zum gewählten Zeitpunkt.
 */
@Composable
fun CombinedChart(h: History, modifier: Modifier = Modifier) {
    val pv = h.pvW.points
    val cons = h.consumptionW.points
    val water = h.waterTempC?.points.orEmpty()

    // Leistung: 0 .. runder Maximalwert
    val pMax = niceCeil((pv + cons).maxOfOrNull { it.second } ?: 1000.0)
    // Temperatur: eng um die Daten, auf 5 °C gerundet
    val tMin = water.minOfOrNull { it.second }?.let { floor(it / 5) * 5 } ?: 0.0
    val tMax = water.maxOfOrNull { it.second }?.let { (ceil(it / 5) * 5).coerceAtLeast(tMin + 5) } ?: 1.0

    var scrubX by remember { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableStateOf(1f) }
    val textMeasurer = rememberTextMeasurer()
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.GERMANY) }

    val scrubT = scrubX?.let { x -> h.fromMillis + ((x / widthPx).coerceIn(0f, 1f) * (h.toMillis - h.fromMillis)).toLong() }
    fun nearest(s: List<Pair<Long, Double>>) = scrubT?.let { t -> s.minByOrNull { abs(it.first - t) } }
    val sPv = nearest(pv)
    val sCons = nearest(cons)
    val sWater = nearest(water)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Kopfzeile mit fester Höhe, damit nichts springt
        Row(
            modifier = Modifier.height(16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (scrubT != null) {
                Text(timeFmt.format(Date(sPv?.first ?: scrubT)), fontSize = 11.sp, color = InkMuted)
                LegendItem(SolarYellow, sPv?.let { formatPower(it.second) } ?: "–")
                LegendItem(HouseBlue, sCons?.let { formatPower(it.second) } ?: "–")
                if (water.isNotEmpty()) LegendItem(HeatOrange, sWater?.let { "%.0f°".format(it.second) } ?: "–")
            } else {
                LegendItem(SolarYellow, "PV")
                LegendItem(HouseBlue, "Verbr.")
                if (water.isNotEmpty()) LegendItem(HeatOrange, "Wasser")
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { scrubX = if (scrubX == null) it.x else null })
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragStart = { scrubX = it.x }) { change, _ ->
                        scrubX = change.position.x
                    }
                },
        ) {
            widthPx = size.width
            fun x(t: Long) = ((t - h.fromMillis).toFloat() / (h.toMillis - h.fromMillis)) * size.width
            fun yP(v: Double) = size.height - (v / pMax).toFloat() * size.height
            fun yT(v: Double) = size.height - ((v - tMin) / (tMax - tMin)).toFloat() * size.height

            // Raster
            listOf(0f, 0.5f, 1f).forEach { f ->
                val y = size.height * f
                drawLine(GridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }

            val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawSeries(pv, ::x, ::yP, SolarYellow, stroke)
            drawSeries(cons, ::x, ::yP, HouseBlue, stroke)
            drawSeries(water, ::x, ::yT, HeatOrange, stroke)

            // Skalen: links Leistung, rechts Temperatur (nach den Linien, mit Hintergrund)
            val style = TextStyle(fontSize = 9.sp, color = InkMuted)
            label(textMeasurer, formatPower(pMax), style, left = true, y = 0f)
            if (water.isNotEmpty()) {
                label(textMeasurer, "%.0f°".format(tMax), style, left = false, y = 0f)
                label(textMeasurer, "%.0f°".format(tMin), style, left = false, y = size.height, bottom = true)
            }

            // Fadenkreuz
            scrubT?.let {
                val cx = x(sPv?.first ?: it)
                drawLine(InkMuted, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1.dp.toPx())
                sPv?.let { p -> dot(Offset(x(p.first), yP(p.second)), SolarYellow) }
                sCons?.let { p -> dot(Offset(x(p.first), yP(p.second)), HouseBlue) }
                sWater?.let { p -> dot(Offset(x(p.first), yT(p.second)), HeatOrange) }
            }
        }

        // Zeitachse etwas eingerückt, damit der runde Rand nichts abschneidet
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val hours = ((h.toMillis - h.fromMillis) / 3_600_000).toInt()
            Text("−${hours}h", fontSize = 9.sp, color = InkMuted)
            Text("−${hours / 2}h", fontSize = 9.sp, color = InkMuted)
            Text("jetzt", fontSize = 9.sp, color = InkMuted)
        }
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(3.dp))
        Text(text, fontSize = 11.sp, color = InkPrimary, maxLines = 1)
    }
}

private fun DrawScope.drawSeries(
    pts: List<Pair<Long, Double>>,
    x: (Long) -> Float,
    y: (Double) -> Float,
    color: Color,
    stroke: Stroke,
) {
    if (pts.isEmpty()) return
    val path = Path()
    var prev: Long? = null
    pts.sortedBy { it.first }.forEach { (t, v) ->
        val gap = prev?.let { t - it > 45 * 60_000 } ?: true // Lücken > 45 min nicht verbinden
        if (gap) path.moveTo(x(t), y(v)) else path.lineTo(x(t), y(v))
        prev = t
    }
    drawPath(path, color, style = stroke)
}

private fun DrawScope.label(
    tm: TextMeasurer,
    text: String,
    style: TextStyle,
    left: Boolean,
    y: Float,
    bottom: Boolean = false,
) {
    val layout = tm.measure(text, style)
    val w = layout.size.width.toFloat()
    val hgt = layout.size.height.toFloat()
    val top = if (bottom) y - hgt - 1f else y + 1f
    val lx = if (left) 0f else size.width - w - 4f
    drawRect(Color.Black.copy(alpha = 0.75f), Offset(lx, top), Size(w + 4f, hgt))
    drawText(layout, topLeft = Offset(lx + 2f, top))
}

private fun DrawScope.dot(c: Offset, color: Color) {
    drawCircle(Color.Black, radius = 4.5.dp.toPx(), center = c) // Ring zur Abgrenzung
    drawCircle(color, radius = 3.5.dp.toPx(), center = c)
}

/** Aufrunden auf 1, 1.5, 2, 2.5, 3, 4, 5, 6, 8 × 10^n */
private fun niceCeil(v: Double): Double {
    if (v <= 0) return 1000.0
    val base = Math.pow(10.0, floor(kotlin.math.log10(v)))
    val f = v / base
    val nice = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0).first { f <= it }
    return nice * base
}

