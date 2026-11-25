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
import com.boldblot.backgammonplyground.core.MAX_SEARCHED
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
    /** Landscape: two columns (the engine and the checker play, the cube and [extra]) and no long notes, to fit without scrolling. */
    landscape: Boolean = false,
    extra: (@Composable () -> Unit)? = null,
) {
    val small = MaterialTheme.typography.bodySmall
    val games = match && engine == EngineKind.PURETD
    if (landscape) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Engine", style = small)
                EngineChips(engine, { e ->
                    onEngine(e)
                    if (e != EngineKind.PURETD && (checker.search != null || cube.search != null)) onChange(checker.plain, Depth(cube.plies))
                })
                Text("Checker play depth", style = small, modifier = Modifier.padding(top = 4.dp))
                CheckerDepthPicker(checker, games, compact = true) { onChange(it, cube) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Cube depth", style = small)
                CubeDepthPicker(cube, games) { onChange(checker, it) }
                extra?.invoke()
            }
        }
        return
    }
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

/** A depth's search version: the plain plies (all moves or pruned) or fast. Legacy windows (±0.04, ±0.08) count as pruned. */
private fun versionOf(d: Depth) = when (d.filter) { MoveFilter.ALL -> MoveFilter.ALL; MoveFilter.FAST -> MoveFilter.FAST; else -> MoveFilter.WIDE }

/** The plies chips with a T-Roller chip after them ([games]): 0 = T-Roller. */
@Composable
private fun PlyOrTRoller(depth: Depth, games: Boolean, onPlies: (Int) -> Unit, onTRoller: () -> Unit) {
    val t = depth.search?.let { GameSearch.isTRoller(it) } == true
    EqualChips((1..Depth.MAX_SEARCH_PLIES).toList(), if (depth.search == null) depth.plies else -1, { "$it-ply" }, onPlies)
    // T-Roller on a line of its own (five chips in a row cut the names).
    if (games) EqualChips(listOf(0), if (t) 0 else -1, { "T-Roller" }, { onTRoller() })
}

/** T-Roller's number of games, with what it costs. */
@Composable
private fun TGames(search: GameSearch, cube: Boolean, onChange: (Int) -> Unit) {
    Text("Number of games", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    EqualChips(GameSearch.T_GAMES, search.games, { "$it" }, onChange)
    Text(
        "More games: more accurate, slower. 48 games take about " + (if (cube) "a fifth" else "a third") + " of 288's time.",
        style = MaterialTheme.typography.labelSmall,
    )
}

/**
 * Checker play depth: 1 to 4 plies, each (from 2-ply) in three versions, standard (every move), pruned and fast; and with
 * [games] (matches with PureTD) T-Roller, its number of games and T-gate.
 */
@Composable
fun CheckerDepthPicker(depth: Depth, games: Boolean, compact: Boolean = false, onChange: (Depth) -> Unit) {
    val version = versionOf(depth)
    PlyOrTRoller(depth, games, { n -> onChange(Depth(n, version)) }, { onChange(Depth(4, MoveFilter.WIDE, GameSearch.tRoller(48, cube = false))) })
    val search = depth.search
    if (search != null && GameSearch.isTRoller(search)) {
        TGames(search, cube = false) { n -> onChange(Depth(4, MoveFilter.WIDE, GameSearch.tRoller(n, false, GameSearch.gateOf(search)))) }
        Text("T-gate", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        EqualChips(GameSearch.TGate.entries, GameSearch.gateOf(search), { it.label }, { g -> onChange(Depth(4, MoveFilter.WIDE, GameSearch.tRoller(search.games, false, g))) })
        if (!compact) Text(
            GameSearch.gateOf(search).note + ". T3 and T2 play games only for close plays: faster, and the time varies by position.",
            style = MaterialTheme.typography.labelSmall,
        )
        return
    }
    if (search != null) Text("Now: ${search.label} (an older search; pick another to change it)", style = MaterialTheme.typography.labelSmall)
    if (search == null && depth.plies > 1) {
        Text("Search", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        EqualChips(VERSIONS, version, { versionLabel(it) }, { onChange(Depth(depth.plies, it)) })
        if (!compact) Text(versionNote(version, depth.plies), style = MaterialTheme.typography.labelSmall)
    }
    if (search == null && depth.plies == 4 && version != MoveFilter.FAST) {
        Text(FOUR_PLY_CHECKER_NOTE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
}

/** Cube depth: plies, from 2-ply standard or fast, and with [games] T-Roller and its number of games. */
@Composable
fun CubeDepthPicker(depth: Depth, games: Boolean, onChange: (Depth) -> Unit) {
    val fast = depth.filter == MoveFilter.FAST
    PlyOrTRoller(depth, games, { n -> onChange(Depth(n, if (fast) MoveFilter.FAST else MoveFilter.ALL)) }, { onChange(Depth(3, MoveFilter.WIDE, GameSearch.tRoller(48, cube = true))) })
    val search = depth.search
    if (search != null && GameSearch.isTRoller(search)) {
        TGames(search, cube = true) { n -> onChange(Depth(3, MoveFilter.WIDE, GameSearch.tRoller(n, true))) }
        return
    }
    if (search != null) Text("Now: ${search.label} (an older search; pick another to change it)", style = MaterialTheme.typography.labelSmall)
    if (search == null && depth.plies > 1) {
        EqualChips(listOf(MoveFilter.ALL, MoveFilter.FAST), if (fast) MoveFilter.FAST else MoveFilter.ALL, { if (it == MoveFilter.FAST) "Fast" else "Standard" }, { onChange(Depth(depth.plies, it)) })
        Text(
            if (fast) "Fast: a shallower search first; the full depth only when the decision is close." else "Standard: the full depth every time.",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** Under the depth pickers of match play with PureTD. */
const val GAME_SEARCH_NOTE = "T-Roller (Truncated Roller, matches with PureTD): checker play at 4-ply fast, then short played-out " +
    "games (7 turns) for the closest plays, valued half and half; cube decisions at 3-ply, then games of 11 turns when the decision " +
    "is close. In each game the first reply is chosen at 2-ply."

/** The search versions of 2 to 4 plies. */
val VERSIONS = listOf(MoveFilter.ALL, MoveFilter.WIDE, MoveFilter.FAST)

fun versionLabel(f: MoveFilter) = when (f) { MoveFilter.ALL -> "Standard"; MoveFilter.FAST -> "Fast"; else -> "Pruned" }

fun versionNote(f: MoveFilter, plies: Int): String = when (f) {
    MoveFilter.ALL -> "Standard: every move searched to full depth (slowest)."
    MoveFilter.FAST -> when (plies) {
        2 -> "Fast: up to 8 moves within 0.08 of the best searched at 2-ply."
        3 -> "Fast: 2-ply on up to 16 moves within 0.16, then 3-ply on 8 within 0.08."
        else -> "Fast: 2-ply on 16 moves, 3-ply on 8, then 4-ply on the 3 best within 0.04."
    }
    else -> PRUNING_NOTE
}

/** The checker search's two root filters, as offered in Settings and Analyze. */
val PRUNING = listOf(MoveFilter.WIDE, MoveFilter.ALL)

const val PRUNING_NOTE = "Pruned: the $MIN_SEARCHED best moves at 1-ply and any within 0.16 of the best (at most $MAX_SEARCHED) are searched to full depth."

/** Shown when 4-ply checker play is chosen (FlashBack tests, 2026-10-22). */
const val FOUR_PLY_CHECKER_NOTE =
    "4-ply checker play is for research only: about 10-25 times slower than 3-ply (minutes on doubles), " +
        "and on FlashBack tests against XG no more accurate than 3-ply (better on some positions, worse on others)."
