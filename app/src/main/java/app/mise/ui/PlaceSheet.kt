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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mise.data.PlaceDetails
import app.mise.data.SavedPlace

/** Bottom sheet: Google gist on top, user's dish bullets below. */
@Composable
fun PlaceSheet(vm: MainViewModel, place: SavedPlace) {
    val details by vm.details.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
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
                is DetailsState.Loaded -> GoogleGist(d.details)
            }
        }
        item {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Dishes", style = MaterialTheme.typography.titleLarge)
            Text("Check off what you've tried", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(notes, key = { it.id }) { note ->
            ListItem(
                headlineContent = { Text(note.text) },
                leadingContent = { Checkbox(checked = note.tried, onCheckedChange = { vm.toggleNote(note) }) },
                trailingContent = { IconButton({ vm.deleteNote(note) }) { Icon(Icons.Filled.Close, "Delete") } },
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
                    IconButton({ vm.addNote(draft); draft = "" }, enabled = draft.isNotBlank()) { Icon(Icons.Filled.Add, "Add") }
                },
            )
        }
        item {
            TextButton({ vm.deleteSelected() }, Modifier.padding(bottom = 16.dp)) { Text("Remove from my list") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoogleGist(d: PlaceDetails) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        d.rating?.let {
            AssistChip(
                onClick = {},
                leadingIcon = { Icon(Icons.Filled.Star, null, Modifier.size(18.dp)) },
                label = { Text("%.1f  ·  %,d reviews".format(it, d.ratingCount ?: 0)) },
            )
        }
        d.summary?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        d.reviews.take(3).forEach { r ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${r.author} · ${"★".repeat(r.rating.toInt())}", style = MaterialTheme.typography.labelLarge)
                    Text(r.text, style = MaterialTheme.typography.bodyMedium, maxLines = 4)
                }
            }
        }
    }
}
