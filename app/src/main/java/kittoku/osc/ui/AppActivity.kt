package kittoku.osc.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kittoku.osc.activity.MainActivity
import kittoku.osc.ui.theme.OscTheme


private const val ROUTE_HOME = "home"
private const val ROUTE_DETAILS = "details"
private const val ROUTE_PROFILES = "profiles"
private const val ROUTE_EDIT = "edit"
private const val ARG_NAME = "name"

class AppActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels()
    private val profilesViewModel: ProfilesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        val openLegacySettings = { startActivity(Intent(this, MainActivity::class.java)) }

        setContent {
            OscTheme {
                val navController = rememberNavController()
                val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
                val profilesState by profilesViewModel.uiState.collectAsStateWithLifecycle()
                val canSwitch = homeState.state == ConnectionState.DISCONNECTED || homeState.state == ConnectionState.ERROR

                val openEditor = { name: String? ->
                    navController.navigate(if (name == null) ROUTE_EDIT else "$ROUTE_EDIT?$ARG_NAME=${Uri.encode(name)}")
                }

                NavHost(navController = navController, startDestination = ROUTE_HOME) {
                    composable(ROUTE_HOME) {
                        HomeScreen(
                            uiState = homeState,
                            onConnect = homeViewModel::connect,
                            onDisconnect = homeViewModel::disconnect,
                            onOpenDetails = { navController.navigate(ROUTE_DETAILS) },
                            onOpenLegacySettings = openLegacySettings,
                            onSelectProfile = { profilesViewModel.activate(it) },
                            onManageProfiles = { navController.navigate(ROUTE_PROFILES) },
                            onCreateProfile = { openEditor(null) },
                        )
                    }
                    composable(ROUTE_DETAILS) {
                        DetailsScreen(rawStatus = homeState.rawStatus, onBack = { navController.popBackStack() })
                    }
                    composable(ROUTE_PROFILES) {
                        ProfilesScreen(
                            uiState = profilesState,
                            canSwitch = canSwitch,
                            onBack = { navController.popBackStack() },
                            onActivate = profilesViewModel::activate,
                            onCreate = { openEditor(null) },
                            onEdit = { openEditor(it) },
                            onDuplicate = { profilesViewModel.duplicate(it) },
                            onDelete = profilesViewModel::delete,
                            exportJson = profilesViewModel::exportJson,
                            importJson = profilesViewModel::importJson,
                        )
                    }
                    composable(
                        route = "$ROUTE_EDIT?$ARG_NAME={$ARG_NAME}",
                        arguments = listOf(navArgument(ARG_NAME) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }),
                    ) { entry ->
                        val name = entry.arguments?.getString(ARG_NAME)
                        val initial = remember(name) { profilesViewModel.formFor(name) }

                        ProfileEditScreen(
                            isNew = name == null,
                            initial = initial,
                            onSave = { form ->
                                profilesViewModel.save(name, form).also { if (it == null) navController.popBackStack() }
                            },
                            onBack = { navController.popBackStack() },
                            onOpenLegacySettings = openLegacySettings,
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        profilesViewModel.onResume()
    }
}
