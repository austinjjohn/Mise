package app.mise.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.mise.data.PlaceMatch
import app.mise.data.SavedPlace
import app.mise.data.Suggestion
import app.mise.data.displayName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The everyday way to add a place: one search box with autocomplete, like Google Maps. Bulk import is in the menu. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddPlaceScreen(vm: MainViewModel, onBack: () -> Unit, onBulkImport: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var cuisine by rememberSaveable { mutableStateOf("") }
    var dishes by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<Suggestion>>(emptyList()) }
    var picked by remember { mutableStateOf<PlaceMatch?>(null) }
    var pickedCity by remember { mutableStateOf<String?>(null) }
    var existing by remember { mutableStateOf<SavedPlace?>(null) }
    var saving by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val shape = MaterialTheme.shapes.extraLarge

    LaunchedEffect(query, typing) {
        if (!typing || query.trim().length < 2) { suggestions = emptyList(); return@LaunchedEffect }
        delay(250)
        suggestions = vm.suggestPlaces(query.trim())
    }

    fun choose(s: Suggestion) {
        typing = false; suggestions = emptyList(); query = s.primary
        pickedCity = s.secondary?.split(",")?.take(2)?.joinToString(",") { it.trim() }
        scope.launch {
            val info = vm.resolvePlace(s.placeId) ?: return@launch
            picked = info
            existing = vm.existingFor(info.placeId)
            if (cuisine.isBlank()) cuisine = info.cuisine.orEmpty()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Add place") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton({ menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(menuOpen, { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Bulk import a list") }, onClick = { menuOpen = false; onBulkImport() })
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; typing = true; picked = null; existing = null },
                label = { Text("Search for a place") },
                placeholder = { Text("Name and city, e.g. Cafe Mezcal Columbia") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = if (query.isNotEmpty()) ({
                    IconButton({ query = ""; typing = false; picked = null; existing = null; suggestions = emptyList() }) { Icon(Icons.Filled.Close, "Clear") }
                }) else null,
                singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth(),
            )

            if (suggestions.isNotEmpty()) {
                Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column {
                        suggestions.forEach { s ->
                            ListItem(
                                headlineContent = { Text(s.primary, maxLines = 1) },
                                supportingContent = s.secondary?.let { { Text(it, maxLines = 1) } },
                                leadingContent = { Icon(Icons.Filled.Place, null) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { choose(s) },
                            )
                        }
                    }
                }
            }

            picked?.let { info ->
                Surface(shape = shape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.padding(16.dp).fillMaxWidth()) {
                        Text(info.name ?: query, style = MaterialTheme.typography.titleMediumEmphasized)
                        info.address?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            existing?.let { dup ->
                Surface(shape = shape, color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        "Already on your list as ${dup.displayName}.",
                        Modifier.padding(16.dp).fillMaxWidth(), color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            OutlinedTextField(
                cuisine, { cuisine = it }, label = { Text("Cuisine") }, singleLine = true, shape = shape,
                supportingText = { Text("Filled in from Google when you pick a place; change it if you like") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                dishes, { dishes = it }, label = { Text("Dishes") }, shape = shape,
                supportingText = { Text("Comma separated") }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 2, shape = shape, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    saving = true
                    vm.addPlace(query, picked, pickedCity, cuisine, dishes, notes) { result ->
                        saving = false
                        when (result) {
                            is AddResult.Duplicate -> existing = result.existing
                            is AddResult.Added -> {
                                if (!result.onMap) Toast.makeText(context, "Saved, but Google couldn't find it, so there's no pin yet", Toast.LENGTH_LONG).show()
                                onBack()
                            }
                        }
                    }
                },
                enabled = query.isNotBlank() && !saving && existing == null,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
            ) { Text(if (saving) "Adding…" else "Add to map") }
            Spacer(Modifier.height(24.dp))
        }
    }
}
