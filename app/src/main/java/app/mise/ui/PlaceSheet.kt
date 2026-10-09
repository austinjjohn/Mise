package app.mise.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mise.data.PlaceDetails
import app.mise.data.SavedPlace
import app.mise.data.displayName
import kotlinx.coroutines.delay

/** Detail sheet: star rating, then the user's dishes, notes and cuisine. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaceSheet(vm: MainViewModel, place: SavedPlace) {
    val details by vm.details.collectAsStateWithLifecycle()
    val dishes by vm.notes.collectAsStateWithLifecycle()
    var draft by remember(place.id) { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(SegmentedGap),
    ) {
        item {
            Text(place.displayName, style = MaterialTheme.typography.headlineMediumEmphasized)
            Text(place.address ?: place.city, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            when (val d = details) {
                DetailsState.Idle -> {}
                DetailsState.Loading -> LoadingIndicator(Modifier.size(40.dp))
                is DetailsState.Error -> Text(d.message, color = MaterialTheme.colorScheme.error)
                is DetailsState.Loaded -> RatingChip(d.details)
            }
            Spacer(Modifier.height(16.dp))
            Text("Dishes", style = MaterialTheme.typography.titleLargeEmphasized)
            Text("Tap to check off what you've tried", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
        itemsIndexed(dishes, key = { _, d -> d.id }) { i, dish ->
            SegmentedItem(
                index = i, count = dishes.size, onClick = { vm.toggleDish(dish) },
                leading = { Checkbox(checked = dish.tried, onCheckedChange = null) },
                trailing = { IconButton({ vm.deleteDish(dish) }) { Icon(Icons.Filled.Close, "Delete") } },
            ) {
                Text(dish.text, textDecoration = if (dish.tried) TextDecoration.LineThrough else null)
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Add a dish") },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                trailingIcon = {
                    FilledIconButton({ vm.addDish(draft); draft = "" }, enabled = draft.isNotBlank()) { Icon(Icons.Filled.Add, "Add") }
                },
            )
            Spacer(Modifier.height(12.dp))
            AutoSaveField(place.id, place.notes.orEmpty(), "Notes", singleLine = false, onSave = vm::saveNotes)
            Spacer(Modifier.height(8.dp))
            AutoSaveField(place.id, place.cuisine.orEmpty(), "Cuisine", singleLine = true, onSave = vm::saveCuisine)
            Spacer(Modifier.height(8.dp))
            TextButton({ vm.deleteSelected() }, Modifier.padding(bottom = 16.dp)) { Text("Remove from my list") }
        }
    }
}

/** Text field that saves itself shortly after typing stops. */
@Composable
private fun AutoSaveField(key: Any, saved: String, label: String, singleLine: Boolean, onSave: (String) -> Unit) {
    var text by remember(key) { mutableStateOf(saved) }
    LaunchedEffect(text) {
        if (text != saved) {
            delay(400)
            onSave(text)
        }
    }
    OutlinedTextField(
        value = text, onValueChange = { text = it }, label = { Text(label) },
        singleLine = singleLine, minLines = if (singleLine) 1 else 3,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RatingChip(d: PlaceDetails) {
    val rating = d.rating ?: return
    Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(6.dp))
            Text(
                "%.1f  ·  %,d ratings".format(rating, d.ratingCount ?: 0),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
