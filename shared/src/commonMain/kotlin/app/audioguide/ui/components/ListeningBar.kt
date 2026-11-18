package app.audioguide.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.audioguide.domain.PlayerStatus
import app.audioguide.domain.formatClock
import app.audioguide.feature.listening.ListeningController
import app.audioguide.feature.listening.ListeningState
import app.audioguide.resources.Res
import app.audioguide.resources.artwork_listen
import app.audioguide.resources.artwork_listen_full
import app.audioguide.resources.player_back
import app.audioguide.resources.player_estimated
import app.audioguide.resources.player_forward
import app.audioguide.resources.player_pause
import app.audioguide.resources.player_resume
import app.audioguide.resources.stop
import app.audioguide.ui.AppIcons
import org.jetbrains.compose.resources.stringResource

/** Barre d'écoute : les boutons d'écoute (avec durée estimée) au repos, le lecteur pendant la lecture. */
@Composable
fun ListeningBar(state: ListeningState, controller: ListeningController) {
    if (state.isActive) PlayerBar(state, controller) else ListenButtons(state, controller)
}

/** Les écoutes, avec la durée estimée de chacune. */
@Composable
private fun ListenButtons(state: ListeningState, controller: ListeningController) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Sans version courte (cartel scanné, ancien favori), un seul bouton : tout lire.
        val hasShort = state.shortMillis != null
        ListenButton(
            isSpeaking = false,
            enabled = state.voiceReady,
            onPlay = { controller.play(full = !hasShort) },
            onStop = controller::stop,
            playLabel = listOfNotNull(stringResource(Res.string.artwork_listen), (state.shortMillis ?: state.fullMillis)?.let(::formatClock))
                .joinToString(" · "),
            modifier = Modifier.weight(1f),
        )
        if (hasShort && state.fullMillis != null) {
            TextButton(onClick = { controller.play(full = true) }, enabled = state.voiceReady) {
                Text(listOfNotNull(stringResource(Res.string.artwork_listen_full), formatClock(state.fullMillis!!)).joinToString(" · "))
            }
        }
    }
}

/** Lecteur : pause et reprise, ±10 s, curseur pour aller à un instant, temps écoulé et durée estimée. */
@Composable
private fun PlayerBar(state: ListeningState, controller: ListeningController) {
    val total = state.totalMillis.coerceAtLeast(1)
    // Pendant qu'on glisse le curseur, il suit le doigt ; la lecture ne saute qu'au relâchement.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val position = dragging ?: (state.positionMillis.toFloat() / total).coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Slider(
            value = position,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { controller.seekTo((it * total).toLong()) }
                dragging = null
            },
        )
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatClock((position * total).toLong()), style = MaterialTheme.typography.labelMedium)
            Text(
                stringResource(Res.string.player_estimated, formatClock(state.totalMillis)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextButton(onClick = { controller.skip(-SKIP_MILLIS) }) { Text(stringResource(Res.string.player_back)) }
            if (state.status == PlayerStatus.Playing) {
                Button(onClick = controller::pause) {
                    Icon(AppIcons.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.player_pause))
                }
            } else {
                Button(onClick = controller::resume) {
                    Icon(AppIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.player_resume))
                }
            }
            TextButton(onClick = { controller.skip(SKIP_MILLIS) }) { Text(stringResource(Res.string.player_forward)) }
        }
        TextButton(onClick = controller::stop, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(Res.string.stop))
        }
    }
}

private const val SKIP_MILLIS = 10_000L

