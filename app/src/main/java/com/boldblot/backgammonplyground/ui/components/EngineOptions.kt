package com.boldblot.backgammonplyground.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.boldblot.backgammonplyground.core.Depth
import com.boldblot.backgammonplyground.core.EngineKind
import com.boldblot.backgammonplyground.core.MIN_SEARCHED
import com.boldblot.backgammonplyground.core.MoveFilter

/** One chip per engine. */
@Composable
fun EngineChips(selected: EngineKind, onSelect: (EngineKind) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        EngineKind.entries.forEach { e ->
            FilterChip(selected = selected == e, onClick = { onSelect(e) }, label = { Text(e.label) })
        }
    }
}

/** A row of equally wide, single-line chips. */
@Composable
fun <T> EqualChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o ->
            FilterChip(
                selected = selected == o, onClick = { onSelect(o) }, modifier = Modifier.weight(1f),
                label = { Text(label(o), maxLines = 1, softWrap = false, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            )
        }
    }
}

/** XG plies 1 to [Depth.MAX_SEARCH_PLIES] as equal chips ("1-ply" … "4-ply"). */
@Composable
fun PlyChips(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) =
    EqualChips((1..Depth.MAX_SEARCH_PLIES).toList(), selected, { "$it-ply" }, onSelect, modifier)

/**
 * Engine and analysis level: checker play depth, the moves searched to that depth, and cube depth.
 * [onChange] gets the new checker depth and cube plies.
 */
@Composable
fun AnalysisLevelPicker(
    engine: EngineKind, onEngine: (EngineKind) -> Unit,
    checker: Depth, cubePlies: Int, onChange: (Depth, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val small = MaterialTheme.typography.bodySmall
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Engine", style = small)
        EngineChips(engine, onEngine)
        Text("Checker play depth", style = small, modifier = Modifier.padding(top = 4.dp))
        PlyChips(checker.plies, { onChange(checker.copy(plies = it), cubePlies) })
        if (checker.plies == 4) {
            Text(FOUR_PLY_CHECKER_NOTE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        if (checker.plies > 1) {
            Text("Pruning", style = small, modifier = Modifier.padding(top = 4.dp))
            EqualChips(PRUNING, if (checker.filter == MoveFilter.ALL) MoveFilter.ALL else MoveFilter.WIDE, { if (it == MoveFilter.ALL) "Off" else "On" }, { onChange(checker.copy(filter = it), cubePlies) })
            Text(PRUNING_NOTE, style = MaterialTheme.typography.labelSmall)
        }
        Text("Cube depth", style = small, modifier = Modifier.padding(top = 4.dp))
        PlyChips(cubePlies, { onChange(checker, it) })
    }
}

/** The checker search's two root filters, as offered in Settings and Analyze. */
val PRUNING = listOf(MoveFilter.WIDE, MoveFilter.ALL)

const val PRUNING_NOTE = "On: the $MIN_SEARCHED best moves at 1-ply and any within 0.16 of the best are searched to full depth. " +
    "Off: every move (slower)."

/** Shown when 4-ply checker play is chosen (FlashBack tests, 2026-10-22). */
const val FOUR_PLY_CHECKER_NOTE =
    "4-ply checker play is for research only: about 10-25 times slower than 3-ply (minutes on doubles), " +
        "and on FlashBack tests against XG no more accurate than 3-ply (better on some positions, worse on others)."
