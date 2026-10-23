package com.lucas.arbitraje.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.arbitraje.data.CardColor
import com.lucas.arbitraje.data.EventType
import com.lucas.arbitraje.data.Match
import com.lucas.arbitraje.data.MatchEvent
import com.lucas.arbitraje.data.MatchStatus
import com.lucas.arbitraje.data.Repository
import com.lucas.arbitraje.data.Side
import com.lucas.arbitraje.data.Team
import kotlinx.coroutines.delay
import kotlin.math.ceil

private const val CARD_FLASH_SECONDS = 5

@Composable
fun MatchScreen(matchId: String, onExit: () -> Unit, onFinished: (String) -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val data by repo.data.collectAsStateWithLifecycle()
    val match = data.matches.find { it.id == matchId } ?: return
    val home = data.teams.find { it.id == match.homeTeamId }
    val away = data.teams.find { it.id == match.awayTeamId }

    // La pantalla no se apaga durante el partido.
    DisposableEffect(Unit) {
        val window = (context as Activity).window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Reloj: se recalcula a partir de timestamps, así no se desfasa.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(200)
        }
    }

    val played = match.playedMs(now)
    val regulationMs = match.periodMinutes * 60_000L
    val stoppage = match.stoppageFor(match.currentPeriod, now)

    // Aviso con vibración al cumplirse el tiempo reglamentario del período.
    var vibratedFor by remember { mutableIntStateOf(0) }
    LaunchedEffect(played >= regulationMs, match.currentPeriod, match.status) {
        if (match.status == MatchStatus.PLAYING && played >= regulationMs && vibratedFor != match.currentPeriod) {
            vibratedFor = match.currentPeriod
            vibrate(context, 800)
        }
    }

    fun update(m: Match) = repo.upsertMatch(m)

    var flashCard by remember { mutableStateOf<CardColor?>(null) }
    var assignCard by remember { mutableStateOf<CardColor?>(null) }
    var confirmEndPeriod by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    BackHandler { if (flashCard == null) confirmExit = true }

    fun goal(side: Side, delta: Int) {
        if (delta > 0) {
            val ev = MatchEvent(type = EventType.GOAL, side = side, period = match.currentPeriod, elapsedMs = played)
            update(
                match.copy(
                    homeGoals = match.homeGoals + if (side == Side.HOME) 1 else 0,
                    awayGoals = match.awayGoals + if (side == Side.AWAY) 1 else 0,
                    events = match.events + ev,
                )
            )
            vibrate(context, 60)
        } else {
            val current = if (side == Side.HOME) match.homeGoals else match.awayGoals
            if (current == 0) return
            val last = match.events.lastOrNull { it.type == EventType.GOAL && it.side == side }
            update(
                match.copy(
                    homeGoals = match.homeGoals - if (side == Side.HOME) 1 else 0,
                    awayGoals = match.awayGoals - if (side == Side.AWAY) 1 else 0,
                    events = if (last != null) match.events - last else match.events,
                )
            )
        }
    }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ClockBar(
                match = match,
                played = played,
                regulationMs = regulationMs,
                stoppage = stoppage,
                onStart = { update(match.start(System.currentTimeMillis())) },
                onPause = { update(match.pause(System.currentTimeMillis())) },
                onResume = { update(match.resume(System.currentTimeMillis())) },
                onEndPeriod = { confirmEndPeriod = true },
            )
            // Pantalla dividida: un equipo por mitad.
            if (landscape) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    TeamHalf(home, match.homeGoals, Modifier.weight(1f).fillMaxHeight(), { goal(Side.HOME, 1) }, { goal(Side.HOME, -1) })
                    Spacer(Modifier.width(2.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.4f)))
                    TeamHalf(away, match.awayGoals, Modifier.weight(1f).fillMaxHeight(), { goal(Side.AWAY, 1) }, { goal(Side.AWAY, -1) })
                }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    TeamHalf(home, match.homeGoals, Modifier.weight(1f).fillMaxWidth(), { goal(Side.HOME, 1) }, { goal(Side.HOME, -1) })
                    Spacer(Modifier.height(2.dp).fillMaxWidth().background(Color.White.copy(alpha = 0.4f)))
                    TeamHalf(away, match.awayGoals, Modifier.weight(1f).fillMaxWidth(), { goal(Side.AWAY, 1) }, { goal(Side.AWAY, -1) })
                }
            }
            CardBar(onCard = { flashCard = it; vibrate(context, 120) })
        }

        // Pantalla completa del color de la tarjeta durante 5 segundos.
        AnimatedVisibility(visible = flashCard != null, enter = fadeIn(), exit = fadeOut()) {
            val card = flashCard
            if (card != null) CardFlash(card) {
                flashCard = null
                assignCard = card
            }
        }
    }

    assignCard?.let { card ->
        AssignCardDialog(
            card = card,
            home = home,
            away = away,
            onDismiss = { assignCard = null },
            onAssign = { side, player ->
                val ev = MatchEvent(
                    type = EventType.CARD,
                    side = side,
                    period = match.currentPeriod,
                    elapsedMs = match.playedMs(System.currentTimeMillis()),
                    card = card,
                    player = player.ifBlank { null },
                )
                update(match.copy(events = match.events + ev))
                assignCard = null
            },
        )
    }

    if (confirmEndPeriod) {
        val last = match.currentPeriod >= match.periods
        AlertDialog(
            onDismissRequest = { confirmEndPeriod = false },
            title = { Text(if (last) "¿Terminar el partido?" else "¿Terminar el ${periodName(match.currentPeriod, match.periods)}?") },
            text = { Text("Tiempo detenido en este período: ${formatClock(stoppage)}") },
            confirmButton = {
                Button(onClick = {
                    confirmEndPeriod = false
                    val ended = match.endPeriod(System.currentTimeMillis())
                    update(ended)
                    if (ended.status == MatchStatus.FINISHED) onFinished(ended.id)
                }) { Text("Terminar") }
            },
            dismissButton = { TextButton(onClick = { confirmEndPeriod = false }) { Text("Cancelar") } },
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Salir del partido") },
            text = { Text("El partido queda guardado y podés volver desde el inicio. El reloj sigue corriendo si no lo pausás.") },
            confirmButton = {
                Button(onClick = { confirmExit = false; onExit() }) { Text("Salir") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmExit = false
                    val ended = match.finish(System.currentTimeMillis())
                    update(ended)
                    onFinished(ended.id)
                }) { Text("Finalizar partido") }
            },
        )
    }
}

fun periodName(period: Int, periods: Int): String = when {
    periods == 2 && period == 1 -> "1er tiempo"
    periods == 2 && period == 2 -> "2do tiempo"
    else -> "período $period"
}

@Composable
private fun ClockBar(
    match: Match,
    played: Long,
    regulationMs: Long,
    stoppage: Long,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEndPeriod: () -> Unit,
) {
    val extra = played - regulationMs
    val paused = match.status == MatchStatus.PAUSED
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (paused) Color(0xFF4E342E) else Color(0xFF111111))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                when (match.status) {
                    MatchStatus.READY -> "Listo para empezar"
                    MatchStatus.BREAK -> "Entretiempo · sigue ${periodName(match.currentPeriod, match.periods)}"
                    MatchStatus.PAUSED -> "PAUSADO · ${periodName(match.currentPeriod, match.periods)}"
                    else -> periodName(match.currentPeriod, match.periods).replaceFirstChar { it.uppercase() }
                },
                color = if (paused) Color(0xFFFFCC80) else Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatClock(minOf(played, regulationMs)),
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                if (extra > 0) {
                    Text(
                        " +${formatClock(extra)}",
                        color = Color(0xFFFFB74D),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            // Cuánto agregar: el tiempo detenido redondeado hacia arriba en minutos.
            val addMinutes = ceil(stoppage / 60_000.0).toInt()
            Text(
                "Detenido ${formatClock(stoppage)} · agregar +$addMinutes'",
                color = if (stoppage > 0) Color(0xFFFFCC80) else Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        when (match.status) {
            MatchStatus.READY, MatchStatus.BREAK -> BigRoundButton(Icons.Filled.PlayArrow, "Iniciar", Color(0xFF2E7D32), onStart)
            MatchStatus.PLAYING -> BigRoundButton(Icons.Filled.Pause, "Pausar", Color(0xFFEF6C00), onPause)
            MatchStatus.PAUSED -> BigRoundButton(Icons.Filled.PlayArrow, "Reanudar", Color(0xFF2E7D32), onResume)
            MatchStatus.FINISHED -> {}
        }
        if (match.status == MatchStatus.PLAYING || match.status == MatchStatus.PAUSED) {
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onEndPeriod) { Icon(Icons.Filled.Flag, "Terminar período", tint = Color.White) }
        }
    }
}

@Composable
private fun BigRoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(64.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = Color.White),
    ) { Icon(icon, label, Modifier.size(40.dp)) }
}

@Composable
private fun TeamHalf(team: Team?, goals: Int, modifier: Modifier, onPlus: () -> Unit, onMinus: () -> Unit) {
    val base = Color(team?.color ?: 0xFF607D8B)
    val bg = base.copy(alpha = 0.35f)
    BoxWithConstraints(modifier.background(Color.Black).background(bg).padding(12.dp)) {
        val compact = maxHeight < 260.dp
        val scoreSize = (minOf(maxWidth.value * 0.55f, maxHeight.value * if (compact) 0.6f else 0.45f)).sp
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(team, if (compact) 40.dp else 56.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    team?.name ?: "?",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                FilledIconButton(
                    onClick = onMinus,
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.15f), contentColor = Color.White),
                ) { Icon(Icons.Filled.Remove, "Restar gol") }
                Text(
                    "$goals",
                    color = Color.White,
                    fontSize = scoreSize,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                FilledIconButton(
                    onClick = onPlus,
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black),
                ) { Icon(Icons.Filled.Add, "Sumar gol", Modifier.size(44.dp)) }
            }
        }
    }
}

@Composable
private fun CardBar(onCard: (CardColor) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF111111)).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CardColor.entries.forEach { c ->
            CardButton(c, Modifier.weight(1f)) { onCard(c) }
        }
    }
}

@Composable
private fun RowScope.CardButton(card: CardColor, modifier: Modifier, onClick: () -> Unit) {
    val color = Color(card.argb)
    Box(
        modifier
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(card.label, color = if (color.brightness() > 0.6f) Color.Black else Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CardFlash(card: CardColor, onDone: () -> Unit) {
    var remaining by remember { mutableIntStateOf(CARD_FLASH_SECONDS) }
    LaunchedEffect(card) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
        onDone()
    }
    val color = Color(card.argb)
    val fg = if (color.brightness() > 0.6f) Color.Black else Color.White
    Box(
        Modifier
            .fillMaxSize()
            .background(color)
            // absorbe los toques para no sumar goles por accidente
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TARJETA ${card.label.uppercase()}", color = fg, fontSize = 36.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text("$remaining", color = fg.copy(alpha = 0.6f), fontSize = 72.sp, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onDone, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
            Icon(Icons.Filled.Close, "Cerrar", tint = fg)
        }
    }
}

@Composable
private fun AssignCardDialog(
    card: CardColor,
    home: Team?,
    away: Team?,
    onDismiss: () -> Unit,
    onAssign: (Side, String) -> Unit,
) {
    var player by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tarjeta ${card.label.lowercase()}: ¿para quién?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = player,
                    onValueChange = { player = it },
                    label = { Text("Jugador o número (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TeamChoice(home) { onAssign(Side.HOME, player) }
                TeamChoice(away) { onAssign(Side.AWAY, player) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("No registrar") } },
    )
}

@Composable
private fun ColumnScope.TeamChoice(team: Team?, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
        TeamBadge(team, 32.dp)
        Spacer(Modifier.width(12.dp))
        Text(team?.name ?: "?", Modifier.weight(1f))
    }
}

fun vibrate(context: Context, ms: Long) {
    try {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (_: Exception) {
    }
}
