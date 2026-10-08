package app.mise.ui

import android.graphics.Point
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.maps.android.compose.CameraPositionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Google Maps lets you double-tap, drag to zoom, and let go with a flick so the zoom keeps coasting.
 * The Maps SDK does the live one-finger zoom but stops dead on release, so this watches the same
 * touches (without consuming them) and, on release, keeps zooming with a decaying velocity.
 * Any new touch stops the coast.
 */
fun Modifier.momentumZoom(camera: CameraPositionState, scope: CoroutineScope): Modifier = pointerInput(camera) {
    var coasting: Job? = null
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        coasting?.cancel()

        // A double-tap is: quick tap, then a second touch soon after and near the first.
        withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) { waitForUpOrCancellation(PointerEventPass.Initial) }
            ?: return@awaitEachGesture
        val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        } ?: return@awaitEachGesture
        if ((second.position - first.position).getDistance() > viewConfiguration.touchSlop * 4) return@awaitEachGesture
        coasting?.cancel()

        val startY = second.position.y
        val startZoom = camera.position.zoom
        val tracker = VelocityTracker().apply { addPosition(second.uptimeMillis, second.position) }
        var lastY = startY
        var moved = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.size > 1) return@awaitEachGesture // a second finger: it's a pinch, not this
            val change = event.changes.first()
            tracker.addPosition(change.uptimeMillis, change.position)
            lastY = change.position.y
            if (abs(lastY - startY) > viewConfiguration.touchSlop) moved = true
            if (!change.pressed) break
        }
        if (!moved) return@awaitEachGesture

        // The SDK maps finger travel to zoom; measure that ratio from this very gesture so the coast
        // continues in the same direction and at the same rate, whichever way the SDK maps it.
        val travel = lastY - startY
        val zoomed = camera.position.zoom - startZoom
        if (abs(zoomed) < 0.05f) return@awaitEachGesture
        val zoomVelocity = (zoomed / travel * tracker.calculateVelocity().y).coerceIn(-6f, 6f) // levels per second
        if (abs(zoomVelocity) < 0.3f) return@awaitEachGesture

        val focus = Point(second.position.x.roundToInt(), second.position.y.roundToInt())
        coasting = scope.launch {
            var v = zoomVelocity
            var last = withFrameNanos { it }
            while (abs(v) > 0.05f) {
                val now = withFrameNanos { it }
                val dt = (now - last) / 1e9f
                last = now
                v *= exp(-3.5f * dt)
                camera.move(CameraUpdateFactory.zoomBy(v * dt, focus))
            }
        }
    }
}
