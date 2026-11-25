package com.boldblot.backgammonplyground.ui.positions

import android.content.res.Configuration
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.boldblot.backgammonplyground.ui.components.negativeChipColors

/**
 * The one layout of the lists in My positions (a folder, All positions, No folder, New errors, New Marked, Learned).
 * Portrait: the top bar holds the back arrow and the [title] (and [topActions], a help button); the [options]
 * (sort, filter) are a row under it and the [actions] (white buttons) a bar at the bottom. Landscape: a side column
 * holds the options first, then the actions, on the side of Settings → Board → side column ([railRight]).
 * Selecting keeps the layout: the screen passes the selection's actions ([SelectionActions]) as [actions].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FolderScaffold(
    title: String, onBack: () -> Unit, railRight: Boolean,
    topActions: (@Composable RowScope.() -> Unit)? = null,
    snackbar: androidx.compose.material3.SnackbarHostState? = null,
    options: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    val landscape = isLandscape()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Title(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { topActions?.invoke(this) },
            )
        },
        snackbarHost = { snackbar?.let { androidx.compose.material3.SnackbarHost(it) } },
        bottomBar = {
            if (!landscape && actions != null) {
                FlowRow(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) { actions() }
            }
        },
    ) { padding ->
        if (!landscape) {
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (options != null) FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically,
                ) { options() }
                content(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp))
            }
        } else {
            Row(Modifier.padding(padding).fillMaxSize()) {
                val side: @Composable () -> Unit = {
                    if (options != null || actions != null) Column(
                        Modifier.width(190.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        options?.invoke()
                        if (options != null && actions != null) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        actions?.invoke()
                    }
                }
                if (!railRight) side()
                content(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp))
                if (railRight) side()
            }
        }
    }
}

/** The actions while selecting: how many are selected, All / None, the list's own [extra] actions, Done. */
@Composable
fun SelectionActions(count: Int, allSelected: Boolean, onAll: () -> Unit, onDone: () -> Unit, extra: @Composable () -> Unit) {
    Text("$count selected", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 4.dp))
    FolderAction(if (allSelected) "None" else "All", null, onClick = onAll)
    extra()
    FolderAction("Done", Icons.Default.Close, onClick = onDone)
}

/** A sort order chip: the current order, a menu of the others. */
@Composable
fun <T> SortChip(entries: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = false, onClick = { open = true }, label = { Text(label(selected), maxLines = 1) },
            leadingIcon = { Icon(Icons.Default.SwapVert, "Sort order") },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            entries.forEach { o ->
                DropdownMenuItem(
                    text = { Text(label(o)) },
                    leadingIcon = { if (o == selected) Icon(Icons.Default.Check, null) },
                    onClick = { open = false; onSelect(o) },
                )
            }
        }
    }
}

/** A white action button of the action bar (or the side column). */
@Composable
fun FolderAction(text: String, icon: ImageVector?, enabled: Boolean = true, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick, enabled = enabled, label = { Text(text, maxLines = 1) }, colors = negativeChipColors(), border = null,
        leadingIcon = icon?.let { { Icon(it, null, Modifier.size(AssistChipDefaults.IconSize)) } },
    )
}

/** Whether the screen is in landscape. */
@Composable
fun isLandscape(): Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/** The board of a list row: the same size in every list, 120 dp wide in portrait and 160 dp in landscape. */
@Composable
fun listBoardModifier(): Modifier = Modifier.width(if (isLandscape()) 160.dp else 120.dp).aspectRatio(1.38f)

/**
 * One row of a list of My positions (a folder, New errors, New Marked, Learned): the board at the left, the [title]
 * (the play) with its error [pill], the [lines] under it; the [footer]'s tags at the bottom, wrapping before
 * the [trailing] mark (a quiz verdict) and the [actions] (small icon buttons) pinned to the bottom
 * right corner, hidden while selecting ([selected] not null).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    selected: Boolean?, onClick: () -> Unit, onLongClick: (() -> Unit)?,
    board: @Composable (Modifier) -> Unit,
    title: String, pill: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    /** Under the board and the lines, the row's full width (a position's tags). */
    footer: (@Composable () -> Unit)? = null,
    lines: @Composable ColumnScope.() -> Unit,
) {
    val showActions = actions != null && selected == null
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).combinedClickable(onLongClick = onLongClick, onClick = onClick)) {
        Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(8.dp)) {
                if (selected != null) Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.align(Alignment.CenterVertically).padding(end = 6.dp))
                board(listBoardModifier())
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        pill?.invoke()
                    }
                    lines()
                }
            }
        }
        // The tags across the row, wrapping before the [trailing] mark and the [actions] in the bottom right corner.
        if (footer != null || trailing != null || showActions) Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f).padding(bottom = 4.dp)) { footer?.invoke() }
            trailing?.invoke()
            if (showActions) Row(content = actions!!)
        }
        }
    }
}

private val ROW_BUTTON = 40.dp

/** A button of a list row: always the same size, whatever the row holds. */
@Composable
fun RowAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(ROW_BUTTON)) { Icon(icon, description, Modifier.size(22.dp)) }
}

/** The line of a list that shows nothing: why (the [filtered] variant when a filter hides everything). */
@Composable
fun EmptyListText(empty: String, filtered: Boolean, modifier: Modifier = Modifier) {
    Text(if (filtered) "Nothing passes the filter." else empty, modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The list is loading. */
@Composable
fun ListLoading(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator() }
}

/** After a row was deleted at once: "[message]" with Undo for a few seconds; [undo] puts it back. */
fun offerUndo(scope: kotlinx.coroutines.CoroutineScope, snackbar: androidx.compose.material3.SnackbarHostState, message: String, undo: suspend () -> Unit) {
    scope.launch {
        snackbar.currentSnackbarData?.dismiss()
        val r = snackbar.showSnackbar(message, actionLabel = "Undo", duration = androidx.compose.material3.SnackbarDuration.Short)
        if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) undo()
    }
}

/** The ids of the list a position was opened from, in the list's order (filtered and sorted), for Previous / Next in it. */
object BrowseList {
    var ids: List<Long> = emptyList()
}
