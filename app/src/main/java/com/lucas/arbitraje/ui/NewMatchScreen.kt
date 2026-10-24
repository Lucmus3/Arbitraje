package com.lucas.arbitraje.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.arbitraje.data.League
import com.lucas.arbitraje.data.Match
import com.lucas.arbitraje.data.Repository

@Composable
fun NewMatchScreen(presetLeagueId: String?, onBack: () -> Unit, onStart: (String) -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val data by repo.data.collectAsStateWithLifecycle()

    var leagueId by remember { mutableStateOf(presetLeagueId) }
    var homeId by remember { mutableStateOf<String?>(null) }
    var awayId by remember { mutableStateOf<String?>(null) }
    var minutes by remember { mutableIntStateOf(45) }
    var periods by remember { mutableIntStateOf(2) }
    var creatingTeam by remember { mutableStateOf(false) }

    val league = data.leagues.find { it.id == leagueId }
    val teams = (if (league != null) data.teams.filter { it.id in league.teamIds } else data.teams).sortedBy { it.name.lowercase() }
    val home = teams.find { it.id == homeId }
    val away = teams.find { it.id == awayId }

    Scaffold(containerColor = Pitch, topBar = { AppTopBar("Nuevo partido", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val noLeague = League(id = "", name = "Amistoso (sin liga)")
            Picker(
                label = "Liga",
                options = listOf(noLeague) + data.leagues,
                selected = league ?: noLeague,
                optionLabel = { it.name },
                onSelect = { leagueId = it.id.ifEmpty { null }; homeId = null; awayId = null },
            )
            Picker("Local", teams.filter { it.id != awayId }, home, { it.name }, { homeId = it.id }, leading = { TeamBadge(it, 28.dp) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = { val h = homeId; homeId = awayId; awayId = h }) { Icon(Icons.Filled.SwapVert, "Invertir") }
            }
            Picker("Visitante", teams.filter { it.id != homeId }, away, { it.name }, { awayId = it.id }, leading = { TeamBadge(it, 28.dp) })
            Button(onClick = { creatingTeam = true }, colors = grayButtonColors(), shape = MaterialTheme.shapes.medium) {
                Icon(Icons.Filled.Add, null); Text("Crear equipo")
            }

            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Duración de cada tiempo", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 25, 30, 35, 40, 45).forEach { m ->
                    FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m'") })
                }
            }
            Text("Cantidad de tiempos", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(1, 2, 4).forEach { p ->
                    FilterChip(selected = periods == p, onClick = { periods = p }, label = { Text("$p") })
                }
            }
            } }

            Spacer(Modifier.height(8.dp))
            Button(
                enabled = home != null && away != null,
                onClick = {
                    val m = Match(
                        homeTeamId = home!!.id,
                        awayTeamId = away!!.id,
                        leagueId = league?.id,
                        periodMinutes = minutes,
                        periods = periods,
                    )
                    repo.upsertMatch(m)
                    onStart(m.id)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF4D03F), contentColor = Ink, disabledContainerColor = NavyLight, disabledContentColor = Muted),
            ) {
                Icon(Icons.Filled.PlayArrow, null)
                Text("IR A LA CANCHA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (creatingTeam) TeamEditDialog(null, onDismiss = { creatingTeam = false }, onSave = { t ->
        repo.upsertTeam(t)
        league?.let { repo.upsertLeague(it.copy(teamIds = it.teamIds + t.id)) }
        if (homeId == null) homeId = t.id else if (awayId == null) awayId = t.id
        creatingTeam = false
    })
}
