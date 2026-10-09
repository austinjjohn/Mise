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
        val tracker = VelocityTracker().apply { addPosition(second.uptimeMillis, second.position) }
        // (finger y, camera zoom) at every touch event, to learn how the SDK maps finger travel to zoom.
        val ys = ArrayList<Float>().apply { add(startY) }
        val zooms = ArrayList<Float>().apply { add(camera.position.zoom) }
        var moved = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.size > 1) return@awaitEachGesture // a second finger: it's a pinch, not this
            val change = event.changes.first()
            tracker.addPosition(change.uptimeMillis, change.position)
            ys += change.position.y
            zooms += camera.position.zoom
            if (abs(change.position.y - startY) > viewConfiguration.touchSlop) moved = true
            if (!change.pressed) break
        }
        if (!moved) return@awaitEachGesture

        // The camera reports its zoom a frame behind the finger. That delay shifts every sample the same
        // way, so it doesn't change the slope of zoom against finger position. Fit that slope over the
        // second part of the drag (zoom is already underway there) and use it, instead of comparing the
        // zoom at release with the zoom at the start, which a quick flick makes look like "no zoom".
        val from = ys.size / 3
        val meanY = ys.drop(from).average().toFloat()
        val meanZ = zooms.drop(from).average().toFloat()
        var cross = 0f
        var spread = 0f
        for (i in from until ys.size) {
            cross += (ys[i] - meanY) * (zooms[i] - meanZ)
            spread += (ys[i] - meanY) * (ys[i] - meanY)
        }
        if (spread < 40f * 40f) return@awaitEachGesture // not enough finger travel to judge
        val zoomPerPx = cross / spread
        val zoomVelocity = (zoomPerPx * tracker.calculateVelocity().y).coerceIn(-6f, 6f) // levels per second
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
