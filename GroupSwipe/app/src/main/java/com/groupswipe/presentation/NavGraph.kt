package com.groupswipe.presentation

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.groupswipe.presentation.auth.AuthViewModel
import com.groupswipe.presentation.auth.LoginScreen
import com.groupswipe.presentation.auth.RegisterScreen
import com.groupswipe.presentation.friends.FriendsScreen
import com.groupswipe.presentation.history.HistoryScreen
import com.groupswipe.presentation.home.HomeScreen
import com.groupswipe.presentation.session.create.CreateSessionScreen
import com.groupswipe.presentation.session.results.ResultsScreen
import com.groupswipe.presentation.session.vote.VotingScreen

// ---- Definicja tras nawigacyjnych ----
object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val CREATE_SESSION = "create_session"
    const val VOTING = "voting/{sessionId}"
    const val RESULTS = "results/{sessionId}"
    const val FRIENDS = "friends"
    const val HISTORY = "history"

    fun voting(sessionId: String) = "voting/$sessionId"
    fun results(sessionId: String) = "results/$sessionId"
}

@Composable
fun GroupSwipeNavGraph() {
    val navController = rememberNavController()

    // Wspólny AuthViewModel zarządza stanem autentykacji
    val authViewModel: AuthViewModel = hiltViewModel()
    val currentUser by authViewModel.currentUser.collectAsState()

    // Punkt startowy zależy od stanu logowania
    val startDestination = if (currentUser != null) Routes.HOME else Routes.LOGIN

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // ---- Autentykacja ----
        composable(Routes.LOGIN) {
            LoginScreen(
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) },
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                viewModel = authViewModel
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                onNavigateToLogin = { navController.popBackStack() },
                onRegisterSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                viewModel = authViewModel
            )
        }

        // ---- Główne ekrany ----
        composable(Routes.HOME) {
            HomeScreen(
                onCreateSession = { navController.navigate(Routes.CREATE_SESSION) },
                onJoinSession = { /* obsługiwane przez dialog w HomeScreen */ },
                onOpenSession = { sessionId ->
                    navController.navigate(Routes.voting(sessionId))
                },
                onFriends = { navController.navigate(Routes.FRIENDS) },
                onHistory = { navController.navigate(Routes.HISTORY) },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CREATE_SESSION) {
            CreateSessionScreen(
                onSessionCreated = { sessionId ->
                    navController.navigate(Routes.voting(sessionId)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.VOTING,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            VotingScreen(
                sessionId = sessionId,
                onVotingFinished = {
                    navController.navigate(Routes.results(sessionId)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.RESULTS,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            ResultsScreen(
                sessionId = sessionId,
                onBack = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.FRIENDS) {
            FriendsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.HISTORY) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }
    }
}
