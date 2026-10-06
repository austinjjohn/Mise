package app.mise.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Paste your existing list; each line is matched against Google Places to get an exact pin. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(vm: MainViewModel, onBack: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    val progress by vm.import.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import list") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "One place per line, like:\nJoe's Pizza, New York\nBlue Bottle - Oakland\nTaco Stand",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = city, onValueChange = { city = it }, singleLine = true,
                label = { Text("Default city (for lines without one)") }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text("Your list") }, modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
            )
            Button(
                onClick = { vm.importList(text, city.trim()) },
                enabled = text.isNotBlank() && !progress.running,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (progress.running) "Matching…" else "Find on map") }

            if (progress.total > 0) {
                LinearProgressIndicator(progress = { progress.done / progress.total.toFloat() }, modifier = Modifier.fillMaxWidth())
                Text("${progress.done} / ${progress.total} processed")
                if (progress.failed.isNotEmpty()) {
                    Text("Couldn't match (saved without a pin):", style = MaterialTheme.typography.titleSmall)
                    progress.failed.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
