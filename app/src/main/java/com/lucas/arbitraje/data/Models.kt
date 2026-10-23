package com.lucas.arbitraje.data

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

data class Team(
    val id: String = newId(),
    val name: String,
    /** Ruta local del escudo copiado al almacenamiento interno, o null. */
    val shieldPath: String? = null,
    /** Color ARGB del equipo, se usa cuando no hay escudo. */
    val color: Long = 0xFF1E88E5,
    /** Plantel. Nullable porque los datos guardados antes de la 1.0.3 no lo tienen (Gson no usa los valores por defecto). */
    val players: List<Player>? = null,
) {
    val roster: List<Player> get() = players.orEmpty().sortedWith(compareBy({ it.number ?: Int.MAX_VALUE }, { it.name.lowercase() }))
}

val Positions = listOf("Arquero", "Defensor", "Mediocampista", "Delantero")

data class Player(
    val id: String = newId(),
    val name: String,
    val number: Int? = null,
    val position: String? = null,
) {
    val label: String get() = listOfNotNull(number?.let { "#$it" }, name).joinToString(" ")
}

data class League(
    val id: String = newId(),
    val name: String,
    val teamIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

enum class CardColor(val label: String, val argb: Long) {
    YELLOW("Amarilla", 0xFFF4D03F),
    RED("Roja", 0xFFC0392B),
    BLUE("Azul", 0xFF1E88E5),
    GREEN("Verde", 0xFF43A047);

    companion object {
        /** Tarjetas que se ofrecen en el partido. Azul y verde quedan para leer partidos viejos. */
        val active = listOf(YELLOW, RED)
    }
}

enum class EventType { GOAL, CARD }

enum class Side { HOME, AWAY }

enum class MatchStatus { READY, PLAYING, PAUSED, BREAK, FINISHED }

data class MatchEvent(
    val id: String = newId(),
    val type: EventType,
    /** Equipo al que corresponde, null si la tarjeta no se asignó. */
    val side: Side?,
    val period: Int,
    /** Milisegundos de juego dentro del período. */
    val elapsedMs: Long,
    val card: CardColor? = null,
    val player: String? = null,
    val playerId: String? = null,
)

data class Match(
    val id: String = newId(),
    val homeTeamId: String,
    val awayTeamId: String,
    val leagueId: String? = null,
    val periodMinutes: Int = 45,
    val periods: Int = 2,
    val homeGoals: Int = 0,
    val awayGoals: Int = 0,
    val events: List<MatchEvent> = emptyList(),
    /** Tiempo detenido (pausas) por período, en ms. */
    val stoppageMs: List<Long> = emptyList(),
    val startedAt: Long = System.currentTimeMillis(),
    val finished: Boolean = false,
    // Estado del reloj: se guarda en el partido para que sobreviva si se cierra la app.
    val status: MatchStatus = MatchStatus.READY,
    val currentPeriod: Int = 1,
    /** Tiempo jugado del período actual sin contar el tramo en curso. */
    val periodPlayedMs: Long = 0,
    /** Desde cuándo corre el reloj (null si está detenido). */
    val runningSince: Long? = null,
    /** Desde cuándo está pausado (null si no hay pausa en curso). */
    val pausedSince: Long? = null,
) {
    fun playedMs(now: Long): Long = periodPlayedMs + (runningSince?.let { now - it } ?: 0L)

    fun stoppageFor(period: Int, now: Long): Long {
        val base = stoppageMs.getOrNull(period - 1) ?: 0L
        return if (period == currentPeriod && pausedSince != null) base + (now - pausedSince) else base
    }

    private fun withStoppage(period: Int, add: Long): List<Long> {
        val list = stoppageMs.toMutableList()
        while (list.size < period) list.add(0L)
        list[period - 1] = list[period - 1] + add
        return list
    }

    fun start(now: Long) = when (status) {
        MatchStatus.READY, MatchStatus.BREAK -> copy(status = MatchStatus.PLAYING, runningSince = now)
        else -> this
    }

    fun pause(now: Long) = if (status == MatchStatus.PLAYING)
        copy(status = MatchStatus.PAUSED, periodPlayedMs = playedMs(now), runningSince = null, pausedSince = now)
    else this

    fun resume(now: Long) = if (status == MatchStatus.PAUSED)
        copy(
            status = MatchStatus.PLAYING,
            stoppageMs = withStoppage(currentPeriod, now - (pausedSince ?: now)),
            pausedSince = null,
            runningSince = now,
        )
    else this

    /** Cierra el período actual: pasa al entretiempo o termina el partido. */
    fun endPeriod(now: Long): Match {
        val closed = copy(
            stoppageMs = withStoppage(currentPeriod, pausedSince?.let { now - it } ?: 0L),
            periodPlayedMs = playedMs(now),
            runningSince = null,
            pausedSince = null,
        )
        return if (currentPeriod < periods)
            closed.copy(status = MatchStatus.BREAK, currentPeriod = currentPeriod + 1, periodPlayedMs = 0)
        else closed.finish(now)
    }

    fun finish(now: Long) = copy(
        status = MatchStatus.FINISHED,
        finished = true,
        stoppageMs = if (pausedSince != null) withStoppage(currentPeriod, now - pausedSince) else stoppageMs,
        periodPlayedMs = playedMs(now),
        runningSince = null,
        pausedSince = null,
    )
}

data class AppData(
    val teams: List<Team> = emptyList(),
    val leagues: List<League> = emptyList(),
    val matches: List<Match> = emptyList(),
)

data class StandingRow(
    val team: Team,
    var played: Int = 0,
    var won: Int = 0,
    var drawn: Int = 0,
    var lost: Int = 0,
    var goalsFor: Int = 0,
    var goalsAgainst: Int = 0,
) {
    val points get() = won * 3 + drawn
    val diff get() = goalsFor - goalsAgainst
}

fun standings(league: League, teams: List<Team>, matches: List<Match>): List<StandingRow> {
    val rows = league.teamIds.mapNotNull { id -> teams.find { it.id == id } }
        .associate { it.id to StandingRow(it) }
    matches.filter { it.leagueId == league.id && it.finished }.forEach { m ->
        val h = rows[m.homeTeamId] ?: return@forEach
        val a = rows[m.awayTeamId] ?: return@forEach
        h.played++; a.played++
        h.goalsFor += m.homeGoals; h.goalsAgainst += m.awayGoals
        a.goalsFor += m.awayGoals; a.goalsAgainst += m.homeGoals
        when {
            m.homeGoals > m.awayGoals -> { h.won++; a.lost++ }
            m.homeGoals < m.awayGoals -> { a.won++; h.lost++ }
            else -> { h.drawn++; a.drawn++ }
        }
    }
    return rows.values.sortedWith(
        compareByDescending<StandingRow> { it.points }
            .thenByDescending { it.diff }
            .thenByDescending { it.goalsFor }
            .thenBy { it.team.name }
    )
}

data class TeamStats(
    val team: Team,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val cards: Map<CardColor, Int>,
)

fun teamStats(team: Team, matches: List<Match>): TeamStats {
    var p = 0; var w = 0; var d = 0; var l = 0; var gf = 0; var ga = 0
    val cards = mutableMapOf<CardColor, Int>()
    matches.filter { it.finished && (it.homeTeamId == team.id || it.awayTeamId == team.id) }.forEach { m ->
        val side = if (m.homeTeamId == team.id) Side.HOME else Side.AWAY
        val mine = if (side == Side.HOME) m.homeGoals else m.awayGoals
        val theirs = if (side == Side.HOME) m.awayGoals else m.homeGoals
        p++; gf += mine; ga += theirs
        when {
            mine > theirs -> w++
            mine < theirs -> l++
            else -> d++
        }
        m.events.filter { it.type == EventType.CARD && it.side == side && it.card != null }
            .forEach { val c = it.card!!; cards[c] = (cards[c] ?: 0) + 1 }
    }
    return TeamStats(team, p, w, d, l, gf, ga, cards)
}

data class Scorer(val name: String, val team: Team?, val goals: Int)

fun topScorers(teams: List<Team>, matches: List<Match>): List<Scorer> =
    matches.filter { it.finished }.flatMap { m ->
        m.events.filter { it.type == EventType.GOAL && !it.player.isNullOrBlank() }.map { e ->
            val teamId = if (e.side == Side.HOME) m.homeTeamId else m.awayTeamId
            val team = teams.find { it.id == teamId }
            val name = e.playerId?.let { id -> team?.players.orEmpty().find { it.id == id }?.label } ?: e.player!!
            Triple(teamId, e.playerId ?: name.lowercase(), name to team)
        }
    }.groupBy { it.first to it.second }
        .map { (_, list) -> Scorer(list.last().third.first, list.last().third.second, list.size) }
        .sortedWith(compareByDescending<Scorer> { it.goals }.thenBy { it.name.lowercase() })
