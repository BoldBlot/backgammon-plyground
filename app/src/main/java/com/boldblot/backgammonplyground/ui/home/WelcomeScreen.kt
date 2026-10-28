package com.boldblot.backgammonplyground.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boldblot.backgammonplyground.BuildConfig
import com.boldblot.backgammonplyground.PlyGroundApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A starting depth by how long the player will wait: checker play and cube decisions (4-ply checker play is research only). */
private data class WaitChoice(val title: String, val note: String, val checkerPlies: Int, val cubePlies: Int)

private val WAIT_CHOICES = listOf(
    WaitChoice("Zero wait", "1-ply: the network alone. Instant, the least accurate.", 1, 1),
    WaitChoice("Almost zero wait", "2-ply: looks one roll ahead.", 2, 2),
    WaitChoice("Minimum wait", "3-ply: two rolls ahead. Strong, with a short wait on most phones.", 3, 3),
    WaitChoice("Put me on hold", "4-ply cube decisions, 3-ply checker play: the most accurate. A cube decision can take several seconds.", 3, 4),
)

/**
 * Shown once: on a new install or the first start after an update, and again when the version name
 * changes (not for builds within a version); About opens it any time. What a ply is, and a starting
 * depth picked by how long the player will wait. Without a choice, a new install gets 1-ply, the
 * quickest; otherwise the player's depths are kept.
 */
@Composable
fun WelcomeScreen(app: PlyGroundApp, newInstall: Boolean, onSpeedTest: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf<WaitChoice?>(null) }
    LaunchedEffect(Unit) {
        // Show the player's current depths as chosen when they match a card.
        if (!newInstall) {
            val s = app.settings.settings.first()
            chosen = WAIT_CHOICES.firstOrNull { it.checkerPlies == s.checkerPlies && it.cubePlies == s.cubePlies }
        }
    }
    fun finish(then: () -> Unit) = scope.launch {
        app.settings.update {
            val s = it.copy(welcomeSeen = BuildConfig.VERSION_NAME)
            val age = chosen
            when {
                age != null -> s.copy(checkerPlies = age.checkerPlies, cubePlies = age.cubePlies)
                newInstall -> s.copy(checkerPlies = 1, cubePlies = 1)
                else -> s
            }
        }
        then()
    }
    Scaffold { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (newInstall) "Welcome to Backgammon PlyGround" else "Backgammon PlyGround ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "PlyGround analyzes your games with a backgammon engine: every move and cube decision gets an " +
                    "equity, and mistakes are shown as you play or in the review.",
            )
            Text("What is a ply?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "How far ahead the engine looks. At 1-ply it judges each move by the position right after it, " +
                    "with its neural network alone. 2-ply also tries every roll your opponent could throw and their " +
                    "best reply; each further ply looks one more roll ahead. Deeper analysis is stronger but much " +
                    "slower.",
            )
            Text("Do you want to wait for analysis during the game?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Longer analysis is more accurate.")
            for (age in WAIT_CHOICES) {
                val selected = chosen == age
                OutlinedCard(
                    onClick = { chosen = age },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(
                        if (selected) 2.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                    colors = if (selected) CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    else CardDefaults.outlinedCardColors(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected, onClick = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(age.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(age.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Text(
                if (newInstall) "If you don't choose, PlyGround analyzes at 1-ply, the quickest."
                else "If you don't choose, your analysis settings stay as they are.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "You'll see this screen once. To read it again or run the speed test (about a minute), go to " +
                    "About. You can change the depths any time in Settings → Analysis.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { finish(onDone) }) { Text("Continue") }
                OutlinedButton(onClick = { finish(onSpeedTest) }) { Text("Run the speed test") }
            }
        }
    }
}
