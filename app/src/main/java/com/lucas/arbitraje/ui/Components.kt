package com.lucas.arbitraje.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lucas.arbitraje.data.Team
import java.io.File

val TeamColors = listOf(
    0xFF1E88E5, 0xFFE53935, 0xFF43A047, 0xFFFDD835, 0xFFFB8C00, 0xFF8E24AA,
    0xFF00ACC1, 0xFF6D4C41, 0xFF212121, 0xFFFAFAFA, 0xFFD81B60, 0xFF3949AB,
)

/** Escudo del equipo, o un círculo con sus iniciales si no tiene. */
@Composable
fun TeamBadge(team: Team?, size: Dp, modifier: Modifier = Modifier) {
    if (team?.shieldPath != null && File(team.shieldPath).exists()) {
        AsyncImage(
            model = File(team.shieldPath),
            contentDescription = team.name,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size),
        )
    } else {
        val c = Color(team?.color ?: 0xFF607D8B)
        Box(
            modifier
                .size(size)
                .clip(CircleShape)
                .background(c)
                .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initials(team?.name ?: "?"),
                color = if (c.brightness() > 0.6f) Color.Black else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.36f).sp,
            )
        }
    }
}

fun Color.brightness(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

fun formatClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Pitch),
    )
}

/** Selector simple tipo "combo". */
@Composable
fun <T> Picker(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable (T) -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedCard(Modifier.fillMaxWidth().clickable { open = true }) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selected != null && leading != null) { leading(selected); Spacer(Modifier.width(8.dp)) }
                    Text(selected?.let(optionLabel) ?: "Elegir…", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Icon(Icons.Filled.ArrowDropDown, null)
                }
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(optionLabel(o)) },
                    leadingIcon = if (leading != null) { { leading(o) } } else null,
                    onClick = { onSelect(o); open = false },
                )
            }
        }
    }
}
