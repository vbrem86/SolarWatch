package ch.solarwatch.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.tiles.TileService
import ch.solarwatch.data.History
import ch.solarwatch.data.SolarData
import ch.solarwatch.data.SolarManagerApi
import ch.solarwatch.tile.SolarTileService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class UiState(
    val data: SolarData? = SolarManagerApi.lastData,
    val loading: Boolean = false,
    val error: String? = null,
)

data class HistoryState(
    val history: History? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class SolarViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _history = MutableStateFlow(HistoryState())
    val history: StateFlow<HistoryState> = _history.asStateFlow()

    private var pollJob: Job? = null

    /** Lädt den 24-h-Verlauf; Daten haben 15-min-Auflösung, daher max. alle 5 min neu. */
    fun loadHistory(force: Boolean = false) {
        val cur = _history.value
        if (cur.loading) return
        val fresh = cur.history?.let { System.currentTimeMillis() - it.fetchedAtMillis < 5 * 60_000 } == true
        if (fresh && !force) return
        _history.value = cur.copy(loading = true, error = null)
        viewModelScope.launch {
            _history.value = try {
                HistoryState(history = SolarManagerApi.fetchHistory())
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

    fun refreshNow() {
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        _state.value = _state.value.copy(loading = true)
        _state.value = try {
            val data = SolarManagerApi.fetchStream()
            // Tile gleich mit aktualisieren
            TileService.getUpdater(getApplication()).requestUpdate(SolarTileService::class.java)
            UiState(data = data, loading = false, error = null)
        } catch (e: Exception) {
            _state.value.copy(loading = false, error = e.message ?: e.javaClass.simpleName)
        }
    }
}
