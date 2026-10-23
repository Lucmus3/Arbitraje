package com.lucas.arbitraje.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(nav, startDestination = "home") {
        composable("home") { HomeScreen(onNavigate = { nav.navigate(it) }) }
        composable("teams") { TeamsScreen(onBack = back) }
        composable("leagues") { LeaguesScreen(onBack = back, onOpen = { nav.navigate("league/$it") }) }
        composable("league/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            LeagueDetailScreen(
                leagueId = it.arguments?.getString("id").orEmpty(),
                onBack = back,
                onNewMatch = { id -> nav.navigate("newMatch?league=$id") },
                onOpenMatch = { id -> nav.navigate("matchDetail/$id") },
            )
        }
        composable(
            "newMatch?league={league}",
            arguments = listOf(navArgument("league") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) {
            NewMatchScreen(
                presetLeagueId = it.arguments?.getString("league"),
                onBack = back,
                onStart = { id -> nav.navigate("match/$id") { popUpTo("home") } },
            )
        }
        composable("match/{id}") {
            MatchScreen(
                matchId = it.arguments?.getString("id").orEmpty(),
                onExit = { nav.popBackStack("home", inclusive = false) },
                onFinished = { id -> nav.navigate("matchDetail/$id") { popUpTo("home") } },
            )
        }
        composable("matchDetail/{id}") {
            MatchDetailScreen(
                matchId = it.arguments?.getString("id").orEmpty(),
                onBack = back,
                onResume = { id -> nav.navigate("match/$id") { popUpTo("home") } },
            )
        }
        composable("history") { HistoryScreen(onBack = back, onOpen = { nav.navigate("matchDetail/$it") }) }
        composable("stats") { StatsScreen(onBack = back) }
    }
}
