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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp

/** One-time bulk import: paste a list, then review how each line was split before anything is saved. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImportScreen(vm: MainViewModel, onBack: () -> Unit, onReview: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val shape = MaterialTheme.shapes.extraLarge

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Bulk import") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Paste your list, one place per line. Lines like -- Columbia, MD -- set the location for the places below them. " +
                    "Anything in parentheses at the end of a line is sorted into cuisine, dish or note on the next screen.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                location, { location = it }, singleLine = true, shape = shape, modifier = Modifier.fillMaxWidth(),
                label = { Text("Default location (optional)") },
                supportingText = { Text("Used for lines before any -- header --") },
            )
            OutlinedTextField(
                text, { text = it }, label = { Text("Your list") }, shape = shape,
                modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp),
            )
            Button(
                onClick = { vm.parseForReview(text, location); onReview() },
                enabled = text.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
            ) { Text("Review") }
            Spacer(Modifier.height(24.dp))
        }
    }
}
