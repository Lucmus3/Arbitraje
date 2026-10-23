package com.lucas.arbitraje.auth

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.lucas.arbitraje.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class User(val email: String, val name: String?, val photoUrl: String?)

/**
 * Inicio de sesión con la cuenta de Google.
 * Si la app se compiló con un ID de cliente web de Google Cloud usa "Sign in with Google"
 * (Credential Manager); si no, cae al selector de cuentas de Google del teléfono.
 */
class AuthManager private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    private val _user = MutableStateFlow(loadUser())
    val user: StateFlow<User?> = _user.asStateFlow()

    val usesGoogleSignIn: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    private fun loadUser(): User? {
        val email = prefs.getString("email", null) ?: return null
        return User(email, prefs.getString("name", null), prefs.getString("photo", null))
    }

    private fun setUser(u: User?) {
        prefs.edit().apply {
            if (u == null) clear() else {
                putString("email", u.email); putString("name", u.name); putString("photo", u.photoUrl)
            }
        }.apply()
        _user.value = u
    }

    /** Login con Credential Manager. Lanza excepción con mensaje legible si falla. */
    suspend fun signInWithGoogle(activity: Activity) {
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(activity).getCredential(activity, request)
        val cred = result.credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val google = GoogleIdTokenCredential.createFrom(cred.data)
            setUser(User(google.id, google.displayName, google.profilePictureUri?.toString()))
        } else {
            error("Credencial no reconocida")
        }
    }

    /** Intent del selector de cuentas de Google del sistema (modo sin Google Cloud). */
    fun accountChooserIntent(): Intent =
        AccountManager.newChooseAccountIntent(null, null, arrayOf("com.google"), null, null, null, null)

    fun handleAccountChooserResult(data: Intent?) {
        val email = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME) ?: return
        setUser(User(email, null, null))
    }

    suspend fun signOut() {
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
        }
        setUser(null)
    }

    companion object {
        @Volatile private var instance: AuthManager? = null
        fun get(context: Context): AuthManager =
            instance ?: synchronized(this) { instance ?: AuthManager(context.applicationContext).also { instance = it } }
    }
}
