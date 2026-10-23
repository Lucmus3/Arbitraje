package com.lucas.arbitraje.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/** Guarda todos los datos de la app en un único JSON en el almacenamiento interno. */
class Repository private constructor(private val context: Context) {

    private val gson = Gson()
    private val file = File(context.filesDir, "datos.json")
    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private fun load(): AppData = try {
        if (file.exists()) gson.fromJson(file.readText(), AppData::class.java) ?: AppData() else AppData()
    } catch (e: Exception) {
        AppData()
    }

    @Synchronized
    private fun save(transform: (AppData) -> AppData) {
        _data.update(transform)
        val tmp = File(file.parentFile, "datos.json.tmp")
        tmp.writeText(gson.toJson(_data.value))
        tmp.renameTo(file)
    }

    fun team(id: String?): Team? = _data.value.teams.find { it.id == id }
    fun league(id: String?): League? = _data.value.leagues.find { it.id == id }
    fun match(id: String?): Match? = _data.value.matches.find { it.id == id }

    fun upsertTeam(team: Team) = save { d ->
        d.copy(teams = if (d.teams.any { it.id == team.id }) d.teams.map { if (it.id == team.id) team else it } else d.teams + team)
    }

    fun deleteTeam(id: String) = save { d ->
        d.teams.find { it.id == id }?.shieldPath?.let { File(it).delete() }
        d.copy(
            teams = d.teams.filterNot { it.id == id },
            leagues = d.leagues.map { l -> l.copy(teamIds = l.teamIds - id) },
        )
    }

    fun upsertLeague(league: League) = save { d ->
        d.copy(leagues = if (d.leagues.any { it.id == league.id }) d.leagues.map { if (it.id == league.id) league else it } else d.leagues + league)
    }

    fun deleteLeague(id: String) = save { d ->
        d.copy(
            leagues = d.leagues.filterNot { it.id == id },
            matches = d.matches.map { if (it.leagueId == id) it.copy(leagueId = null) else it },
        )
    }

    fun upsertMatch(match: Match) = save { d ->
        d.copy(matches = if (d.matches.any { it.id == match.id }) d.matches.map { if (it.id == match.id) match else it } else d.matches + match)
    }

    fun deleteMatch(id: String) = save { d -> d.copy(matches = d.matches.filterNot { it.id == id }) }

    /** Copia la imagen elegida a la carpeta interna, reducida a 512px, y devuelve la ruta. */
    fun importShield(uri: Uri, teamId: String): String? = try {
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val scale = 512f / maxOf(bmp.width, bmp.height)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val dir = File(context.filesDir, "escudos").apply { mkdirs() }
        val out = File(dir, "$teamId-${System.currentTimeMillis()}.png")
        out.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        out.absolutePath
    } catch (e: Exception) {
        null
    }

    companion object {
        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository =
            instance ?: synchronized(this) { instance ?: Repository(context.applicationContext).also { instance = it } }
    }
}
