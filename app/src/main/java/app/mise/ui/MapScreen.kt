package app.mise.ui

import android.Manifest
import android.annotation.SuppressLint
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mise.data.SavedPlace
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val RADIUS_OPTIONS = listOf(1, 2, 5, 10, 15, 25, 50)
private const val METERS_PER_MILE = 1609.34f

/** A location picked from search; the nearby list centers on it. */
private data class Area(val name: String, val latLng: LatLng)

private data class SuggestionRow(val title: String, val subtitle: String?, val saved: Boolean, val onClick: () -> Unit)

@SuppressLint("MissingPermission") // guarded by hasLocation
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MapScreen(vm: MainViewModel, onOpenAdd: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val places by vm.places.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val details by vm.details.collectAsStateWithLifecycle()
    val radiusMiles by vm.radiusMiles.collectAsStateWithLifecycle()

    var hasLocation by remember { mutableStateOf(false) }
    var userLoc by remember { mutableStateOf<LatLng?>(null) }
    var area by remember { mutableStateOf<Area?>(null) }
    var showDetails by rememberSaveable { mutableStateOf(false) }

    var query by rememberSaveable { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    var predictions by remember { mutableStateOf<List<app.mise.data.Suggestion>>(emptyList()) }

    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded, skipHiddenState = true)
    val scaffoldState = rememberBottomSheetScaffoldState(sheetState)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val peek = screenHeight * 0.4f

    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(39.5, -98.35), 4f) }

    suspend fun fetchLocation(): LatLng? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val loc = runCatching { client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await() }.getOrNull()
            ?: runCatching { client.lastLocation.await() }.getOrNull()
        return loc?.let { LatLng(it.latitude, it.longitude) }?.also { userLoc = it }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasLocation = it[Manifest.permission.ACCESS_FINE_LOCATION] == true || it[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    fun askForLocation() = permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    LaunchedEffect(Unit) { askForLocation() }
    LaunchedEffect(hasLocation) {
        if (hasLocation) fetchLocation()?.let { camera.animate(CameraUpdateFactory.newLatLngZoom(it, 12f), 800) }
    }

    fun flyTo(ll: LatLng, zoom: Float) {
        scope.launch { camera.animate(CameraUpdateFactory.newLatLngZoom(ll, zoom), 600) }
    }

    fun pick(p: SavedPlace) {
        vm.select(p)
        showDetails = false
        scope.launch { sheetState.partialExpand() }
        val lat = p.lat ?: return
        val lng = p.lng ?: return
        flyTo(LatLng(lat, lng), 15f)
    }

    fun clearSearch() {
        query = ""; typing = false; predictions = emptyList(); area = null
        focusManager.clearFocus()
    }

    // Autocomplete: wait for a pause in typing, bias results toward where the user is looking.
    LaunchedEffect(query, typing) {
        if (!typing || query.trim().length < 2) { predictions = emptyList(); return@LaunchedEffect }
        delay(250)
        predictions = vm.suggest(query.trim(), area?.latLng ?: userLoc)
    }

    val suggestionRows: List<SuggestionRow> = if (!typing) emptyList() else {
        val mine = places.filter { it.name.contains(query.trim(), ignoreCase = true) }.take(3).map { p ->
            SuggestionRow(p.name, listOfNotNull(p.cuisine, p.city.ifBlank { null }).joinToString(" · "), saved = true) {
                typing = false; query = p.name; focusManager.clearFocus(); area = null; pick(p)
            }
        }
        val google = predictions.map { s ->
            SuggestionRow(s.primary, s.secondary, saved = false) {
                typing = false; query = s.primary; predictions = emptyList(); focusManager.clearFocus()
                scope.launch {
                    val (name, ll) = vm.locate(s.placeId) ?: return@launch
                    vm.select(null)
                    area = Area(name.ifBlank { s.primary }, ll)
                    flyTo(ll, 12f)
                }
            }
        }
        mine + google
    }

    // What the nearby list is centered on: the selected restaurant, else the searched area, else you.
    val sel = selected
    val selLatLng = sel?.let { p -> p.lat?.let { LatLng(it, p.lng!!) } }
    val center = selLatLng ?: area?.latLng ?: userLoc
    val title = when {
        sel != null -> "Also near ${sel.name}"
        area != null -> "Near ${area!!.name}"
        else -> "Near you"
    }
    val rows = remember(places, center, sel?.id, radiusMiles) {
        if (center == null) emptyList() else places
            .filter { it.lat != null && it.lng != null && it.id != sel?.id }
            .map { it to meters(center, it) }
            .filter { it.second <= radiusMiles * METERS_PER_MILE }
            .sortedBy { it.second }
    }

    val properties = remember(hasLocation) { MapProperties(isMyLocationEnabled = hasLocation) }
    val uiSettings = remember {
        MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false, mapToolbarEnabled = false)
    }
    val unmatched = places.count { it.lat == null }

    val rating = (details as? DetailsState.Loaded)?.details?.let { d -> d.rating?.let { it to (d.ratingCount ?: 0) } }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = peek,
        sheetShape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        sheetContent = {
            NearbyPanel(
                maxListHeight = screenHeight,
                title = title, focus = sel, rating = rating, hasCenter = center != null, rows = rows,
                radiusMiles = radiusMiles, onRadius = vm::setRadius,
                onPick = ::pick, onOpenDetails = { showDetails = true }, onClearFocus = { vm.select(null) },
            )
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = camera,
                properties = properties,
                uiSettings = uiSettings,
                contentPadding = PaddingValues(bottom = peek),
                onMapClick = { vm.select(null); focusManager.clearFocus(); typing = false },
            ) {
                places.forEach { p ->
                    val lat = p.lat ?: return@forEach
                    val lng = p.lng ?: return@forEach
                    key(p.id) {
                        Marker(state = remember { MarkerState(LatLng(lat, lng)) }, title = p.name, onClick = { pick(p); true })
                    }
                }
            }

            Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                LocationSearchField(query, onQuery = { query = it; typing = true }, onClear = ::clearSearch)
                if (suggestionRows.isNotEmpty()) {
                    Surface(
                        Modifier.padding(top = 6.dp), shape = RoundedCornerShape(24.dp), shadowElevation = 6.dp,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        LazyColumn(Modifier.heightIn(max = 280.dp)) {
                            items(suggestionRows) { r ->
                                ListItem(
                                    headlineContent = { Text(r.title, maxLines = 1) },
                                    supportingContent = r.subtitle?.takeIf { it.isNotBlank() }?.let { { Text(it, maxLines = 1) } },
                                    leadingContent = { Icon(if (r.saved) Icons.Filled.Restaurant else Icons.Filled.Place, null) },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier.clickable(onClick = r.onClick),
                                )
                            }
                        }
                    }
                } else if (unmatched > 0) {
                    AssistChip(
                        onClick = vm::retryUnmatched,
                        label = { Text("$unmatched not on map — tap to retry") },
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = peek + 16.dp),
                horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FloatingActionButton(
                    onClick = {
                        if (!hasLocation) askForLocation() else scope.launch {
                            clearSearch(); vm.select(null)
                            (fetchLocation() ?: userLoc)?.let { camera.animate(CameraUpdateFactory.newLatLngZoom(it, 13f), 600) }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) { Icon(Icons.Filled.MyLocation, "Back to my location") }
                ExtendedFloatingActionButton(
                    onClick = onOpenAdd, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add place") },
                )
            }
        }
    }

    if (sel != null && showDetails) {
        ModalBottomSheet(onDismissRequest = { showDetails = false }) { PlaceSheet(vm, sel) }
    }
}

@Composable
private fun LocationSearchField(query: String, onQuery: (String) -> Unit, onClear: () -> Unit) {
    Surface(shape = CircleShape, shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        TextField(
            value = query, onValueChange = onQuery, singleLine = true,
            placeholder = { Text("Search a city or place") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClear) { Icon(Icons.Filled.Close, "Clear") } }) else null,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NearbyPanel(
    maxListHeight: Dp,
    title: String,
    focus: SavedPlace?,
    rating: Pair<Double, Int>?,
    hasCenter: Boolean,
    rows: List<Pair<SavedPlace, Float>>,
    radiusMiles: Int,
    onRadius: (Int) -> Unit,
    onPick: (SavedPlace) -> Unit,
    onOpenDetails: () -> Unit,
    onClearFocus: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (focus != null) {
            Surface(
                onClick = onOpenDetails,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 2.dp),
            ) {
                Row(Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(focus.name, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1)
                        val sub = listOfNotNull(rating?.let { "★ %.1f (%,d)".format(it.first, it.second) }, focus.cuisine).joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(onOpenDetails, shapes = ButtonDefaults.shapes()) { Text("Details") }
                    IconButton(onClearFocus) { Icon(Icons.Filled.Close, "Close") }
                }
            }
        }
        Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1, modifier = Modifier.weight(1f))
            if (hasCenter) {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    Surface(onClick = { menuOpen = true }, shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${rows.size} within $radiusMiles mi",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Icon(Icons.Filled.ArrowDropDown, "Change radius", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    DropdownMenu(menuOpen, { menuOpen = false }) {
                        RADIUS_OPTIONS.forEach { miles ->
                            DropdownMenuItem(
                                text = { Text("$miles mi") },
                                leadingIcon = { if (miles == radiusMiles) Icon(Icons.Filled.Check, null) },
                                onClick = { onRadius(miles); menuOpen = false },
                            )
                        }
                    }
                }
            }
        }
        val empty = when {
            !hasCenter -> "Allow location, or search a city to see your spots near it."
            rows.isEmpty() -> "None of your saved places are within $radiusMiles miles."
            else -> null
        }
        if (empty != null) {
            Text(empty, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = maxListHeight),
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(SegmentedGap),
        ) {
            itemsIndexed(rows, key = { _, r -> r.first.id }) { i, (p, dist) ->
                SegmentedItem(
                    index = i, count = rows.size, onClick = { onPick(p) },
                    leading = {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(44.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Restaurant, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                    },
                    supporting = { Text(listOfNotNull(p.cuisine, miles(dist)).joinToString(" · ")) },
                ) { Text(p.name, style = MaterialTheme.typography.titleMediumEmphasized) }
            }
        }
    }
}

private fun meters(from: LatLng, p: SavedPlace): Float {
    val out = FloatArray(1)
    Location.distanceBetween(from.latitude, from.longitude, p.lat!!, p.lng!!, out)
    return out[0]
}

private fun miles(meters: Float): String = "%.1f mi".format(meters / METERS_PER_MILE)
