package app.mise.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mise.data.EntryKind

/** Check and fix how each pasted line was split, then save everything. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(vm: MainViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val entries by vm.review.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val committing = progress.total > 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (committing) "Adding places" else "Review ${entries.size} places") },
                navigationIcon = {
                    if (!committing) IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
        bottomBar = {
            if (!committing && entries.isNotEmpty()) {
                Button(
                    onClick = vm::commitReview,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                ) { Text("Add ${entries.size} places to map") }
            }
        },
    ) { padding ->
        if (committing) {
            Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LinearProgressIndicator(progress = { progress.done / progress.total.toFloat() }, modifier = Modifier.fillMaxWidth())
                Text("${progress.done} / ${progress.total} matched on Google Maps")
                if (progress.failed.isNotEmpty()) {
                    Text("Saved without a pin (retry from the map):", style = MaterialTheme.typography.titleSmall)
                    progress.failed.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (!progress.running) Button(onDone, Modifier.fillMaxWidth()) { Text("Done") }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            ) {
                items(entries, key = { it.id }) { e ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                OutlinedTextField(
                                    e.name, { v -> vm.updateEntry(e.id) { it.copy(name = v) } },
                                    label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f),
                                )
                                IconButton({ vm.removeEntry(e.id) }) { Icon(Icons.Filled.Delete, "Skip this place") }
                            }
                            OutlinedTextField(
                                e.location, { v -> vm.updateEntry(e.id) { it.copy(location = v) } },
                                label = { Text("Location") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            )
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                EntryKind.entries.forEachIndexed { i, kind ->
                                    SegmentedButton(
                                        selected = e.kind == kind,
                                        onClick = { vm.updateEntry(e.id) { it.copy(kind = kind) } },
                                        shape = SegmentedButtonDefaults.itemShape(i, EntryKind.entries.size),
                                    ) { Text(kind.name) }
                                }
                            }
                            OutlinedTextField(
                                e.text, { v -> vm.updateEntry(e.id) { it.copy(text = v) } },
                                label = { Text(if (e.kind == EntryKind.Dish) "Dishes (comma separated)" else e.kind.name) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
