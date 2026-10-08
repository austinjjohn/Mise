package app.mise.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Gap between connected rows (Material 3 Expressive "segmented" list spacing). */
val SegmentedGap: Dp = 2.dp

/**
 * Expressive connected list row: the first and last rows get big outer corners, rows in between
 * get small ones, so a list reads as one grouped surface. Stand-in for the library's
 * SegmentedListItem, which isn't in the Material 3 alpha this project is pinned to.
 */
@Composable
fun SegmentedItem(
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    headline: @Composable () -> Unit,
) {
    val big = 24.dp
    val small = 6.dp
    val shape = RoundedCornerShape(
        topStart = if (index == 0) big else small, topEnd = if (index == 0) big else small,
        bottomStart = if (index == count - 1) big else small, bottomEnd = if (index == count - 1) big else small,
    )
    Surface(onClick = onClick, shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        ListItem(
            headlineContent = headline,
            supportingContent = supporting,
            leadingContent = leading,
            trailingContent = trailing,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}
