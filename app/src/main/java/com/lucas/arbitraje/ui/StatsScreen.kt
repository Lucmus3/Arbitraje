package com.lucas.arbitraje.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.arbitraje.data.CardColor
import com.lucas.arbitraje.data.topScorers
import com.lucas.arbitraje.data.EventType
import com.lucas.arbitraje.data.Repository
import com.lucas.arbitraje.data.teamStats

@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val data by repo.data.collectAsStateWithLifecycle()
    val finished = data.matches.filter { it.finished }
    val totalGoals = finished.sumOf { it.homeGoals + it.awayGoals }
    val cardTotals = CardColor.active.associateWith { c ->
        finished.sumOf { m -> m.events.count { it.type == EventType.CARD && it.card == c } }
    }
    val stats = data.teams.map { teamStats(it, data.matches) }
        .sortedWith(compareByDescending<com.lucas.arbitraje.data.TeamStats> { it.won * 3 + it.drawn }.thenByDescending { it.goalsFor })

    Scaffold(containerColor = Pitch, topBar = { AppTopBar("Estadísticas", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth()) {
                    StatTile("Partidos", "${finished.size}", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    StatTile("Goles", "$totalGoals", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    StatTile(
                        "Goles/partido",
                        if (finished.isEmpty()) "-" else "%.1f".format(totalGoals.toFloat() / finished.size),
                        Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.size(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    CardColor.active.forEachIndexed { i, c ->
                        if (i > 0) Spacer(Modifier.width(8.dp))
                        StatTile(c.label, "${cardTotals[c] ?: 0}", Modifier.weight(1f), Color(c.argb))
                    }
                }
                Text("Por equipo", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                Card {
                    Column(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(30.dp))
                            Text("Equipo", Modifier.width(120.dp), fontWeight = FontWeight.Bold)
                            listOf("PJ", "G", "E", "P", "GF", "GC").forEach { Cell(it, bold = true) }
                            CardColor.active.forEach { c ->
                                Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(12.dp, 16.dp).clip(RoundedCornerShape(2.dp)).background(Color(c.argb)))
                                }
                            }
                        }
                        stats.forEach { s ->
                            HorizontalDivider()
                            Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                                TeamBadge(s.team, 24.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(s.team.name, Modifier.width(120.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Cell("${s.played}"); Cell("${s.won}"); Cell("${s.drawn}"); Cell("${s.lost}")
                                Cell("${s.goalsFor}"); Cell("${s.goalsAgainst}")
                                CardColor.active.forEach { c -> Cell("${s.cards[c] ?: 0}") }
                            }
                        }
                        if (stats.isEmpty()) Text("Sin equipos todavía.", Modifier.padding(8.dp))
                    }
                }
                val scorers = topScorers(data.teams, data.matches)
                Text("Goleadores", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                Card {
                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                        if (scorers.isEmpty()) Text("Todavía no hay goles con autor.", Modifier.padding(8.dp))
                        scorers.take(15).forEachIndexed { i, sc ->
                            if (i > 0) HorizontalDivider()
                            Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}", Modifier.width(28.dp), fontWeight = FontWeight.Bold)
                                TeamBadge(sc.team, 24.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(sc.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${sc.goals}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier, accent: Color? = null) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            if (accent != null) Box(Modifier.size(14.dp, 20.dp).clip(RoundedCornerShape(3.dp)).background(accent))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
