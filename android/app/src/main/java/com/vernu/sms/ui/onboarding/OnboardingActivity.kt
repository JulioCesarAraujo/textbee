package com.vernu.sms.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.vernu.sms.ui.main.NewMainActivity
import com.vernu.sms.ui.onboarding.screens.*
import com.vernu.sms.ui.theme.TextbeeTheme
import com.textbee.gateway.presentation.navigation.textbeeGatewayGraph
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {

    companion object {
        /** Se presente, pula a Home e abre direto a tela de credenciais (true = reconectar dispositivo). */
        const val EXTRA_RETURNING_USER = "extra_returning_user"
    }

    private val viewModel: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val qrLauncher = registerForActivityResult(ScanContract()) { result ->
            result?.contents?.let { scanned ->
                viewModel.onQrScanned(scanned.trim())
            }
        }

        val skipHome = intent.hasExtra(EXTRA_RETURNING_USER)
        if (skipHome) {
            viewModel.setReturningUser(intent.getBooleanExtra(EXTRA_RETURNING_USER, false))
        }

        setContent {
            TextbeeTheme {
                val navController = rememberNavController()
                OnboardingNavGraph(
                    navController = navController,
                    viewModel = viewModel,
                    startDestination = if (skipHome) "credentials" else "welcome",
                    onExit = { finish() },
                    onScanQr = {
                        qrLauncher.launch(ScanOptions().apply {
                            setPrompt("Scan the QR code from textbee.dev/dashboard")
                            setBeepEnabled(true)
                            setOrientationLocked(false)
                        })
                    },
                    onComplete = {
                        val intent = Intent(this, NewMainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
private fun OnboardingNavGraph(
    navController: NavHostController,
    viewModel: OnboardingViewModel,
    startDestination: String,
    onExit: () -> Unit,
    onScanQr: () -> Unit,
    onComplete: () -> Unit
) {
    NavHost(navController = navController, startDestination = startDestination) {
        // Nova Home (substitui o antigo WelcomeScreen) + Configuração + Servidor
        textbeeGatewayGraph(
            navController = navController,
            homeRoute = "welcome",
            onGetStartedClick = {
                viewModel.setReturningUser(false)
                navController.navigate("credentials")
            },
            onReconnectClick = {
                viewModel.setReturningUser(true)
                navController.navigate("credentials")
            }
        )
        composable("credentials") {
            CredentialsScreen(
                viewModel = viewModel,
                onScanQr = onScanQr,
                onNext = { navController.navigate("device_setup") },
                onBack = { if (!navController.popBackStack()) onExit() }
            )
        }
        composable("device_setup") {
            DeviceSetupScreen(
                viewModel = viewModel,
                onSuccess = { navController.navigate("permissions") },
                onBack = { navController.popBackStack() }
            )
        }
        composable("permissions") {
            PermissionsScreen(
                onContinue = { navController.navigate("setup_complete") },
                onBack = { navController.popBackStack() }
            )
        }
        composable("setup_complete") {
            SetupCompleteScreen(
                viewModel = viewModel,
                onOpenDashboard = onComplete
            )
        }
    }
}
