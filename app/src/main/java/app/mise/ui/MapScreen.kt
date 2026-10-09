package app.mise.ui

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.RectF
import android.location.Location
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mise.data.SavedPlace
import app.mise.data.displayName
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

private val RADIUS_OPTIONS = listOf(1, 2, 5, 10, 15, 25, 50)
private const val METERS_PER_MILE = 1609.34f

/** Labels appear once the map is zoomed in at least this far (they are also decluttered). */
private const val LABEL_MIN_ZOOM = 11.5f

/** A location picked from search; the nearby list centers on it. */
private data class Area(val name: String, val latLng: LatLng)

private enum class RowKind { Filter, Saved, Place }

private data class SuggestionRow(val title: String, val subtitle: String?, val kind: RowKind, val onClick: () -> Unit)

@Composable
fun MapScreen(vm: MainViewModel, onOpenAdd: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        MapContent(vm, onOpenAdd, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
    }
}

@SuppressLint("MissingPermission") // guarded by hasLocation
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MapContent(vm: MainViewModel, onOpenAdd: () -> Unit, widthPx: Float, heightPx: Float) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val places by vm.places.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val details by vm.details.collectAsStateWithLifecycle()
    val radiusMiles by vm.radiusMiles.collectAsStateWithLifecycle()
    val dishes by vm.dishesByPlace.collectAsStateWithLifecycle()

    var hasLocation by remember { mutableStateOf(false) }
    var userLoc by remember { mutableStateOf<LatLng?>(null) }
    var area by remember { mutableStateOf<Area?>(null) }
    var filter by remember { mutableStateOf<String?>(null) }
    var showDetails by rememberSaveable { mutableStateOf(false) }

    var query by rememberSaveable { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    var predictions by remember { mutableStateOf<List<app.mise.data.Suggestion>>(emptyList()) }

    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(39.5, -98.35), 4f) }
    val listState = rememberLazyListState()

    // --- bottom sheet: collapsed / one-third / nearly full, with Expressive spring motion -------
    val sel = selected
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val latestSpec by rememberUpdatedState(spatialSpec)
    val topInset = WindowInsets.statusBars.getTop(density).toFloat()
    val bottomInset = WindowInsets.navigationBars.getBottom(density).toFloat()
    val collapsedBase = with(density) { 68.dp.toPx() } + bottomInset
    val controller = remember(heightPx) {
        SheetController(
            scope = scope, spec = { latestSpec },
            halfPx = heightPx * 0.4f,
            fullPx = heightPx - topInset - with(density) { 76.dp.toPx() },
            collapsed = collapsedBase,
        )
    }
    // A selected place adds a card to the header, so the collapsed height grows to show it.
    LaunchedEffect(sel != null, collapsedBase) {
        val wasCollapsed = controller.isCollapsed()
        controller.collapsedPx = collapsedBase + if (sel != null) with(density) { 84.dp.toPx() } else 0f
        if (wasCollapsed) controller.collapse()
    }
    val aboveHalf by remember(controller) { derivedStateOf { controller.heightPx > controller.halfPx + 1f } }

    // --- location -------------------------------------------------------------------------------
    suspend fun fetchLocation(): LatLng? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val loc = runCatching { client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await() }.getOrNull()
            ?: runCatching { client.lastLocation.await() }.getOrNull()
        return loc?.let { LatLng(it.latitude, it.longitude) }?.also { userLoc = it; vm.nearHint = it }
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
        controller.half()
        val lat = p.lat ?: return
        val lng = p.lng ?: return
        flyTo(LatLng(lat, lng), 15f)
    }

    fun clearSearch() {
        query = ""; typing = false; predictions = emptyList(); area = null; filter = null
        focusManager.clearFocus()
    }

    // The map's bottom padding (where Google's logo sits) follows the sheet, but only changes once the
    // sheet has settled and the camera is idle, so it never fights a gesture.
    var padBottomPx by remember(controller) { mutableFloatStateOf(controller.halfPx) }
    LaunchedEffect(controller) {
        snapshotFlow { controller.heightPx to camera.isMoving }.collectLatest { (h, moving) ->
            if (moving) return@collectLatest
            delay(150)
            padBottomPx = if (h <= controller.collapsedPx + 2f) controller.collapsedPx else controller.halfPx
        }
    }

    // Like Google Maps: touching the map tucks the sheet away.
    LaunchedEffect(camera.isMoving) {
        if (camera.isMoving && camera.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE && !controller.isCollapsed()) {
            controller.collapse()
        }
    }

    // Back gesture peels one layer at a time: expanded sheet, then selection, then search.
    BackHandler(enabled = aboveHalf || sel != null || area != null || query.isNotEmpty()) {
        when {
            aboveHalf -> controller.half()
            sel != null -> vm.select(null)
            else -> clearSearch()
        }
    }

    // --- search suggestions ---------------------------------------------------------------------
    LaunchedEffect(query, typing) {
        if (!typing || query.trim().length < 2) { predictions = emptyList(); return@LaunchedEffect }
        delay(250)
        predictions = vm.suggest(query.trim(), area?.latLng ?: userLoc)
    }

    fun applyFilter(q: String) {
        val text = q.trim()
        if (text.isEmpty()) return
        filter = text; query = text; typing = false; predictions = emptyList(); area = null
        focusManager.clearFocus()
        vm.select(null)
        controller.half()
        val hits = places.filter { it.lat != null && it.lng != null && searchReason(it, dishes[it.id].orEmpty(), text) != null }
        when {
            hits.size == 1 -> flyTo(LatLng(hits[0].lat!!, hits[0].lng!!), 14f)
            hits.size > 1 -> scope.launch {
                val bounds = LatLngBounds.Builder().apply { hits.forEach { include(LatLng(it.lat!!, it.lng!!)) } }.build()
                runCatching { camera.animate(CameraUpdateFactory.newLatLngBounds(bounds, with(density) { 64.dp.roundToPx() }), 700) }
            }
        }
    }

    val suggestionRows: List<SuggestionRow> = if (!typing) emptyList() else {
        val q = query.trim()
        val hits = places.mapNotNull { p -> searchReason(p, dishes[p.id].orEmpty(), q)?.let { p to it } }
        val showAll = if (hits.size > 1) {
            listOf(SuggestionRow("Show all ${hits.size} places matching “$q”", null, RowKind.Filter) { applyFilter(q) })
        } else emptyList()
        val mine = hits.take(4).map { (p, reason) ->
            SuggestionRow(p.displayName, reason.ifBlank { listOfNotNull(p.cuisine, p.city.ifBlank { null }).joinToString(" · ") }, RowKind.Saved) {
                typing = false; query = p.displayName; focusManager.clearFocus(); area = null; filter = null; pick(p)
            }
        }
        val google = predictions.map { s ->
            SuggestionRow(s.primary, s.secondary, RowKind.Place) {
                typing = false; query = s.primary; predictions = emptyList(); focusManager.clearFocus()
                scope.launch {
                    val (name, ll) = vm.locate(s.placeId) ?: return@launch
                    vm.select(null)
                    filter = null
                    area = Area(name.ifBlank { s.primary }, ll)
                    controller.half()
                    flyTo(ll, 12f)
                }
            }
        }
        showAll + mine + google
    }

    // --- what the nearby list is centered on ------------------------------------------------------
    val selLatLng = sel?.let { p -> p.lat?.let { LatLng(it, p.lng!!) } }
    val center = selLatLng ?: area?.latLng ?: userLoc
    val activeFilter = filter
    val title = when {
        activeFilter != null && sel != null -> "More “$activeFilter”"
        activeFilter != null -> "“$activeFilter”"
        sel != null -> "Also near ${sel.displayName}"
        area != null -> "Near ${area!!.name}"
        else -> "Near you"
    }
    val matchedIds = remember(places, dishes, activeFilter) {
        if (activeFilter == null) null
        else places.filter { searchReason(it, dishes[it.id].orEmpty(), activeFilter) != null }.map { it.id }.toSet()
    }
    val rows = remember(places, center, sel?.id, radiusMiles, matchedIds) {
        places.filter { it.lat != null && it.lng != null && it.id != sel?.id && (matchedIds == null || it.id in matchedIds) }
            .map { it to (center?.let { c -> meters(c, it) } ?: 0f) }
            .filter { matchedIds != null || (center != null && it.second <= radiusMiles * METERS_PER_MILE) }
            .sortedBy { it.second }
    }
    LaunchedEffect(sel?.id, area, filter) { listState.scrollToItem(0) }

    // --- map markers + zoom-aware labels --------------------------------------------------------
    val cs = MaterialTheme.colorScheme
    val icons = remember(context, cs.tertiary, cs.primary, cs.onSurface, cs.surface) {
        MarkerIcons(
            context,
            MarkerColors(cs.tertiary.toArgb(), cs.onTertiary.toArgb(), cs.primary.toArgb(), cs.onPrimary.toArgb(), cs.onSurface.toArgb(), cs.surface.toArgb()),
        )
    }
    val shownPlaces = remember(places, matchedIds) { if (matchedIds == null) places else places.filter { it.id in matchedIds } }
    var labelIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var mapLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(shownPlaces, sel?.id, icons, mapLoaded) {
        if (!mapLoaded) return@LaunchedEffect
        snapshotFlow { camera.position }.collectLatest {
            delay(80)
            labelIds = chooseLabels(shownPlaces, camera, icons, sel?.id, widthPx, heightPx)
        }
    }

    val properties = remember(hasLocation) { MapProperties(isMyLocationEnabled = hasLocation) }
    val uiSettings = remember {
        MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false, mapToolbarEnabled = false, compassEnabled = false)
    }
    val unmatched = places.count { it.lat == null }
    val rating = (details as? DetailsState.Loaded)?.details?.let { d -> d.rating?.let { it to (d.ratingCount ?: 0) } }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().momentumZoom(camera, scope)) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            properties = properties,
            uiSettings = uiSettings,
            contentPadding = PaddingValues(bottom = with(density) { padBottomPx.toDp() }),
            mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
            onMapLoaded = { mapLoaded = true },
            onMapClick = { vm.select(null); focusManager.clearFocus(); typing = false; controller.collapse() },
        ) {
            shownPlaces.forEach { p ->
                val lat = p.lat ?: return@forEach
                val lng = p.lng ?: return@forEach
                key(p.id, lat, lng) {
                    val isSel = p.id == sel?.id
                    PlaceMarker(p, LatLng(lat, lng), isSel, isSel || p.id in labelIds, icons) { pick(p) }
                }
            }
        }
        }

        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            LocationSearchField(query, onQuery = { query = it; typing = true }, onClear = ::clearSearch, onSearch = { applyFilter(query) })
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
                                leadingContent = { Icon(when (r.kind) { RowKind.Filter -> Icons.Filled.FilterList; RowKind.Saved -> Icons.Filled.Restaurant; RowKind.Place -> Icons.Filled.Place }, null) },
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

        // Buttons ride on top of the sheet until it reaches its one-third height, then stay put behind it.
        Column(
            Modifier.align(Alignment.BottomEnd)
                .offset { IntOffset(0, -min(controller.heightPx, controller.halfPx).roundToInt()) }
                .padding(end = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CompassButton(camera) {
                scope.launch {
                    camera.animate(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(camera.position).bearing(0f).tilt(0f).build()), 400)
                }
            }
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

        ResizableSheet(controller, listState, Modifier.align(Alignment.BottomCenter)) { drag ->
            NearbyHeader(
                dragModifier = drag, title = title, focus = sel, rating = rating, hasCenter = center != null,
                count = rows.size, filtered = filter != null, radiusMiles = radiusMiles, onRadius = vm::setRadius,
                onOpenDetails = { showDetails = true }, onClearFocus = { vm.select(null) },
            )
            val empty = when {
                filter != null && rows.isEmpty() -> "No saved places match “$filter”."
                center == null && filter == null -> "Allow location, or search a city to see your spots near it."
                rows.isEmpty() -> "None of your saved places are within $radiusMiles miles."
                else -> null
            }
            if (empty != null) {
                Text(empty, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = with(density) { bottomInset.toDp() } + 8.dp),
                verticalArrangement = Arrangement.spacedBy(SegmentedGap),
            ) {
                itemsIndexed(rows, key = { _, r -> r.first.id }) { i, (p, dist) ->
                    SegmentedItem(
                        index = i, count = rows.size, onClick = { pick(p) },
                        leading = {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(44.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Restaurant, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                }
                            }
                        },
                        supporting = {
                            val reason = activeFilter?.let { searchReason(p, dishes[p.id].orEmpty(), it) }?.ifBlank { null }
                            Text(
                                listOfNotNull(
                                    reason, p.cuisine?.takeIf { reason?.startsWith("Cuisine") != true },
                                    if (center != null) miles(dist) else null,
                                ).joinToString(" · "),
                            )
                        },
                    ) { Text(p.displayName, style = MaterialTheme.typography.titleMediumEmphasized) }
                }
            }
        }
    }

    if (sel != null && showDetails) {
        ModalBottomSheet(onDismissRequest = { showDetails = false }) { PlaceSheet(vm, sel) }
    }
}

/** Appears only when the map is rotated or tilted; the arrow keeps pointing north. Tap to face north again. */
@Composable
private fun CompassButton(camera: CameraPositionState, onClick: () -> Unit) {
    val turned by remember { derivedStateOf { abs(camera.position.bearing) > 0.5f || camera.position.tilt > 0.5f } }
    AnimatedVisibility(turned, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Icon(Icons.Filled.Navigation, "Face north", Modifier.graphicsLayer { rotationZ = -camera.position.bearing })
        }
    }
}

/** Badge marker plus a name label that fades in and out as zoom and overlap allow. */
@Composable
@GoogleMapComposable
private fun PlaceMarker(p: SavedPlace, pos: LatLng, selected: Boolean, showLabel: Boolean, icons: MarkerIcons, onClick: () -> Unit) {
    Marker(
        state = remember { MarkerState(pos) },
        icon = remember(selected, icons) { icons.badge(selected) },
        anchor = Offset(0.5f, 0.5f),
        zIndex = if (selected) 3f else 2f,
        onClick = { onClick(); true },
    )
    val alpha by animateFloatAsState(if (showLabel) 1f else 0f, tween(220), label = "label")
    if (alpha > 0.01f) {
        Marker(
            state = remember { MarkerState(pos) },
            icon = remember(p.displayName, selected, icons) { icons.label(p.displayName, selected) },
            anchor = Offset(0f, 0.5f), // label bitmap's left edge sits on the badge center
            alpha = alpha,
            zIndex = 1f,
            onClick = { onClick(); true },
        )
    }
}

/**
 * Picks which places get a name label: nearest to the middle of the map first, skipping any label
 * that would overlap another label or marker, like Google Maps. Nothing is labeled when zoomed out.
 */
private fun chooseLabels(
    places: List<SavedPlace>, camera: CameraPositionState, icons: MarkerIcons, selectedId: Long?, w: Float, h: Float,
): Set<Long> {
    val proj = camera.projection ?: return emptySet()
    if (camera.position.zoom < LABEL_MIN_ZOOM && selectedId == null) return emptySet()
    class Item(val id: Long, val name: String, val x: Float, val y: Float, val r: Float)

    val items = places.mapNotNull { p ->
        val pt = proj.toScreenLocation(LatLng(p.lat ?: return@mapNotNull null, p.lng ?: return@mapNotNull null))
        if (pt.x < -60 || pt.y < -60 || pt.x > w + 60 || pt.y > h + 60) null
        else Item(p.id, p.displayName, pt.x.toFloat(), pt.y.toFloat(), icons.badgeRadius(p.id == selectedId))
    }
    val mid = proj.toScreenLocation(camera.position.target)
    val ordered = items.sortedBy { if (it.id == selectedId) -1.0 else hypot((it.x - mid.x).toDouble(), (it.y - mid.y).toDouble()) }
    val badges = items.associate { it.id to RectF(it.x - it.r, it.y - it.r, it.x + it.r, it.y + it.r) }
    val labels = mutableListOf<RectF>()
    val shown = mutableSetOf<Long>()
    for (it in ordered) {
        if (it.id != selectedId && camera.position.zoom < LABEL_MIN_ZOOM) continue
        val (lw, lh) = icons.labelSize(it.name, it.id == selectedId)
        val rect = RectF(it.x + it.r, it.y - lh / 2, it.x + lw, it.y + lh / 2)
        val clash = badges.any { (id, b) -> id != it.id && RectF.intersects(rect, b) } || labels.any { l -> RectF.intersects(rect, l) }
        if (!clash || it.id == selectedId) { shown += it.id; labels += rect }
    }
    return shown
}

@Composable
private fun LocationSearchField(query: String, onQuery: (String) -> Unit, onClear: () -> Unit, onSearch: () -> Unit) {
    Surface(shape = CircleShape, shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        TextField(
            value = query, onValueChange = onQuery, singleLine = true,
            placeholder = { Text("Search places, dishes, cuisines, or a city") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
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

/** Top of the sheet: the selected-place card (if any) and the title with the radius picker. Draggable. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NearbyHeader(
    dragModifier: Modifier,
    title: String,
    focus: SavedPlace?,
    rating: Pair<Double, Int>?,
    hasCenter: Boolean,
    count: Int,
    filtered: Boolean,
    radiusMiles: Int,
    onRadius: (Int) -> Unit,
    onOpenDetails: () -> Unit,
    onClearFocus: () -> Unit,
) {
    Column(dragModifier) {
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
                        Text(focus.displayName, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1)
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
            if (filtered) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        "$count results", Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            } else if (hasCenter) {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    Surface(onClick = { menuOpen = true }, shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "$count within $radiusMiles mi",
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
    }
}

private fun meters(from: LatLng, p: SavedPlace): Float {
    val out = FloatArray(1)
    Location.distanceBetween(from.latitude, from.longitude, p.lat!!, p.lng!!, out)
    return out[0]
}

private fun miles(meters: Float): String = "%.1f mi".format(meters / METERS_PER_MILE)
