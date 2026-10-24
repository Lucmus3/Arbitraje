package com.lucas.arbitraje.ui

import androidx.compose.foundation.layout.heightIn
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.arbitraje.BuildConfig
import com.lucas.arbitraje.auth.AuthManager
import com.lucas.arbitraje.data.MatchStatus
import com.lucas.arbitraje.data.Repository
import com.lucas.arbitraje.update.UpdateInfo
import com.lucas.arbitraje.update.Updater
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val auth = remember { AuthManager.get(context) }
    val data by repo.data.collectAsStateWithLifecycle()
    val user by auth.user.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }

    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(Unit) { update = Updater.check() }

    fun installUpdate(info: UpdateInfo) {
        if (!Updater.canInstall(context)) {
            Toast.makeText(context, "Permití instalar apps desde Arbitraje y volvé a tocar Actualizar", Toast.LENGTH_LONG).show()
            context.startActivity(Updater.installPermissionIntent(context))
            return
        }
        scope.launch {
            progress = 0f
            try {
                val apk = Updater.download(context, info) { p -> progress = p }
                Updater.install(context, apk)
            } catch (e: Exception) {
                Toast.makeText(context, "Error al descargar: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                progress = null
            }
        }
    }

    val live = data.matches.lastOrNull { it.status != MatchStatus.FINISHED }

    Scaffold(
        containerColor = Pitch,
        topBar = {
            AppTopBar(title = "Arbitraje", actions = {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menú") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Buscar actualización") },
                        leadingIcon = { Icon(Icons.Filled.SystemUpdate, null) },
                        onClick = {
                            menu = false
                            scope.launch {
                                update = Updater.check()
                                if (update == null) Toast.makeText(context, "Ya tenés la última versión", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Cerrar sesión") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                        onClick = { menu = false; scope.launch { auth.signOut() } },
                    )
                }
            })
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Hola, ${user?.name ?: user?.email ?: ""}",
                style = MaterialTheme.typography.titleMedium,
                color = Muted,
            )

            update?.let { info ->
                Card(colors = CardDefaults.cardColors(containerColor = NavyLight, contentColor = Color.White)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Nueva versión ${info.versionName} disponible", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        val p = progress
                        if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                        else Button(onClick = { installUpdate(info) }, colors = grayButtonColors()) { Text("ACTUALIZAR", fontWeight = FontWeight.Bold) }
                    }
                }
            }

            if (live != null) {
                val h = repo.team(live.homeTeamId)
                val a = repo.team(live.awayTeamId)
                Card(
                    Modifier.fillMaxWidth().clickable { onNavigate("match/${live.id}") },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFC0392B), contentColor = Color.White),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Timer, null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Partido en curso", fontWeight = FontWeight.Bold)
                            Text("${h?.name ?: "?"} ${live.homeGoals} - ${live.awayGoals} ${a?.name ?: "?"}")
                        }
                        Icon(Icons.Filled.PlayArrow, null)
                    }
                }
            }

            Button(
                onClick = { onNavigate("newMatch") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                shape = MaterialTheme.shapes.large,
                colors = grayButtonColors(),
            ) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(32.dp))
                Spacer(Modifier.width(8.dp))
                Text("NUEVO PARTIDO", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeTile("Equipos", "${data.teams.size}", Icons.Filled.Groups, Modifier.weight(1f)) { onNavigate("teams") }
                HomeTile("Ligas", "${data.leagues.size}", Icons.Filled.EmojiEvents, Modifier.weight(1f)) { onNavigate("leagues") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeTile("Estadísticas", "", Icons.Filled.BarChart, Modifier.weight(1f)) { onNavigate("stats") }
                HomeTile("Historial", "${data.matches.count { it.finished }}", Icons.Filled.History, Modifier.weight(1f)) { onNavigate("history") }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Versión ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
    }
}

@Composable
private fun HomeTile(title: String, count: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.heightIn(min = 130.dp).clickable(onClick = onClick)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 130.dp).padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, Modifier.size(36.dp), tint = Accent)
            Column {
                if (count.isNotEmpty()) Text(count, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
