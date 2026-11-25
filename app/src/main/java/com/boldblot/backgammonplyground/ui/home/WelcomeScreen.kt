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
import androidx.compose.runtime.collectAsState
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

/**
 * Shown once: when it has never been shown (a new install, or the app's data cleared); updates go
 * straight to the home screen. About opens it any time. What a ply is, and a starting
 * depth picked by how long the player will wait. The card matching the current depths is selected
 * (on a new install the defaults: Put me on hold), and Continue applies the card selected.
 */
@Composable
fun WelcomeScreen(app: PlyGroundApp, newInstall: Boolean, onSpeedTest: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf<AnalysisBudget?>(null) }
    LaunchedEffect(Unit) { chosen = AnalysisBudget.of(app.settings.settings.first().analysisBudget) }
    fun finish(then: () -> Unit) = scope.launch {
        val b = chosen
        app.settings.update { it.copy(welcomeSeen = BuildConfig.VERSION_NAME, analysisBudget = b?.ms ?: it.analysisBudget, budgetAsked = true) }
        com.boldblot.backgammonplyground.AppLog.i("Welcome: analysis budget ${b?.ms} ms")
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
            Text("How long may an analysis take?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Longer analysis is more accurate. After this, a speed test (a few minutes) finds the best analysis that fits on this phone.")
            for (age in AnalysisBudget.entries) {
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
                "You'll see this screen once. To read it again or run the speed test, go to About. Deeper and longer analyses " +
                    "are in Settings → Analysis.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val settings by app.settings.settings.collectAsState(initial = null)
            val done = settings?.let(::speedTestLine)
            if (done != null) Text(done, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { finish(onSpeedTest) }) { Text("Continue to the speed test") }
                OutlinedButton(onClick = { finish(onDone) }) { Text("Skip") }
            }
        }
    }
}
