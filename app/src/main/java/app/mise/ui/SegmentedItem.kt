package app.mise.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/** Gap between connected rows (Material 3 Expressive "segmented" list spacing). */
val SegmentedGap: Dp = ListItemDefaults.SegmentedGap

/** Expressive connected list row: big outer corners on the first and last rows, small ones between. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = modifier,
        colors = ListItemDefaults.segmentedColors(),
        leadingContent = leading,
        trailingContent = trailing,
        supportingContent = supporting,
        content = { headline() },
    )
}
