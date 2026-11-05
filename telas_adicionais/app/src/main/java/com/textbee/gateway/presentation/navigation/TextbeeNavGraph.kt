package com.textbee.gateway.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.textbee.gateway.presentation.home.HomeScreen
import com.textbee.gateway.presentation.server.ServerConfigScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object ServerConfig : Screen("server_config")
}

@Composable
fun TextbeeNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = hiltViewModel(),
                onNavigateToServerConfig = {
                    navController.navigate(Screen.ServerConfig.route)
                },
                onGetStartedClick = {
                    // Navigate to onboarding / pairing flow
                },
                onReconnectClick = {
                    // Reconnect trigger
                }
            )
        }

        composable(Screen.ServerConfig.route) {
            ServerConfigScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
