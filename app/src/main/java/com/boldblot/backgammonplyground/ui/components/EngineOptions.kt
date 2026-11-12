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
import com.boldblot.backgammonplyground.core.GameSearch
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
 * [match] (a match position, with PureTD) adds the searches by played-out games ([GameSearch]).
 * [onChange] gets the new checker and cube depths.
 */
@Composable
fun AnalysisLevelPicker(
    engine: EngineKind, onEngine: (EngineKind) -> Unit,
    checker: Depth, cube: Depth, onChange: (Depth, Depth) -> Unit,
    modifier: Modifier = Modifier,
    match: Boolean = false,
) {
    val small = MaterialTheme.typography.bodySmall
    val games = match && engine == EngineKind.PURETD
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Engine", style = small)
        EngineChips(engine, { e ->
            onEngine(e)
            // The other engines play no games out: back to the plies before them.
            if (e != EngineKind.PURETD && (checker.search != null || cube.search != null)) onChange(checker.plain, Depth(cube.plies))
        })
        Text("Checker play depth", style = small, modifier = Modifier.padding(top = 4.dp))
        CheckerDepthPicker(checker, games) { onChange(it, cube) }
        Text("Cube depth", style = small, modifier = Modifier.padding(top = 4.dp))
        CubeDepthPicker(cube, games) { onChange(checker, it) }
        if (games) Text(GAME_SEARCH_NOTE, style = MaterialTheme.typography.labelSmall)
    }
}

/** Checker play depth: plies and pruning, and with [games] the searches by played-out games. */
@Composable
fun CheckerDepthPicker(depth: Depth, games: Boolean, onChange: (Depth) -> Unit) {
    val filter = if (depth.filter == MoveFilter.ALL) MoveFilter.ALL else MoveFilter.WIDE
    PlyChips(if (depth.search == null) depth.plies else 0, { onChange(Depth(it, filter)) })
    if (games) {
        val all = GameSearch.checker
        EqualChips<GameSearch?>(all.take(3), depth.search, { it?.label ?: "" }, { onChange(Depth(it!!.plies, MoveFilter.WIDE, it)) })
        EqualChips<GameSearch?>(all.drop(3), depth.search, { it?.label ?: "" }, { onChange(Depth(it!!.plies, MoveFilter.WIDE, it)) })
    }
    if (depth.search == null && depth.plies == 4) {
        Text(FOUR_PLY_CHECKER_NOTE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
    if (depth.search == null && depth.plies > 1) {
        Text("Pruning", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        EqualChips(PRUNING, filter, { if (it == MoveFilter.ALL) "Off" else "On" }, { onChange(depth.copy(filter = it)) })
        Text(PRUNING_NOTE, style = MaterialTheme.typography.labelSmall)
    }
}

/** Cube depth: plies, and with [games] the searches by played-out games. */
@Composable
fun CubeDepthPicker(depth: Depth, games: Boolean, onChange: (Depth) -> Unit) {
    PlyChips(if (depth.search == null) depth.plies else 0, { onChange(Depth(it)) })
    if (games) EqualChips<GameSearch?>(GameSearch.cubes, depth.search, { it?.label ?: "" }, { onChange(Depth(it!!.plies, MoveFilter.WIDE, it)) })
}

/** Under the played-out-games chips, wherever they are offered. */
const val GAME_SEARCH_NOTE = "X/YY/Z (experimental, matches with PureTD): X-ply first, then YY short played-out games of Z turns " +
    "each, for the best few plays or for a close cube decision. More games are slower and steadier. " +
    "\"4-ply, fast\" narrows the plays at 2-ply and 3-ply before 4-ply."

/** The checker search's two root filters, as offered in Settings and Analyze. */
val PRUNING = listOf(MoveFilter.WIDE, MoveFilter.ALL)

const val PRUNING_NOTE = "On: the $MIN_SEARCHED best moves at 1-ply and any within 0.16 of the best are searched to full depth. " +
    "Off: every move (slower)."

/** Shown when 4-ply checker play is chosen (FlashBack tests, 2026-10-22). */
const val FOUR_PLY_CHECKER_NOTE =
    "4-ply checker play is for research only: about 10-25 times slower than 3-ply (minutes on doubles), " +
        "and on FlashBack tests against XG no more accurate than 3-ply (better on some positions, worse on others)."
