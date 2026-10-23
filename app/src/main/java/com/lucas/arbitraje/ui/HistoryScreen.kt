package com.lucas.arbitraje.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.arbitraje.data.EventType
import com.lucas.arbitraje.data.Match
import com.lucas.arbitraje.data.MatchEvent
import com.lucas.arbitraje.data.Repository
import com.lucas.arbitraje.data.Side
import com.lucas.arbitraje.data.Team
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val data by repo.data.collectAsStateWithLifecycle()
    val df = remember { SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()) }
    val matches = data.matches.filter { it.finished }.sortedByDescending { it.startedAt }

    Scaffold(containerColor = Pitch, topBar = { AppTopBar("Historial", onBack) }) { padding ->
        if (matches.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No hay partidos terminados.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            items(matches, key = { it.id }) { m ->
                val league = data.leagues.find { it.id == m.leagueId }
                MatchRow(m, data.teams, df.format(Date(m.startedAt)) + (league?.let { " · ${it.name}" } ?: "")) { onOpen(m.id) }
            }
        }
    }
}

@Composable
fun MatchRow(m: Match, teams: List<Team>, subtitle: String, onClick: () -> Unit) {
    val h = teams.find { it.id == m.homeTeamId }
    val a = teams.find { it.id == m.awayTeamId }
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(h, 28.dp)
                Text(h?.name ?: "?", Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${m.homeGoals} - ${m.awayGoals}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(a?.name ?: "?", Modifier.weight(1f).padding(horizontal = 8.dp), textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TeamBadge(a, 28.dp)
            }
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun eventMinute(m: Match, e: MatchEvent): String {
    val reg = m.periodMinutes * 60_000L
    val base = (e.period - 1) * m.periodMinutes
    val minute = (minOf(e.elapsedMs, reg) / 60_000).toInt() + 1
    val extra = if (e.elapsedMs > reg) ((e.elapsedMs - reg) / 60_000).toInt() + 1 else 0
    val total = minOf(base + minute, base + m.periodMinutes)
    return if (extra > 0) "${base + m.periodMinutes}+$extra'" else "$total'"
}

@Composable
fun MatchDetailScreen(matchId: String, onBack: () -> Unit, onResume: (String) -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val data by repo.data.collectAsStateWithLifecycle()
    val m = data.matches.find { it.id == matchId }
    var deleting by remember { mutableStateOf(false) }
    if (m == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val h = data.teams.find { it.id == m.homeTeamId }
    val a = data.teams.find { it.id == m.awayTeamId }

    Scaffold(
        containerColor = Pitch,
        topBar = {
            AppTopBar("Resumen del partido", onBack) {
                IconButton(onClick = { deleting = true }) { Icon(Icons.Filled.Delete, "Borrar") }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        TeamBadge(h, 72.dp); Text(h?.name ?: "?", textAlign = TextAlign.Center)
                    }
                    Text("${m.homeGoals} - ${m.awayGoals}", fontSize = 48.sp, fontWeight = FontWeight.Black)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        TeamBadge(a, 72.dp); Text(a?.name ?: "?", textAlign = TextAlign.Center)
                    }
                }
                Spacer(Modifier.height(12.dp))
                val stop = m.stoppageMs.mapIndexed { i, ms -> "${periodName(i + 1, m.periods)}: ${formatClock(ms)}" }
                if (stop.isNotEmpty()) Text("Tiempo detenido · " + stop.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!m.finished) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onResume(m.id) }) { Icon(Icons.Filled.PlayArrow, null); Text("Volver al partido") }
                }
                Text("Eventos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                if (m.events.isEmpty()) Text("Sin eventos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(m.events.sortedWith(compareBy({ it.period }, { it.elapsedMs })), key = { it.id }) { e ->
                val team = when (e.side) { Side.HOME -> h; Side.AWAY -> a; null -> null }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(eventMinute(m, e), Modifier.width(64.dp), fontWeight = FontWeight.Bold)
                    if (e.type == EventType.GOAL) {
                        Icon(Icons.Filled.SportsSoccer, null, Modifier.size(22.dp))
                    } else {
                        Box(Modifier.size(width = 16.dp, height = 22.dp).clip(RoundedCornerShape(3.dp)).background(Color(e.card!!.argb)))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(if (e.type == EventType.GOAL) "Gol de ${team?.name ?: "?"}" else "Tarjeta ${e.card!!.label.lowercase()} · ${team?.name ?: "sin equipo"}")
                        e.player?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }

    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("¿Borrar este partido?") },
        text = { Text("También deja de contar para la liga y las estadísticas.") },
        confirmButton = { Button(onClick = { deleting = false; repo.deleteMatch(m.id) }) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancelar") } },
    )
}
