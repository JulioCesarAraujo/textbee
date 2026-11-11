package com.vernu.sms.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vernu.sms.AppConstants
import com.vernu.sms.helpers.HeartbeatManager
import com.vernu.sms.helpers.RecoveryPoll
import com.vernu.sms.helpers.SharedPreferenceHelper
import com.vernu.sms.helpers.VersionTracker
import com.vernu.sms.ui.dashboard.DashboardScreen
import com.vernu.sms.ui.messages.ComposeScreen
import com.vernu.sms.ui.messages.MessagesScreen
import com.vernu.sms.ui.onboarding.OnboardingActivity
import com.vernu.sms.ui.settings.DeviceHealthScreen
import com.vernu.sms.ui.settings.SMSFilterScreen
import com.vernu.sms.ui.settings.SettingsScreen
import com.vernu.sms.ui.theme.TextbeeTheme
import com.textbee.gateway.presentation.navigation.Screen
import com.textbee.gateway.presentation.navigation.textbeeGatewayGraph
import dagger.hilt.android.AndroidEntryPoint

enum class MainDestination(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    MESSAGES("Activity", Icons.Default.History),
    SETTINGS("Settings", Icons.Default.Settings)
}

@AndroidEntryPoint
class NewMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RecoveryPoll.runAsync(this, "app_open")
        if (VersionTracker.hasVersionChanged(this)) VersionTracker.reportVersionToServer(applicationContext)
        setContent {
            TextbeeTheme {
                val navController = rememberNavController()
                MainScaffold(
                    navController = navController,
                    onOpenOnboarding = { returning ->
                        startActivity(
                            Intent(this, OnboardingActivity::class.java)
                                .putExtra(OnboardingActivity.EXTRA_RETURNING_USER, returning)
                        )
                    },
                    onDisconnect = {
                        listOf(
                            AppConstants.SHARED_PREFS_DEVICE_ID_KEY,
                            AppConstants.SHARED_PREFS_API_KEY_KEY,
                            AppConstants.SHARED_PREFS_GATEWAY_ENABLED_KEY,
                            AppConstants.SHARED_PREFS_DEVICE_NAME_KEY,
                            AppConstants.SHARED_PREFS_LAST_HEARTBEAT_MS_KEY
                        ).forEach { key ->
                            SharedPreferenceHelper.clearSharedPreference(this, key)
                        }
                        HeartbeatManager.cancelHeartbeat(this)
                        startActivity(
                            Intent(this, OnboardingActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                        )
                    }
                )
            }
        }
    }
}

private const val GATEWAY_WELCOME_ROUTE = "gateway_welcome"

@Composable
private fun MainScaffold(
    navController: NavHostController,
    onOpenOnboarding: (returning: Boolean) -> Unit,
    onDisconnect: () -> Unit
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute !in setOf(
        "compose",
        "filters",
        "health",
        Screen.Configuracao.route,
        Screen.ServerConfig.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    MainDestination.values().forEach { dest ->
                        val selected = currentRoute == dest.name
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.name) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    dest.icon,
                                    contentDescription = dest.label,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    dest.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MainDestination.HOME.name,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Home: o dashboard real do aparelho. Esta tela só abre com o aparelho
            // já cadastrado (a SplashActivity manda os demais para o onboarding),
            // então a tela de boas-vindas não aparece mais aqui.
            composable(MainDestination.HOME.name) {
                DashboardScreen()
            }
            // Configuração e Servidor local; a tela de boas-vindas fica numa rota
            // que nenhum menu abre
            textbeeGatewayGraph(
                navController = navController,
                homeRoute = GATEWAY_WELCOME_ROUTE,
                onGetStartedClick = { onOpenOnboarding(false) },
                onReconnectClick = { onOpenOnboarding(true) },
                onNavigateToSmsFilters = { navController.navigate("filters") },
                onNavigateToDeviceHealth = { navController.navigate("health") }
            )
            composable(MainDestination.MESSAGES.name) {
                MessagesScreen(
                    onNavigateToCompose = { navController.navigate("compose") }
                )
            }
            composable(MainDestination.SETTINGS.name) {
                SettingsScreen(
                    onNavigateToFilters = { navController.navigate("filters") },
                    onNavigateToServerConfig = { navController.navigate(Screen.ServerConfig.route) },
                    onNavigateToHealth = { navController.navigate("health") },
                    onDisconnect = onDisconnect
                )
            }
            composable("compose") {
                ComposeScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("filters") {
                SMSFilterScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable("health") {
                DeviceHealthScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
