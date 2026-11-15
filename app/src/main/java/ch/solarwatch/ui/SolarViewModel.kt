package ch.solarwatch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.tiles.TileService
import ch.solarwatch.data.ChartSeries
import ch.solarwatch.data.History
import ch.solarwatch.data.SettingsStore
import ch.solarwatch.data.SolarData
import ch.solarwatch.data.SolarManagerApi
import ch.solarwatch.tile.SolarTileService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class UiState(
    val data: SolarData? = SolarManagerApi.lastData,
    val loading: Boolean = false,
    val error: String? = null,
)

data class HistoryState(
    val history: History? = SolarManagerApi.lastHistory,
    val loading: Boolean = false,
    val error: String? = null,
)

class SolarViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _history = MutableStateFlow(HistoryState())
    val history: StateFlow<HistoryState> = _history.asStateFlow()

    private var pollJob: Job? = null
    private var historyJob: Job? = null

    init {
        // Zeitraum / Wasserkurve geändert -> Verlauf neu laden
        viewModelScope.launch {
            SettingsStore.display
                .map { it.chartHours to needsWater() }
                .distinctUntilChanged()
                .drop(1)
                .collect { loadHistory(force = true) }
        }
        // Neue Zugangsdaten -> alles neu laden
        viewModelScope.launch {
            SettingsStore.credentials.drop(1).collect {
                _state.value = UiState(data = null)
                _history.value = HistoryState(history = null)
                refresh()
                loadHistory(force = true)
            }
        }
    }

    private fun needsWater(): Boolean {
        val s = SettingsStore.display.value
        return ChartSeries.WATER in s.chartSeries
    }

    /** Lädt den Verlauf; Daten haben 15-min-Auflösung, daher max. alle 5 min neu. */
    fun loadHistory(force: Boolean = false) {
        val cur = _history.value
        if (cur.loading && !force) return
        val hours = SettingsStore.display.value.chartHours.toLong()
        val h = cur.history
        val fresh = h != null &&
            System.currentTimeMillis() - h.fetchedAtMillis < 5 * 60_000 &&
            (h.toMillis - h.fromMillis) / 3_600_000 == hours
        if (fresh && !force) return
        historyJob?.cancel()
        _history.value = cur.copy(loading = true, error = null)
        historyJob = viewModelScope.launch {
            _history.value = try {
                HistoryState(history = SolarManagerApi.fetchHistory(hours, includeWater = needsWater()))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _history.value.copy(loading = false, error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Pollt alle 10 s, solange die App sichtbar ist (Stream-Intervall des Solar Managers). */
    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                refresh()
                loadHistory() // lädt selbst höchstens alle 5 min neu
                delay(10_000)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun refresh() {
        _state.value = _state.value.copy(loading = true)
        _state.value = try {
            val data = SolarManagerApi.fetchStream()
            // Tile gleich mit aktualisieren
            TileService.getUpdater(getApplication()).requestUpdate(SolarTileService::class.java)
            UiState(data = data, loading = false, error = null)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value.copy(loading = false, error = e.message ?: e.javaClass.simpleName)
        }
    }
}
