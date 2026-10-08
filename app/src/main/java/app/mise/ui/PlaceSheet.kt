package app.mise.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mise.data.PlaceDetails
import app.mise.data.SavedPlace
import kotlinx.coroutines.delay

/** Detail sheet: Google gist on top, then the user's dishes, notes and cuisine. */
@Composable
fun PlaceSheet(vm: MainViewModel, place: SavedPlace) {
    val details by vm.details.collectAsStateWithLifecycle()
    val dishes by vm.notes.collectAsStateWithLifecycle()
    var draft by remember(place.id) { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(place.name, style = MaterialTheme.typography.headlineMedium)
            Text(place.address ?: place.city, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            when (val d = details) {
                DetailsState.Idle -> {}
                DetailsState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                is DetailsState.Error -> Text(d.message, color = MaterialTheme.colorScheme.error)
                is DetailsState.Loaded -> RatingChip(d.details)
            }
        }
        item {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Dishes", style = MaterialTheme.typography.titleLarge)
            Text("Check off what you've tried", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(dishes, key = { it.id }) { dish ->
            ListItem(
                headlineContent = { Text(dish.text) },
                leadingContent = { Checkbox(checked = dish.tried, onCheckedChange = { vm.toggleDish(dish) }) },
                trailingContent = { IconButton({ vm.deleteDish(dish) }) { Icon(Icons.Filled.Close, "Delete") } },
            )
        }
        item {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Add a dish") },
                singleLine = true,
                trailingIcon = {
                    IconButton({ vm.addDish(draft); draft = "" }, enabled = draft.isNotBlank()) { Icon(Icons.Filled.Add, "Add") }
                },
            )
        }
        item {
            Spacer(Modifier.height(8.dp))
            AutoSaveField(place.id, place.notes.orEmpty(), "Notes", singleLine = false, onSave = vm::saveNotes)
        }
        item {
            AutoSaveField(place.id, place.cuisine.orEmpty(), "Cuisine", singleLine = true, onSave = vm::saveCuisine)
        }
        item {
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
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RatingChip(d: PlaceDetails) {
    val rating = d.rating ?: return
    AssistChip(
        onClick = {},
        leadingIcon = { Icon(Icons.Filled.Star, null, Modifier.size(18.dp)) },
        label = { Text("%.1f  ·  %,d ratings".format(rating, d.ratingCount ?: 0)) },
    )
}
