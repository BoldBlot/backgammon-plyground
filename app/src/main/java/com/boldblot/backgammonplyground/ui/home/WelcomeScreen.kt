package com.boldblot.backgammonplyground.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boldblot.backgammonplyground.BuildConfig
import com.boldblot.backgammonplyground.PlyGroundApp
import kotlinx.coroutines.launch

/**
 * Shown on a new install and again when the version name changes (not for builds within a
 * version): what a ply is, and an offer of the engine speed test. Skipping it on a new install
 * sets 1-ply analysis, the quickest; after an update the player's own depths are kept.
 */
@Composable
fun WelcomeScreen(app: PlyGroundApp, newInstall: Boolean, onSpeedTest: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    fun finish(then: () -> Unit, quickest: Boolean = false) = scope.launch {
        app.settings.update {
            val s = it.copy(welcomeSeen = BuildConfig.VERSION_NAME)
            if (quickest) s.copy(checkerPlies = 1, cubePlies = 1) else s
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
                    "slower: on a phone, a 4-ply cube decision can take several seconds.",
            )
            Text("How deep should it look on your phone?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "The speed test times the engine on this phone at every depth (about a minute) and recommends the " +
                    "deepest settings that still answer quickly.",
            )
            Text(
                if (newInstall) "If you skip it, PlyGround analyzes at 1-ply, the quickest. "
                else "If you skip it, your analysis settings stay as they are. ",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "You can change the depths any time in Settings → Analysis, and run the speed test from there or from About.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { finish(onSpeedTest) }) { Text("Run the speed test") }
                OutlinedButton(onClick = { finish(onDone, quickest = newInstall) }) { Text("Skip") }
            }
        }
    }
}
