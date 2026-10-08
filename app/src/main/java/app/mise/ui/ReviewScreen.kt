package app.mise.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mise.data.EntryKind

/** Check and fix how each pasted line was split, then save everything. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReviewScreen(vm: MainViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    val entries by vm.review.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val committing = progress.total > 0
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val shape = MaterialTheme.shapes.extraLarge

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(if (committing) "Adding places" else "Review ${entries.size} places") },
                navigationIcon = {
                    if (!committing) IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                scrollBehavior = scroll,
            )
        },
        bottomBar = {
            if (!committing && entries.isNotEmpty()) {
                Button(
                    onClick = vm::commitReview,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).heightIn(min = ButtonDefaults.MediumContainerHeight),
                ) { Text("Add ${entries.size} places to map") }
            }
        },
    ) { padding ->
        if (committing) {
            Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LinearWavyProgressIndicator(progress = { progress.done / progress.total.toFloat() }, modifier = Modifier.fillMaxWidth())
                Text("${progress.done} / ${progress.total} matched on Google Maps", style = MaterialTheme.typography.titleMediumEmphasized)
                if (progress.failed.isNotEmpty()) {
                    Text("Saved without a pin (retry from the map):", style = MaterialTheme.typography.titleSmall)
                    progress.failed.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (!progress.running) {
                    Button(onDone, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight)) { Text("Done") }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding),
            ) {
                items(entries, key = { it.id }) { e ->
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    e.name, { v -> vm.updateEntry(e.id) { it.copy(name = v) } },
                                    label = { Text("Name") }, singleLine = true, shape = shape, modifier = Modifier.weight(1f),
                                )
                                IconButton({ vm.removeEntry(e.id) }) { Icon(Icons.Filled.Delete, "Skip this place") }
                            }
                            OutlinedTextField(
                                e.location, { v -> vm.updateEntry(e.id) { it.copy(location = v) } },
                                label = { Text("Location") }, singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth(),
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                                val kinds = EntryKind.entries
                                kinds.forEachIndexed { i, kind ->
                                    ToggleButton(
                                        checked = e.kind == kind,
                                        onCheckedChange = { vm.updateEntry(e.id) { it.copy(kind = kind) } },
                                        shapes = when (i) {
                                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                            kinds.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) { Text(kind.name) }
                                }
                            }
                            OutlinedTextField(
                                e.text, { v -> vm.updateEntry(e.id) { it.copy(text = v) } },
                                label = { Text(if (e.kind == EntryKind.Dish) "Dishes (comma separated)" else e.kind.name) },
                                shape = shape, modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
