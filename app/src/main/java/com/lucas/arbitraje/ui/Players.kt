package com.lucas.arbitraje.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucas.arbitraje.data.Player
import com.lucas.arbitraje.data.Positions
import com.lucas.arbitraje.data.Team

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerEditDialog(player: Player?, onDismiss: () -> Unit, onSave: (Player) -> Unit, onDelete: (() -> Unit)? = null) {
    var name by remember { mutableStateOf(player?.name ?: "") }
    var number by remember { mutableStateOf(player?.number?.toString() ?: "") }
    var position by remember { mutableStateOf(player?.position) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (player == null) "Nuevo jugador" else "Editar jugador") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = number,
                    onValueChange = { v -> number = v.filter { it.isDigit() }.take(3) },
                    label = { Text("Número") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Posición", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Positions.forEach { p ->
                        FilterChip(selected = position == p, onClick = { position = if (position == p) null else p }, label = { Text(p) })
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Quitar del plantel", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = {
                onSave(
                    Player(
                        id = player?.id ?: com.lucas.arbitraje.data.newId(),
                        name = name.trim(),
                        number = number.toIntOrNull(),
                        position = position,
                    )
                )
            }) { Text("Guardar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Lista del plantel para editar dentro del diálogo del equipo. */
@Composable
fun RosterEditor(players: List<Player>, onChange: (List<Player>) -> Unit) {
    var editing by remember { mutableStateOf<Player?>(null) }
    var adding by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Plantel (${players.size})", style = MaterialTheme.typography.labelLarge)
        players.sortedWith(compareBy({ it.number ?: Int.MAX_VALUE }, { it.name.lowercase() })).forEach { p ->
            TextButton(onClick = { editing = p }, modifier = Modifier.fillMaxWidth()) {
                Text(p.number?.toString() ?: "-", Modifier.width(36.dp), fontWeight = FontWeight.Bold)
                Text(p.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                p.position?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Agregar jugador") }
    }
    if (adding) PlayerEditDialog(null, onDismiss = { adding = false }, onSave = { onChange(players + it); adding = false })
    editing?.let { e ->
        PlayerEditDialog(
            e,
            onDismiss = { editing = null },
            onSave = { p -> onChange(players.map { if (it.id == p.id) p else it }); editing = null },
            onDelete = { onChange(players.filterNot { it.id == e.id }); editing = null },
        )
    }
}

/**
 * Pregunta quién fue: se elige del plantel o se escribe el nombre en el momento.
 * onPick recibe el jugador (o null = sin especificar) y si hay que guardarlo en el plantel.
 */
@Composable
fun PlayerPickerDialog(
    title: String,
    team: Team?,
    onDismiss: () -> Unit,
    onPick: (player: Player?, isNew: Boolean) -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    var save by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (team != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamBadge(team, 28.dp); Spacer(Modifier.width(8.dp)); Text(team.name, fontWeight = FontWeight.Bold)
                }
                team?.roster?.forEach { p ->
                    OutlinedButton(onClick = { onPick(p, false) }, modifier = Modifier.fillMaxWidth()) {
                        Text(p.number?.toString() ?: "-", Modifier.width(36.dp), fontWeight = FontWeight.Bold)
                        Text(p.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (team?.roster.isNullOrEmpty()) {
                    Text("Este equipo no tiene plantel cargado. Escribí el nombre:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Nombre o número") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                if (typed.isNotBlank() && team != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = save, onCheckedChange = { save = it })
                    Text("Guardar en el plantel")
                }
            }
        },
        confirmButton = {
            Button(enabled = typed.isNotBlank(), onClick = {
                val t = typed.trim()
                val num = t.toIntOrNull()
                onPick(Player(name = if (num != null) "Jugador $num" else t, number = num), save && team != null)
            }) { Text("Listo") }
        },
        dismissButton = { TextButton(onClick = { onPick(null, false) }) { Text("Sin especificar") } },
    )
}
