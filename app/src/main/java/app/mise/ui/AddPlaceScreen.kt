package app.mise.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp

/** The everyday way to add a place. Bulk import lives in the overflow menu. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddPlaceScreen(vm: MainViewModel, onBack: () -> Unit, onBulkImport: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var cuisine by rememberSaveable { mutableStateOf("") }
    var dishes by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val shape = MaterialTheme.shapes.extraLarge

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
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                location, { location = it }, label = { Text("Location") }, singleLine = true, shape = shape,
                supportingText = { Text("City or neighborhood, e.g. Columbia, MD") }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(cuisine, { cuisine = it }, label = { Text("Cuisine") }, singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                dishes, { dishes = it }, label = { Text("Dishes") }, shape = shape,
                supportingText = { Text("Comma separated") }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 2, shape = shape, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    saving = true
                    vm.addPlace(name, location, cuisine, dishes, notes) { onBack() }
                },
                enabled = name.isNotBlank() && !saving,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
            ) { Text(if (saving) "Finding it…" else "Add to map") }
            Spacer(Modifier.height(24.dp))
        }
    }
}
