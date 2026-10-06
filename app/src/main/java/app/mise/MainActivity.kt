package app.mise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import app.mise.ui.ImportScreen
import app.mise.ui.MainViewModel
import app.mise.ui.MapScreen
import app.mise.ui.theme.MiseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiseTheme {
                val vm: MainViewModel = viewModel()
                // Two screens only; swap for Navigation Compose if this grows.
                var showImport by rememberSaveable { mutableStateOf(false) }
                BackHandler(showImport) { showImport = false }
                if (showImport) ImportScreen(vm, onBack = { showImport = false })
                else MapScreen(vm, onOpenImport = { showImport = true })
            }
        }
    }
}
