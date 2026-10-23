package com.lucas.arbitraje

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucas.arbitraje.auth.AuthManager
import com.lucas.arbitraje.ui.AppNav
import com.lucas.arbitraje.ui.ArbitrajeTheme
import com.lucas.arbitraje.ui.LoginScreen
import com.lucas.arbitraje.ui.Pitch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val auth = AuthManager.get(this)
        setContent {
            ArbitrajeTheme {
                Surface(Modifier.fillMaxSize(), color = Pitch) {
                    val user by auth.user.collectAsStateWithLifecycle()
                    if (user == null) LoginScreen() else AppNav()
                }
            }
        }
    }
}
