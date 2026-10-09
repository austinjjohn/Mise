package app.mise.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Height state for a bottom sheet with three snap points (collapsed, half, full) that follows the
 * finger and settles with a spring. The height is only read in the layout phase, so dragging
 * relayouts the sheet without recomposing the screen.
 */
@Stable
class SheetController(
    private val scope: CoroutineScope,
    private val spec: () -> AnimationSpec<Float>,
    val halfPx: Float,
    val fullPx: Float,
    collapsed: Float,
) {
    var collapsedPx by mutableFloatStateOf(collapsed)
    var heightPx by mutableFloatStateOf(halfPx)
        private set
    private var job: Job? = null
    private val anchors get() = listOf(collapsedPx, halfPx, fullPx)

    fun animateTo(target: Float, velocity: Float = 0f) {
        job?.cancel()
        job = scope.launch { animate(heightPx, target, velocity, spec()) { v, _ -> heightPx = v } }
    }

    fun collapse() = animateTo(collapsedPx)
    fun half() = animateTo(halfPx)
    fun isCollapsed() = heightPx <= collapsedPx + 1f

    /** [delta] is finger movement in px, positive = downward. */
    fun dragBy(delta: Float) {
        job?.cancel()
        heightPx = (heightPx - delta).coerceIn(collapsedPx, fullPx)
    }

    /** Snap to the anchor nearest to where the sheet would coast to, keeping the fling velocity. */
    fun settle(velocityDown: Float) {
        val projected = heightPx - velocityDown * 0.12f
        animateTo(anchors.minBy { abs(it - projected) }, -velocityDown)
    }

    /** Lets a scrolling list and the sheet share a drag: the sheet moves first, then the list scrolls. */
    fun nestedScrollConnection(listCanScrollBackward: () -> Boolean) = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy < 0 && heightPx < fullPx) {
                val used = min(-dy, fullPx - heightPx)
                job?.cancel(); heightPx += used
                return Offset(0f, -used)
            }
            if (dy > 0 && !listCanScrollBackward() && heightPx > collapsedPx) {
                val used = min(dy, heightPx - collapsedPx)
                job?.cancel(); heightPx -= used
                return Offset(0f, used)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            val v = available.y
            val atFull = heightPx >= fullPx - 1f
            if ((v < 0 && atFull) || (v > 0 && listCanScrollBackward())) return Velocity.Zero
            settle(v)
            return available
        }
    }
}

/**
 * Rounded Expressive bottom sheet. [content] gets a modifier that makes any header area draggable;
 * put the scrolling list last so it fills the remaining height.
 */
@Composable
fun ResizableSheet(
    controller: SheetController,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(dragModifier: Modifier) -> Unit,
) {
    val dragState = rememberDraggableState { controller.dragBy(it) }
    val dragModifier = Modifier.draggable(dragState, Orientation.Vertical, onDragStopped = { controller.settle(it) })
    val connection = remember(controller, listState) { controller.nestedScrollConnection { listState.canScrollBackward } }

    Surface(
        modifier = modifier.fillMaxWidth().layout { measurable, constraints ->
            val h = controller.heightPx.roundToInt().coerceIn(0, controller.fullPx.roundToInt())
            val p = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
            layout(p.width, h) { p.place(0, 0) }
        },
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.nestedScroll(connection)) {
            Box(Modifier.fillMaxWidth().then(dragModifier).height(28.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
            }
            content(dragModifier)
        }
    }
}
