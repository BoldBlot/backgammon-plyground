package com.boldblot.backgammonplyground.ui.positions

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
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
import com.boldblot.backgammonplyground.ui.components.negativeChipColors

/**
 * The one layout of the lists in My positions (a folder, All positions, No folder, New errors, New Marked, Learned).
 * Portrait: the top bar holds only the back arrow and the [title]; the [options] (sort, filter) are a row under it
 * and the [actions] (one or two white buttons) a bar at the bottom. Landscape: a side column holds the options
 * first, then the actions, on the side of Settings → Board → side column ([railRight]).
 * While selecting, [selectionBar] replaces the top bar and the options and actions are hidden.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FolderScaffold(
    title: String, onBack: () -> Unit, railRight: Boolean,
    selectionBar: (@Composable () -> Unit)? = null,
    options: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val bars = selectionBar == null
    Scaffold(
        topBar = {
            selectionBar?.invoke() ?: TopAppBar(
                title = { Title(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            if (!landscape && bars && actions != null) {
                FlowRow(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) { actions() }
            }
        },
    ) { padding ->
        if (!landscape) {
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (bars && options != null) FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically,
                ) { options() }
                content(Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            Row(Modifier.padding(padding).fillMaxSize()) {
                val side: @Composable () -> Unit = {
                    if (bars && (options != null || actions != null)) Column(
                        Modifier.width(190.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        options?.invoke()
                        if (options != null && actions != null) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        actions?.invoke()
                    }
                }
                if (!railRight) side()
                content(Modifier.weight(1f).fillMaxHeight())
                if (railRight) side()
            }
        }
    }
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
