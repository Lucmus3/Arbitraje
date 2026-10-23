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
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import com.lucas.arbitraje.data.Player
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
    // Gol recién sumado al que falta asignarle autor.
    var askScorer by remember { mutableStateOf<Pair<Side, String>?>(null) }
    // Tarjeta con equipo elegido a la que falta asignarle jugador.
    var askCardPlayer by remember { mutableStateOf<Triple<CardColor, Side, Long>?>(null) }
    // Cambio: primero el equipo, después quién sale y quién entra.
    var subChooseTeam by remember { mutableStateOf(false) }
    var subOut by remember { mutableStateOf<Pair<Side, Long>?>(null) }
    var subIn by remember { mutableStateOf<Triple<Side, Long, Player?>?>(null) }
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
            askScorer = side to ev.id
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

    Box(Modifier.fillMaxSize().background(Navy)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("ASISTENTE ARBITRAL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f).padding(start = 4.dp))
                if (match.status == MatchStatus.PLAYING || match.status == MatchStatus.PAUSED) {
                    IconButton(onClick = { confirmEndPeriod = true }) { Icon(Icons.Filled.Flag, "Terminar período", tint = Color.White) }
                }
                IconButton(onClick = { confirmExit = true }) { Icon(Icons.Filled.Close, "Salir", tint = Color.White) }
            }
            ClockBar(
                match = match,
                played = played,
                regulationMs = regulationMs,
                stoppage = stoppage,
                onStart = { update(match.start(System.currentTimeMillis())) },
                onPause = { update(match.pause(System.currentTimeMillis())) },
                onResume = { update(match.resume(System.currentTimeMillis())) },
                home = home,
                away = away,
            )
            // Pantalla dividida: un equipo por mitad.
            if (landscape) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TeamHalf(home, match.homeGoals, Modifier.weight(1f).fillMaxHeight(), { goal(Side.HOME, 1) }, { goal(Side.HOME, -1) })
                    TeamHalf(away, match.awayGoals, Modifier.weight(1f).fillMaxHeight(), { goal(Side.AWAY, 1) }, { goal(Side.AWAY, -1) })
                }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TeamHalf(home, match.homeGoals, Modifier.weight(1f).fillMaxWidth(), { goal(Side.HOME, 1) }, { goal(Side.HOME, -1) })
                    TeamHalf(away, match.awayGoals, Modifier.weight(1f).fillMaxWidth(), { goal(Side.AWAY, 1) }, { goal(Side.AWAY, -1) })
                }
            }
            CardBar(onCard = { flashCard = it; vibrate(context, 120) })
            Button(
                onClick = { subChooseTeam = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = grayButtonColors(),
            ) {
                Icon(Icons.Filled.SwapHoriz, null)
                Spacer(Modifier.width(8.dp))
                Text("CAMBIOS", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
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
            onAssign = { side ->
                askCardPlayer = Triple(card, side, match.playedMs(System.currentTimeMillis()))
                assignCard = null
            },
        )
    }

    fun teamOf(side: Side) = if (side == Side.HOME) home else away

    /** Guarda en el plantel al jugador escrito en el momento, si se pidió. */
    fun keep(side: Side, player: Player?, isNew: Boolean) {
        val team = teamOf(side)
        if (player != null && isNew && team != null) repo.addPlayer(team.id, player)
    }

    askScorer?.let { (side, eventId) ->
        PlayerPickerDialog(
            title = "¿Quién hizo el gol?",
            team = teamOf(side),
            onDismiss = { askScorer = null },
            onPick = { player, isNew ->
                keep(side, player, isNew)
                if (player != null) repo.match(match.id)?.let { m ->
                    update(m.copy(events = m.events.map { if (it.id == eventId) it.copy(player = player.label, playerId = player.id) else it }))
                }
                askScorer = null
            },
        )
    }

    if (subChooseTeam) {
        AlertDialog(
            onDismissRequest = { subChooseTeam = false },
            title = { Text("Cambio: ¿de qué equipo?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val elapsed = match.playedMs(System.currentTimeMillis())
                    TeamChoice(home) { subOut = Side.HOME to elapsed; subChooseTeam = false }
                    TeamChoice(away) { subOut = Side.AWAY to elapsed; subChooseTeam = false }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { subChooseTeam = false }) { Text("Cancelar") } },
        )
    }

    subOut?.let { (side, elapsed) ->
        PlayerPickerDialog(
            title = "Cambio: ¿quién sale?",
            team = teamOf(side),
            onDismiss = { subOut = null },
            onPick = { player, isNew ->
                keep(side, player, isNew)
                subIn = Triple(side, elapsed, player)
                subOut = null
            },
        )
    }

    subIn?.let { (side, elapsed, out) ->
        PlayerPickerDialog(
            title = "Cambio: ¿quién entra?",
            team = teamOf(side),
            excludeId = out?.id,
            onDismiss = { subIn = null },
            onPick = { player, isNew ->
                keep(side, player, isNew)
                val ev = MatchEvent(
                    type = EventType.SUB,
                    side = side,
                    period = match.currentPeriod,
                    elapsedMs = elapsed,
                    player = out?.label,
                    playerId = out?.id,
                    playerIn = player?.label,
                    playerInId = player?.id,
                )
                repo.match(match.id)?.let { m -> update(m.copy(events = m.events + ev)) }
                subIn = null
            },
        )
    }

    askCardPlayer?.let { (card, side, elapsed) ->
        PlayerPickerDialog(
            title = "Tarjeta ${card.label.lowercase()}: ¿a quién?",
            team = teamOf(side),
            onDismiss = { askCardPlayer = null },
            onPick = { player, isNew ->
                keep(side, player, isNew)
                val ev = MatchEvent(
                    type = EventType.CARD,
                    side = side,
                    period = match.currentPeriod,
                    elapsedMs = elapsed,
                    card = card,
                    player = player?.label,
                    playerId = player?.id,
                )
                repo.match(match.id)?.let { m -> update(m.copy(events = m.events + ev)) }
                askCardPlayer = null
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
    home: Team?,
    away: Team?,
) {
    val extra = played - regulationMs
    val paused = match.status == MatchStatus.PAUSED
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardGray)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // espacio igual al del botón para que el reloj quede centrado
        Spacer(Modifier.width(56.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (match.status) {
                    MatchStatus.READY -> "LISTO PARA EMPEZAR"
                    MatchStatus.BREAK -> "ENTRETIEMPO · SIGUE ${periodName(match.currentPeriod, match.periods).uppercase()}"
                    MatchStatus.PAUSED -> "PAUSADO · ${periodName(match.currentPeriod, match.periods).uppercase()}"
                    else -> periodName(match.currentPeriod, match.periods).uppercase()
                },
                color = if (paused) Color(0xFFC0392B) else InkSoft,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatClock(minOf(played, regulationMs)),
                    color = Ink,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (extra > 0) {
                    Text(
                        " +${formatClock(extra)}",
                        color = Color(0xFFC0392B),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
            }
            Text(
                "LOCAL ${match.homeGoals} — ${match.awayGoals} VISITANTE",
                color = Ink,
                style = MaterialTheme.typography.bodyMedium,
            )
            // Cuánto agregar: el tiempo detenido redondeado hacia arriba en minutos.
            val addMinutes = ceil(stoppage / 60_000.0).toInt()
            Text(
                "Detenido ${formatClock(stoppage)} · agregar +$addMinutes'",
                color = if (stoppage > 0) Color(0xFFB45309) else InkSoft,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (stoppage > 0) FontWeight.Bold else FontWeight.Normal,
            )
        }
        when (match.status) {
            MatchStatus.READY, MatchStatus.BREAK -> BigRoundButton(Icons.Filled.PlayArrow, "Iniciar", Navy, onStart)
            MatchStatus.PLAYING -> BigRoundButton(Icons.Filled.Pause, "Pausar", Navy, onPause)
            MatchStatus.PAUSED -> BigRoundButton(Icons.Filled.PlayArrow, "Reanudar", Color(0xFFC0392B), onResume)
            MatchStatus.FINISHED -> Spacer(Modifier.width(56.dp))
        }
    }
}

@Composable
private fun BigRoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = Color.White),
    ) { Icon(icon, label, Modifier.size(34.dp)) }
}

@Composable
private fun TeamHalf(team: Team?, goals: Int, modifier: Modifier, onPlus: () -> Unit, onMinus: () -> Unit) {
    val teamColor = Color(team?.color ?: 0xFF607D8B)
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CardGray),
    ) {
        // franja con el color del equipo
        Box(Modifier.fillMaxHeight().width(6.dp).background(teamColor))
        val compact = maxHeight < 260.dp
        val scoreSize = (minOf(maxWidth.value * 0.5f, maxHeight.value * if (compact) 0.55f else 0.42f)).sp
        Column(
            Modifier.fillMaxSize().padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(team, if (compact) 36.dp else 48.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    (team?.name ?: "?").uppercase(),
                    color = Ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                FilledIconButton(
                    onClick = onMinus,
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = ButtonGray, contentColor = Ink),
                ) { Icon(Icons.Filled.Remove, "Restar gol") }
                Text(
                    "$goals",
                    color = Ink,
                    fontSize = scoreSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
                FilledIconButton(
                    onClick = onPlus,
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Navy, contentColor = Color.White),
                ) { Icon(Icons.Filled.Add, "Sumar gol", Modifier.size(44.dp)) }
            }
        }
    }
}

@Composable
private fun CardBar(onCard: (CardColor) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CardColor.active.forEach { c ->
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
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Tarjeta ${card.label}" },
    )
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
    // Solo el color, sin textos: se muestra a los jugadores.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(card.argb))
            // absorbe los toques para no sumar goles por accidente
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    )
}

@Composable
private fun AssignCardDialog(
    card: CardColor,
    home: Team?,
    away: Team?,
    onDismiss: () -> Unit,
    onAssign: (Side) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tarjeta ${card.label.lowercase()}: ¿para quién?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TeamChoice(home) { onAssign(Side.HOME) }
                TeamChoice(away) { onAssign(Side.AWAY) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("No registrar") } },
    )
}

@Composable
private fun ColumnScope.TeamChoice(team: Team?, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp), colors = grayButtonColors(), shape = MaterialTheme.shapes.medium) {
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
