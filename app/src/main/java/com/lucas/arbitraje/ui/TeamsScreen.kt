package com.lucas.arbitraje.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.arbitraje.data.Repository
import com.lucas.arbitraje.data.Team
import com.lucas.arbitraje.data.newId

@Composable
fun TeamsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val data by repo.data.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Team?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Pitch,
        topBar = { AppTopBar("Equipos", onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Nuevo equipo") })
        },
    ) { padding ->
        if (data.teams.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Todavía no hay equipos.\nCreá uno con su escudo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(data.teams.sortedBy { it.name.lowercase() }, key = { it.id }) { team ->
                Card(Modifier.fillMaxWidth().clickable { editing = team }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TeamBadge(team, 48.dp)
                        Spacer(Modifier.width(16.dp))
                        Text(team.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (creating) TeamEditDialog(null, onDismiss = { creating = false }, onSave = { repo.upsertTeam(it); creating = false })
    editing?.let { t ->
        TeamEditDialog(
            t,
            onDismiss = { editing = null },
            onSave = { repo.upsertTeam(it); editing = null },
            onDelete = { repo.deleteTeam(t.id); editing = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeamEditDialog(team: Team?, onDismiss: () -> Unit, onSave: (Team) -> Unit, onDelete: (() -> Unit)? = null) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val id = remember { team?.id ?: newId() }
    var name by remember { mutableStateOf(team?.name ?: "") }
    var color by remember { mutableLongStateOf(team?.color ?: TeamColors.first()) }
    var shield by remember { mutableStateOf(team?.shieldPath) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) repo.importShield(uri, id)?.let { shield = it }
    }
    val preview = Team(id = id, name = name.ifBlank { "?" }, shieldPath = shield, color = color)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (team == null) "Nuevo equipo" else "Editar equipo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamBadge(preview, 72.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        OutlinedButton(onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(Icons.Filled.Image, null); Spacer(Modifier.width(6.dp)); Text("Escudo")
                        }
                        if (shield != null) TextButton(onClick = { shield = null }) { Text("Quitar escudo") }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Color", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TeamColors.forEach { c ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(if (c == color) 3.dp else 1.dp, if (c == color) Color.White else Color.Gray, CircleShape)
                                .clickable { color = c },
                        )
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(6.dp))
                        Text("Borrar equipo", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = {
                onSave(Team(id = id, name = name.trim(), shieldPath = shield, color = color))
            }) { Text("Guardar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
