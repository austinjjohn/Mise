package app.mise.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.mise.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ImportProgress(val done: Int = 0, val total: Int = 0, val failed: List<String> = emptyList(), val running: Boolean = false)

sealed interface DetailsState {
    data object Idle : DetailsState
    data object Loading : DetailsState
    data class Loaded(val details: PlaceDetails) : DetailsState
    data class Error(val message: String) : DetailsState
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    private val repo = PlacesRepository(app)

    val places: StateFlow<List<SavedPlace>> =
        dao.observePlaces().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedId = MutableStateFlow<Long?>(null)
    val selected: StateFlow<SavedPlace?> =
        combine(places, selectedId) { list, id -> list.firstOrNull { it.id == id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val notes: StateFlow<List<DishNote>> =
        selectedId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.observeNotes(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _details = MutableStateFlow<DetailsState>(DetailsState.Idle)
    val details: StateFlow<DetailsState> = _details
    private val detailsCache = HashMap<String, PlaceDetails>()

    private val _import = MutableStateFlow(ImportProgress())
    val import: StateFlow<ImportProgress> = _import

    fun select(place: SavedPlace?) {
        selectedId.value = place?.id
        _details.value = DetailsState.Idle
        val gid = place?.googlePlaceId ?: return
        detailsCache[gid]?.let { _details.value = DetailsState.Loaded(it); return }
        _details.value = DetailsState.Loading
        viewModelScope.launch {
            _details.value = runCatching { repo.details(gid) }.fold(
                onSuccess = { detailsCache[gid] = it; DetailsState.Loaded(it) },
                onFailure = { DetailsState.Error(it.message ?: "Couldn't load place info") },
            )
        }
    }

    fun addNote(text: String) {
        val id = selectedId.value ?: return
        if (text.isBlank()) return
        viewModelScope.launch { dao.insertNote(DishNote(placeId = id, text = text.trim())) }
    }
    fun toggleNote(n: DishNote) = viewModelScope.launch { dao.updateNote(n.copy(tried = !n.tried)) }
    fun deleteNote(n: DishNote) = viewModelScope.launch { dao.deleteNote(n) }

    fun deleteSelected() {
        val p = selected.value ?: return
        select(null)
        viewModelScope.launch { dao.delete(p) }
    }

    /** Lines like "Name, City", "Name - City", "Name | City", or just "Name" (uses defaultCity). */
    fun importList(raw: String, defaultCity: String) {
        if (_import.value.running) return
        val entries = raw.lines().map { it.trim().trimStart('-', '*', '•', ' ') }.filter { it.isNotEmpty() }.map { line ->
            val parts = line.split(" - ", "|", ",").map { it.trim() }.filter { it.isNotEmpty() }
            parts.first() to (parts.drop(1).joinToString(" ").ifBlank { defaultCity })
        }
        viewModelScope.launch {
            val failed = mutableListOf<String>()
            _import.value = ImportProgress(total = entries.size, running = true)
            entries.forEachIndexed { i, (name, city) ->
                if (dao.countDuplicates(name, city) == 0) {
                    val match = runCatching { repo.match(name, city) }.getOrNull()
                    if (match == null) failed += "$name ($city)"
                    dao.insert(
                        SavedPlace(
                            name = name, city = city,
                            googlePlaceId = match?.placeId,
                            lat = match?.latLng?.latitude, lng = match?.latLng?.longitude,
                            address = match?.address,
                        )
                    )
                }
                _import.value = ImportProgress(i + 1, entries.size, failed.toList(), running = true)
            }
            _import.value = _import.value.copy(running = false)
        }
    }

    /** Retry Places matching for entries that failed earlier (e.g. no network at import time). */
    fun retryUnmatched() {
        viewModelScope.launch {
            dao.unmatched().forEach { p ->
                val m = runCatching { repo.match(p.name, p.city) }.getOrNull() ?: return@forEach
                dao.update(p.copy(googlePlaceId = m.placeId, lat = m.latLng.latitude, lng = m.latLng.longitude, address = m.address))
            }
        }
    }
}
