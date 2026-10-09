package kittoku.osc.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kittoku.osc.activity.MainActivity
import kittoku.osc.preference.Profile
import kittoku.osc.preference.SettingsError
import kittoku.osc.preference.SettingsSection
import kittoku.osc.preference.profileSettingsErrors
import kittoku.osc.ui.theme.OscTheme


private const val ROUTE_HOME = "home"
private const val ROUTE_DETAILS = "details"
private const val ROUTE_PROFILES = "profiles"
private const val ROUTE_EDIT = "edit"
private const val ROUTE_EDIT_ROUTES = "edit/routes"
private const val ROUTE_EDIT_APPS = "edit/apps"
private const val ROUTE_EDIT_ADVANCED = "edit/advanced"

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
                val currentDraft by profilesViewModel.draft.collectAsStateWithLifecycle()
                val canSwitch = homeState.state == ConnectionState.DISCONNECTED || homeState.state == ConnectionState.ERROR

                val openEditor = { name: String? ->
                    profilesViewModel.startEdit(name)
                    navController.navigate(ROUTE_EDIT)
                }
                val popBack: () -> Unit = { navController.popBackStack() }

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
                        DetailsScreen(rawStatus = homeState.rawStatus, onBack = popBack)
                    }
                    composable(ROUTE_PROFILES) {
                        ProfilesScreen(
                            uiState = profilesState,
                            canSwitch = canSwitch,
                            onBack = popBack,
                            onActivate = profilesViewModel::activate,
                            onCreate = { openEditor(null) },
                            onEdit = { openEditor(it) },
                            onDuplicate = { profilesViewModel.duplicate(it) },
                            onDelete = profilesViewModel::delete,
                            exportJson = profilesViewModel::exportJson,
                            importJson = profilesViewModel::importJson,
                        )
                    }
                    composable(ROUTE_EDIT) {
                        rememberLastNonNull(currentDraft)?.also { draft ->
                            ProfileEditScreen(
                                draft = draft,
                                onFormChange = profilesViewModel::updateForm,
                                onOpenSection = { section ->
                                    navController.navigate(
                                        when (section) {
                                            SettingsSection.ROUTES -> ROUTE_EDIT_ROUTES
                                            SettingsSection.APPS -> ROUTE_EDIT_APPS
                                            SettingsSection.ADVANCED -> ROUTE_EDIT_ADVANCED
                                        }
                                    )
                                },
                                onSave = { if (profilesViewModel.saveDraft()) navController.popBackStack() },
                                onBack = {
                                    navController.popBackStack()
                                    profilesViewModel.discardDraft()
                                },
                            )
                        }
                    }
                    composable(ROUTE_EDIT_ROUTES) {
                        DraftSection(currentDraft) { profile, errors ->
                            RoutesSettingsScreen(profile, errors, profilesViewModel::updateProfile, popBack)
                        }
                    }
                    composable(ROUTE_EDIT_APPS) {
                        DraftSection(currentDraft) { profile, _ ->
                            AppsSettingsScreen(profile, profilesViewModel::updateProfile, popBack)
                        }
                    }
                    composable(ROUTE_EDIT_ADVANCED) {
                        DraftSection(currentDraft) { profile, errors ->
                            AdvancedSettingsScreen(profile, errors, profilesViewModel::updateProfile, popBack)
                        }
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

// Errors are highlighted only after the user tried to save
@Composable
private fun DraftSection(draft: ProfileDraft?, content: @Composable (Profile, Set<SettingsError>) -> Unit) {
    val shown = rememberLastNonNull(draft) ?: return
    val profile = remember(shown.json) { shown.profile() }
    val errors = remember(shown.json, shown.attemptedSave) {
        if (shown.attemptedSave) profileSettingsErrors(profile) else emptySet()
    }
    content(profile, errors)
}
