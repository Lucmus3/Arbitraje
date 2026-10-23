package com.lucas.arbitraje.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.arbitraje.data.League
import com.lucas.arbitraje.data.Repository
import com.lucas.arbitraje.data.Team
import com.lucas.arbitraje.data.standings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LeaguesScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val data by repo.data.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Pitch,
        topBar = { AppTopBar("Ligas", onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Nueva liga") })
        },
    ) { padding ->
        if (data.leagues.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Todavía no hay ligas.", color = Muted)
            }
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(data.leagues, key = { it.id }) { league ->
                val leader = standings(league, data.teams, data.matches).firstOrNull()
                Card(Modifier.fillMaxWidth().clickable { onOpen(league.id) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.EmojiEvents, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(league.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${league.teamIds.size} equipos" + (leader?.takeIf { it.played > 0 }?.let { " · puntero: ${it.team.name}" } ?: ""),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating) LeagueEditDialog(null, data.teams, onDismiss = { creating = false }) {
        repo.upsertLeague(it); creating = false
    }
}

@Composable
fun LeagueEditDialog(league: League?, teams: List<Team>, onDismiss: () -> Unit, onSave: (League) -> Unit) {
    var name by remember { mutableStateOf(league?.name ?: "") }
    var selected by remember { mutableStateOf(league?.teamIds?.toSet() ?: emptySet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (league == null) "Nueva liga" else "Editar liga") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Equipos", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                if (teams.isEmpty()) Text("Primero creá equipos en la sección Equipos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                teams.sortedBy { it.name.lowercase() }.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = if (t.id in selected) selected - t.id else selected + t.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(t.id in selected, { selected = if (it) selected + t.id else selected - t.id })
                        TeamBadge(t, 28.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(t.name)
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = {
                val ids = teams.map { it.id }.filter { it in selected }
                onSave(league?.copy(name = name.trim(), teamIds = ids) ?: League(name = name.trim(), teamIds = ids))
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun LeagueDetailScreen(leagueId: String, onBack: () -> Unit, onNewMatch: (String) -> Unit, onOpenMatch: (String) -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val data by repo.data.collectAsStateWithLifecycle()
    val league = data.leagues.find { it.id == leagueId }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    if (league == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val table = standings(league, data.teams, data.matches)
    val matches = data.matches.filter { it.leagueId == league.id && it.finished }.sortedByDescending { it.startedAt }
    val df = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }

    Scaffold(
        containerColor = Pitch,
        topBar = {
            AppTopBar(league.name, onBack) {
                IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, "Editar") }
                IconButton(onClick = { deleting = true }) { Icon(Icons.Filled.Delete, "Borrar") }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { onNewMatch(league.id) }, icon = { Icon(Icons.Filled.PlayArrow, null) }, text = { Text("Partido de liga") })
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)) {
            item {
                Text("Tabla de posiciones", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                Card {
                    Column(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp)) {
                        StandingsHeader()
                        table.forEachIndexed { i, r ->
                            HorizontalDivider()
                            Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                                Cell("${i + 1}", 28)
                                TeamBadge(r.team, 24.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(r.team.name, Modifier.width(120.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Cell("${r.played}"); Cell("${r.won}"); Cell("${r.drawn}"); Cell("${r.lost}")
                                Cell("${r.goalsFor}"); Cell("${r.goalsAgainst}"); Cell("${r.diff}")
                                Cell("${r.points}", bold = true)
                            }
                        }
                        if (table.isEmpty()) Text("Agregá equipos a la liga con el lápiz.", Modifier.padding(8.dp))
                    }
                }
                Text("Partidos jugados", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                if (matches.isEmpty()) Text("Ninguno todavía.", color = Muted)
            }
            items(matches, key = { it.id }) { m ->
                MatchRow(m, data.teams, df.format(Date(m.startedAt))) { onOpenMatch(m.id) }
            }
        }
    }

    if (editing) LeagueEditDialog(league, data.teams, onDismiss = { editing = false }) { repo.upsertLeague(it); editing = false }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("¿Borrar la liga ${league.name}?") },
        text = { Text("Los partidos jugados se conservan en el historial.") },
        confirmButton = { Button(onClick = { deleting = false; repo.deleteLeague(league.id) }) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancelar") } },
    )
}

@Composable
private fun StandingsHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Cell("#", 28); Spacer(Modifier.width(30.dp))
        Text("Equipo", Modifier.width(120.dp), fontWeight = FontWeight.Bold)
        listOf("PJ", "G", "E", "P", "GF", "GC", "DG", "Pts").forEach { Cell(it, bold = true) }
    }
}

@Composable
fun Cell(text: String, width: Int = 36, bold: Boolean = false) {
    Text(text, Modifier.width(width.dp), textAlign = TextAlign.Center, fontWeight = if (bold) FontWeight.Bold else null)
}
