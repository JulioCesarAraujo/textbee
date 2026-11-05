package com.textbee.gateway.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.textbee.gateway.presentation.configuracao.ConfiguracaoScreen
import com.textbee.gateway.presentation.home.HomeScreen
import com.textbee.gateway.presentation.server.ServerConfigScreen
import com.textbee.gateway.presentation.theme.TextbeeGatewayTheme

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Configuracao : Screen("configuracao")
    data object ServerConfig : Screen("server_config")
}

/**
 * Registra Home, Configuração e Configuração do Servidor em qualquer NavHost.
 * Usado tanto pela [TextbeeNavGraph] (Activity standalone) quanto pela
 * NewMainActivity (onde a Home substitui o antigo Dashboard).
 *
 * @param homeRoute rota da Home no NavHost que hospeda o grafo
 */
fun NavGraphBuilder.textbeeGatewayGraph(
    navController: NavHostController,
    homeRoute: String = Screen.Home.route,
    onGetStartedClick: () -> Unit = {},
    onReconnectClick: () -> Unit = {},
    onNavigateToSmsFilters: (() -> Unit)? = null,
    onNavigateToDeviceHealth: (() -> Unit)? = null
) {
    composable(homeRoute) {
        TextbeeGatewayTheme {
            HomeScreen(
                viewModel = hiltViewModel(),
                onNavigateToServerConfig = { navController.navigate(Screen.ServerConfig.route) },
                onNavigateToConfiguracao = { navController.navigate(Screen.Configuracao.route) },
                onGetStartedClick = onGetStartedClick,
                onReconnectClick = onReconnectClick
            )
        }
    }

    composable(Screen.Configuracao.route) {
        TextbeeGatewayTheme {
            ConfiguracaoScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToServerConfig = { navController.navigate(Screen.ServerConfig.route) },
                onNavigateToSmsFilters = onNavigateToSmsFilters,
                onNavigateToDeviceHealth = onNavigateToDeviceHealth
            )
        }
    }

    composable(Screen.ServerConfig.route) {
        TextbeeGatewayTheme {
            ServerConfigScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
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
        textbeeGatewayGraph(navController = navController)
    }
}
