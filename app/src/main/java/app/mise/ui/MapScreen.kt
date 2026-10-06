package app.mise.ui

import android.Manifest
import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@SuppressLint("MissingPermission") // guarded by hasLocation
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MapScreen(vm: MainViewModel, onOpenImport: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val places by vm.places.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()

    var hasLocation by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasLocation = it[Manifest.permission.ACCESS_FINE_LOCATION] == true || it[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    LaunchedEffect(Unit) {
        permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(39.5, -98.35), 4f) }

    // Center on the user once we have permission.
    LaunchedEffect(hasLocation) {
        if (!hasLocation) return@LaunchedEffect
        LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { loc ->
            loc?.let { camera.position = CameraPosition.fromLatLngZoom(LatLng(it.latitude, it.longitude), 13f) }
        }
    }

    val unmatched = places.count { it.lat == null }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onOpenImport, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Import list") })
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = camera,
                properties = MapProperties(isMyLocationEnabled = hasLocation),
                uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocation, zoomControlsEnabled = false),
                contentPadding = padding,
                onMapClick = { vm.select(null) },
            ) {
                places.forEach { p ->
                    val lat = p.lat ?: return@forEach
                    val lng = p.lng ?: return@forEach
                    Marker(
                        state = MarkerState(LatLng(lat, lng)),
                        title = p.name,
                        onClick = { vm.select(p); false },
                    )
                }
            }
            if (unmatched > 0) {
                AssistChip(
                    onClick = vm::retryUnmatched,
                    label = { Text("$unmatched not on map — tap to retry") },
                    modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp),
                )
            }
        }
    }

    selected?.let { place ->
        ModalBottomSheet(onDismissRequest = { vm.select(null) }) {
            PlaceSheet(vm, place)
        }
    }
}
