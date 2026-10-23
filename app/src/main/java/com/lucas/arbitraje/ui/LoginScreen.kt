package com.lucas.arbitraje.ui

import androidx.compose.ui.graphics.Color
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.lucas.arbitraje.auth.AuthManager
import kotlinx.coroutines.launch

@Composable
fun LoginScreen() {
    val context = LocalContext.current
    val auth = remember { AuthManager.get(context) }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        auth.handleAccountChooserResult(it.data)
    }

    fun googleSignIn() {
        loading = true; error = null
        scope.launch {
            try {
                auth.signInWithGoogle(context as Activity)
            } catch (e: GetCredentialCancellationException) {
                // el usuario cerró el diálogo
            } catch (e: Exception) {
                error = "No se pudo iniciar sesión: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.SportsSoccer, null, Modifier.size(96.dp), tint = Color.White)
        Spacer(Modifier.height(16.dp))
        Text("ASISTENTE ARBITRAL", fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            "Administrá partidos, equipos y ligas",
            color = Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(48.dp))
        if (loading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { if (auth.usesGoogleSignIn) googleSignIn() else chooser.launch(auth.accountChooserIntent()) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.large,
                colors = grayButtonColors(),
            ) {
                Icon(Icons.Filled.AccountCircle, null)
                Spacer(Modifier.size(8.dp))
                Text("Entrar con Google", fontSize = 18.sp)
            }
            if (auth.usesGoogleSignIn) {
                TextButton(onClick = { chooser.launch(auth.accountChooserIntent()) }) {
                    Text("Elegir una cuenta del teléfono", color = Muted)
                }
            }
        }
        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
    }
}
