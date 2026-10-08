package app.mise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import app.mise.ui.*
import app.mise.ui.theme.MiseTheme

private enum class Screen { Map, Add, Import, Review }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiseTheme {
                val vm: MainViewModel = viewModel()
                // Four screens; swap for Navigation Compose if this grows.
                var screen by rememberSaveable { mutableStateOf(Screen.Map) }
                val progress by vm.progress.collectAsState()
                BackHandler(screen != Screen.Map) {
                    screen = when (screen) {
                        Screen.Review -> if (progress.total > 0 && progress.running) Screen.Review else Screen.Import
                        Screen.Import -> Screen.Add
                        else -> Screen.Map
                    }
                }
                when (screen) {
                    Screen.Map -> MapScreen(vm, onOpenAdd = { screen = Screen.Add })
                    Screen.Add -> AddPlaceScreen(vm, onBack = { screen = Screen.Map }, onBulkImport = { screen = Screen.Import })
                    Screen.Import -> ImportScreen(vm, onBack = { screen = Screen.Add }, onReview = { screen = Screen.Review })
                    Screen.Review -> ReviewScreen(vm, onBack = { screen = Screen.Import }, onDone = { screen = Screen.Map })
                }
            }
        }
    }
}
