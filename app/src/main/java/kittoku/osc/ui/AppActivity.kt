package kittoku.osc.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kittoku.osc.activity.MainActivity
import kittoku.osc.ui.theme.OscTheme


private const val ROUTE_HOME = "home"
private const val ROUTE_DETAILS = "details"

class AppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        setContent {
            OscTheme {
                val navController = rememberNavController()
                val viewModel: HomeViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                NavHost(navController = navController, startDestination = ROUTE_HOME) {
                    composable(ROUTE_HOME) {
                        HomeScreen(
                            uiState = uiState,
                            onConnect = viewModel::connect,
                            onDisconnect = viewModel::disconnect,
                            onOpenDetails = { navController.navigate(ROUTE_DETAILS) },
                            onOpenLegacySettings = { startActivity(Intent(this@AppActivity, MainActivity::class.java)) },
                        )
                    }
                    composable(ROUTE_DETAILS) {
                        DetailsScreen(rawStatus = uiState.rawStatus, onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
