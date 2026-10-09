package app.mise.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.mise.data.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ImportProgress(
    val done: Int = 0,
    val total: Int = 0,
    val added: Int = 0,
    val failed: List<String> = emptyList(),
    /** Entries skipped because the place is already on the list, e.g. "Bon Fresco sandwiches (same as Bon Fresco)". */
    val skipped: List<String> = emptyList(),
    val running: Boolean = false,
)

sealed interface AddResult {
    /** [onMap] is false when Google couldn't find the place, so it was saved without a pin. */
    data class Added(val onMap: Boolean) : AddResult
    data class Duplicate(val existing: SavedPlace) : AddResult
}

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

    /** Every dish bullet by place, so search can match dishes without opening each place. */
    val dishesByPlace: StateFlow<Map<Long, List<String>>> =
        dao.observeAllDishes().map { all -> all.groupBy({ it.placeId }, { it.text }) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        viewModelScope.launch { backfillCuisines() }
    }

    /**
     * Fills in Google's real name and, where blank, the cuisine for places saved earlier. Each place is
     * handled once; network failures are retried next launch.
     */
    private suspend fun backfillCuisines() {
        // Every database write re-emits the whole list and redraws the map, so write in batches, not one by one.
        val pending = ArrayList<SavedPlace>()
        for (p in dao.needsGoogleData()) {
            val result = runCatching { repo.nameAndCuisine(p.googlePlaceId!!) }
            val (name, cuisine) = result.getOrNull() ?: continue
            pending += p.copy(
                googleName = name?.takeIf { it.isNotBlank() } ?: p.name,
                cuisine = p.cuisine ?: cuisine.takeIf { !p.typeChecked },
                typeChecked = true,
            )
            if (pending.size >= 25) { dao.updateAll(pending.toList()); pending.clear() }
            delay(100)
        }
        if (pending.isNotEmpty()) dao.updateAll(pending.toList())
    }

    /** Last known user location, used to bias autocomplete in the Add place form. */
    var nearHint: LatLng? = null

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

    private val prefs = app.getSharedPreferences("mise", android.content.Context.MODE_PRIVATE)
    private val _radius = MutableStateFlow(prefs.getInt("radius_miles", 10))
    val radiusMiles: StateFlow<Int> = _radius
    fun setRadius(miles: Int) {
        _radius.value = miles
        prefs.edit().putInt("radius_miles", miles).apply()
    }

    private val _progress = MutableStateFlow(ImportProgress())
    val progress: StateFlow<ImportProgress> = _progress

    private val _review = MutableStateFlow<List<ParsedEntry>>(emptyList())
    val review: StateFlow<List<ParsedEntry>> = _review

    // --- selection / details -------------------------------------------------

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

    fun addDish(text: String) {
        val id = selectedId.value ?: return
        if (text.isBlank()) return
        viewModelScope.launch { dao.insertNote(DishNote(placeId = id, text = text.trim())) }
    }
    fun toggleDish(n: DishNote) = viewModelScope.launch { dao.updateNote(n.copy(tried = !n.tried)) }
    fun deleteDish(n: DishNote) = viewModelScope.launch { dao.deleteNote(n) }

    fun saveNotes(text: String) = updateSelected { it.copy(notes = text.trim().ifBlank { null }) }
    fun saveCuisine(text: String) = updateSelected { it.copy(cuisine = text.trim().ifBlank { null }) }
    private fun updateSelected(change: (SavedPlace) -> SavedPlace) {
        val p = selected.value ?: return
        viewModelScope.launch { dao.update(change(p)) }
    }

    fun deleteSelected() {
        val p = selected.value ?: return
        select(null)
        viewModelScope.launch { dao.delete(p) }
    }

    // --- location search ---------------------------------------------------

    suspend fun suggest(query: String, near: LatLng?): List<Suggestion> =
        runCatching { repo.suggest(query, near) }.getOrDefault(emptyList())

    suspend fun locate(placeId: String): Pair<String, LatLng>? = runCatching { repo.locate(placeId) }.getOrNull()

    // --- adding places -------------------------------------------------------

    /** Autocomplete for the Add place form: businesses only, biased toward where the user is. */
    suspend fun suggestPlaces(query: String): List<Suggestion> =
        runCatching { repo.suggest(query, nearHint, businessesOnly = true) }.getOrDefault(emptyList())

    /** Full details (real name, address, cuisine) of a suggestion the user picked. */
    suspend fun resolvePlace(placeId: String): PlaceMatch? = runCatching { repo.placeInfo(placeId) }.getOrNull()

    /** The saved place that [placeId] already is, if any. */
    suspend fun existingFor(placeId: String): SavedPlace? = dao.findByGoogleId(placeId)

    /**
     * Add form. [picked] is set when the user chose an autocomplete suggestion; otherwise [text] (name and
     * location in one string, like a Google Maps search) is looked up on Google.
     */
    fun addPlace(text: String, picked: PlaceMatch?, pickedCity: String?, cuisine: String, dishes: String, notes: String, onDone: (AddResult) -> Unit) {
        viewModelScope.launch {
            val result = if (picked != null) {
                savePlace(picked.name ?: text.trim(), pickedCity.orEmpty(), cuisine, ListParser.splitDishes(dishes), notes, known = picked)
            } else {
                savePlace(text.trim(), "", cuisine, ListParser.splitDishes(dishes), notes)
            }
            onDone(result)
        }
    }

    /** Looks the place up on Google, refuses duplicates (by Google id, or name + city), and saves it. */
    private suspend fun savePlace(
        name: String, location: String, cuisine: String?, dishes: List<String>, notes: String?, known: PlaceMatch? = null,
    ): AddResult {
        val match = known ?: runCatching { repo.match(name, location) }.getOrNull()
        val existing = match?.placeId?.let { dao.findByGoogleId(it) } ?: dao.findByNameAndCity(name, location)
        if (existing != null) return AddResult.Duplicate(existing)
        val id = dao.insert(
            SavedPlace(
                name = name, city = location,
                googlePlaceId = match?.placeId,
                lat = match?.latLng?.latitude, lng = match?.latLng?.longitude,
                address = match?.address,
                cuisine = cuisine?.trim()?.ifBlank { null } ?: match?.cuisine,
                typeChecked = match != null,
                googleName = match?.name,
                notes = notes?.trim()?.ifBlank { null },
            )
        )
        dishes.forEach { dao.insertNote(DishNote(placeId = id, text = it)) }
        return AddResult.Added(onMap = match != null)
    }

    // --- bulk import: parse -> review -> commit ------------------------------

    fun parseForReview(raw: String, defaultLocation: String) {
        _progress.value = ImportProgress()
        _review.value = ListParser.parse(raw, defaultLocation.trim())
    }

    fun updateEntry(id: Int, change: (ParsedEntry) -> ParsedEntry) {
        _review.update { list -> list.map { if (it.id == id) change(it) else it } }
    }

    fun removeEntry(id: Int) = _review.update { list -> list.filterNot { it.id == id } }

    fun commitReview() {
        if (_progress.value.running) return
        val entries = _review.value.filter { it.name.isNotBlank() }
        viewModelScope.launch {
            val failed = mutableListOf<String>()
            val skipped = mutableListOf<String>()
            var added = 0
            _progress.value = ImportProgress(total = entries.size, running = true)
            entries.forEachIndexed { i, e ->
                val text = e.text.trim()
                val result = savePlace(
                    name = e.name.trim(), location = e.location.trim(),
                    cuisine = text.takeIf { e.kind == EntryKind.Cuisine },
                    dishes = if (e.kind == EntryKind.Dish) ListParser.splitDishes(text) else emptyList(),
                    notes = text.takeIf { e.kind == EntryKind.Note },
                )
                when (result) {
                    is AddResult.Duplicate -> skipped += "${e.name} (already on your list as ${result.existing.displayName})"
                    is AddResult.Added -> { added++; if (!result.onMap) failed += "${e.name} (${e.location})" }
                }
                _progress.value = ImportProgress(i + 1, entries.size, added, failed.toList(), skipped.toList(), running = true)
            }
            _progress.value = _progress.value.copy(running = false)
            _review.value = emptyList()
        }
    }

    /** Retry Places matching for entries that failed earlier (e.g. no network at import time). */
    fun retryUnmatched() {
        viewModelScope.launch {
            dao.unmatched().forEach { p ->
                val m = runCatching { repo.match(p.name, p.city) }.getOrNull() ?: return@forEach
                dao.update(p.copy(googlePlaceId = m.placeId, lat = m.latLng.latitude, lng = m.latLng.longitude, address = m.address, cuisine = p.cuisine ?: m.cuisine, typeChecked = true, googleName = m.name))
            }
        }
    }
}
