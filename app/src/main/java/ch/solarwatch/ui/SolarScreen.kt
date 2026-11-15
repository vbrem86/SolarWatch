package ch.solarwatch.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import ch.solarwatch.R
import ch.solarwatch.data.HEAT_PUMP_STATES
import ch.solarwatch.data.HeatPumpData
import ch.solarwatch.data.formatEnergy
import ch.solarwatch.data.formatPower
import ch.solarwatch.data.stateLabel

val SolarYellow = Color(0xFFFFC107)
val HouseBlue = Color(0xFF64B5F6)
val GridRed = Color(0xFFE57373)
val GridGreen = Color(0xFF81C784)
val BatteryTeal = Color(0xFF4DD0E1)
val HeatOrange = Color(0xFFFF8A65)
private val HeatingRed = Color(0xFFEF5350)

/**
 * Ein Bildschirm ohne Scrollen:
 *  oben   – aktuelle PV-Leistung, Verbrauch, PV-Produktion heute
 *  Mitte  – 24-h-Diagramm (PV, Verbrauch, Wassertemperatur)
 *  unten  – Aussentemperatur, Zustand Wärmepumpe, Wassertemperatur
 */
@Composable
fun SolarScreen(vm: SolarViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val hist by vm.history.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.startPolling() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.stopPolling() }

    MaterialTheme {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            val d = state.data
            val sidePad = maxWidth * 0.07f
            if (d == null) {
                Text(
                    text = state.error ?: "Lade Daten…",
                    color = if (state.error != null) GridRed else InkPrimary,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                return@BoxWithConstraints
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = maxHeight * 0.10f, bottom = maxHeight * 0.08f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ---------- oben: jetzt + heute
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconValue(R.drawable.ic_sunny, SolarYellow, formatPower(d.pvW), 17.sp, "PV-Leistung", bold = true)
                    IconValue(R.drawable.ic_home, HouseBlue, formatPower(d.consumptionW), 17.sp, "Verbrauch", bold = true)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconValue(
                        R.drawable.ic_today,
                        SolarYellow,
                        hist.history?.let { formatEnergy(it.todayPvWh) } ?: "–",
                        13.sp,
                        "PV-Produktion heute",
                    )
                    Text(" heute", fontSize = 11.sp, color = InkMuted)
                }

                Spacer(Modifier.height(4.dp))

                // ---------- Mitte: Diagramm
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = sidePad),
                    contentAlignment = Alignment.Center,
                ) {
                    val h = hist.history
                    if (h != null) {
                        CombinedChart(h, Modifier.fillMaxSize())
                    } else {
                        Text(
                            hist.error ?: "Lade Verlauf…",
                            fontSize = 12.sp,
                            color = if (hist.error != null) GridRed else InkMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // ---------- unten: Wärmepumpe
                d.heatPump?.let { hp ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconValue(R.drawable.ic_device_thermostat, InkPrimary, temp(hp.outsideTempC), 15.sp, "Aussentemperatur")
                        HeatPumpStateIcon(hp)
                        IconValue(R.drawable.ic_water_drop, HeatOrange, temp(hp.waterTempC), 15.sp, "Wassertemperatur", bold = true)
                    }
                }
                if (state.error != null) {
                    Text("⚠ offline", fontSize = 10.sp, color = GridRed)
                }
            }
        }
    }
}

private fun temp(c: Double?): String = c?.let { "%.1f°".format(it) } ?: "–"

@Composable
private fun IconValue(
    @DrawableRes icon: Int,
    tint: Color,
    value: String,
    size: TextUnit,
    description: String,
    bold: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size((size.value + 3).dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            value,
            fontSize = size,
            color = InkPrimary,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** Zustand als farbiges Symbol: Heizen = Flamme, Warmwasser = Dusche, sonst Wärmepumpe + Code. */
@Composable
private fun HeatPumpStateIcon(hp: HeatPumpData) {
    val (icon, tint) = when (hp.operationState) {
        2 -> R.drawable.ic_local_fire_department to HeatingRed
        3 -> R.drawable.ic_shower to HeatOrange
        else -> R.drawable.ic_heat_pump to InkMuted
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(icon),
            contentDescription = "Wärmepumpe: ${hp.stateLabel()}",
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        // Unbekannter Zustand: Code anzeigen, damit wir ihn zuordnen können
        if (hp.operationState != null && hp.operationState !in HEAT_PUMP_STATES) {
            Text("${hp.operationState}", fontSize = 11.sp, color = InkMuted)
        }
    }
}
