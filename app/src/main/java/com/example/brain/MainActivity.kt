package com.example.brain

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.brain.ui.capture.CaptureScreen
import com.example.brain.ui.capture.CaptureViewModel
import com.example.brain.ui.capture.CaptureViewModelFactory
import com.example.brain.ui.collections.CollectionsScreen
import com.example.brain.ui.collections.CollectionsViewModel
import com.example.brain.ui.collections.CollectionsViewModelFactory
import com.example.brain.ui.detail.ItemDetailScreen
import com.example.brain.ui.detail.ItemDetailViewModel
import com.example.brain.ui.detail.ItemDetailViewModelFactory
import com.example.brain.ui.explore.ExploreScreen
import com.example.brain.ui.explore.ExploreViewModel
import com.example.brain.ui.explore.ExploreViewModelFactory
import com.example.brain.ui.home.HomeScreen
import com.example.brain.ui.home.HomeViewModel
import com.example.brain.ui.home.HomeViewModelFactory
import com.example.brain.ui.lock.AppLockScreen
import com.example.brain.ui.navigation.Screen
import com.example.brain.ui.search.SearchScreen
import com.example.brain.ui.search.SearchViewModel
import com.example.brain.ui.search.SearchViewModelFactory
import com.example.brain.ui.settings.SettingsScreen
import com.example.brain.ui.settings.SettingsViewModel
import com.example.brain.ui.settings.SettingsViewModelFactory
import com.example.brain.ui.theme.IndigoPrimary
import com.example.brain.ui.theme.SecondBrainTheme
import com.example.brain.ui.vault.VaultScreen
import com.example.brain.ui.vault.VaultViewModel
import com.example.brain.ui.vault.VaultViewModelFactory
import com.example.brain.ui.assistant.AskBrainScreen
import com.example.brain.ui.assistant.AskBrainViewModel
import com.example.brain.ui.assistant.AskBrainViewModelFactory
import com.example.brain.ui.onboarding.OnboardingScreen
import com.example.brain.util.OnboardingPreferences
import com.example.brain.util.ShareIntentParser

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as BrainApplication
        val repository = app.repository
        val securityManager = app.securityManager
        val backupManager = app.backupManager
        val vaultCryptoManager = app.vaultCryptoManager
        val biometricAuthManager = app.biometricAuthManager

        // Check for incoming share intent
        val sharedContent = ShareIntentParser.parse(intent)

        setContent {
            SecondBrainTheme {
                val onboardingPreferences = remember { OnboardingPreferences(this@MainActivity) }
                val startDestination = remember {
                    if (onboardingPreferences.isOnboardingCompleted()) Screen.Home.route else Screen.Onboarding.route
                }
                var isAppLocked by remember { mutableStateOf(securityManager.isAppLockedState) }
                val isDecoyMode = securityManager.isDecoyMode

                // Monitor lifecycle for auto-lock timeouts
                DisposableEffect(this@MainActivity) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_STOP -> {
                                securityManager.recordAppBackground()
                            }
                            Lifecycle.Event.ON_START -> {
                                securityManager.recordAppForeground()
                                if (securityManager.isAppLockedState) {
                                    isAppLocked = true
                                }
                            }
                            else -> {}
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose {
                        lifecycle.removeObserver(observer)
                    }
                }

                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // Dynamic FLAG_SECURE window protection for App Lock, Vault, and Settings
                LaunchedEffect(isAppLocked, currentRoute) {
                    if (isAppLocked || currentRoute == Screen.Vault.route || currentRoute == Screen.Settings.route) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }

                // If App is currently locked, show AppLockScreen
                if (isAppLocked) {
                    AppLockScreen(
                        securityManager = securityManager,
                        biometricAuthManager = biometricAuthManager,
                        onUnlocked = {
                            isAppLocked = false
                        }
                    )
                } else {
                    // Auto-route to capture if opened via system Share Sheet
                    LaunchedEffect(sharedContent) {
                        if (sharedContent != null) {
                            navController.navigate(Screen.Capture.route)
                        }
                    }

                    // Bottom Navigation: hide Vault if in duress Decoy Mode
                    val availableNavItems = remember(isDecoyMode) {
                        val items = if (isDecoyMode) {
                            Screen.bottomNavItems.filter { it != Screen.Vault }
                        } else {
                            Screen.bottomNavItems
                        }
                        items.filterNotNull()
                    }
                    val showBottomNav = currentRoute != null && currentRoute in availableNavItems.mapNotNull { it?.route }

                    Scaffold(
                        bottomBar = {
                            if (showBottomNav) {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = IndigoPrimary
                                ) {
                                    availableNavItems.forEach { screen ->
                                        val isSelected = currentRoute == screen.route
                                        val iconVector = if (isSelected) {
                                            screen.selectedIcon ?: screen.unselectedIcon ?: Icons.Default.Home
                                        } else {
                                            screen.unselectedIcon ?: screen.selectedIcon ?: Icons.Default.Home
                                        }
                                        NavigationBarItem(
                                            icon = {
                                                Icon(
                                                    imageVector = iconVector,
                                                    contentDescription = screen.title
                                                )
                                            },
                                            label = { Text(screen.title) },
                                            selected = isSelected,
                                            onClick = {
                                                if (currentRoute != screen.route) {
                                                    navController.navigate(screen.route) {
                                                        popUpTo(navController.graph.findStartDestination().id) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = startDestination,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            // 0. Onboarding Screen
                            composable(Screen.Onboarding.route) {
                                OnboardingScreen(
                                    onComplete = {
                                        onboardingPreferences.setOnboardingCompleted()
                                        navController.navigate(Screen.Home.route) {
                                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                                        }
                                    },
                                    onConfigureSecurity = {
                                        onboardingPreferences.setOnboardingCompleted()
                                        navController.navigate(Screen.Settings.route) {
                                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            // 1. Home Screen
                            composable(Screen.Home.route) {
                                val homeViewModel: HomeViewModel by viewModels {
                                    HomeViewModelFactory(repository)
                                }
                                HomeScreen(
                                    viewModel = homeViewModel,
                                    onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                                    onNavigateToCapture = { navController.navigate(Screen.Capture.route) },
                                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                                    onNavigateToDetail = { id -> navController.navigate(Screen.Detail.createRoute(id)) },
                                    onNavigateToAskBrain = { navController.navigate(Screen.AskBrain.route) },
                                    onNavigateToInbox = { navController.navigate(Screen.Inbox.route) }
                                )
                            }


                            // 2. Explore Screen
                            composable(Screen.Explore.route) {
                                val exploreViewModel: ExploreViewModel by viewModels {
                                    ExploreViewModelFactory(repository)
                                }
                                ExploreScreen(
                                    viewModel = exploreViewModel,
                                    onNavigateToDetail = { id -> navController.navigate(Screen.Detail.createRoute(id)) },
                                    onNavigateToSearch = { navController.navigate(Screen.Search.route) }
                                )

                            }

                            // 3. Capture Screen
                            composable(Screen.Capture.route) {
                                val captureViewModel: CaptureViewModel by viewModels {
                                    CaptureViewModelFactory(repository, vaultCryptoManager)
                                }
                                LaunchedEffect(sharedContent) {
                                    if (sharedContent != null) {
                                        captureViewModel.initFromContent(sharedContent, this@MainActivity)
                                    }
                                }
                                CaptureScreen(
                                    viewModel = captureViewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onSaved = { newId ->
                                        navController.popBackStack()
                                        navController.navigate(Screen.Detail.createRoute(newId))
                                    },
                                    onOpenExisting = { existingId ->
                                        navController.popBackStack()
                                        navController.navigate(Screen.Detail.createRoute(existingId))
                                    }
                                )
                            }

                            // 4. Collections Screen
                            composable(Screen.Collections.route) {
                                val collectionsViewModel: CollectionsViewModel by viewModels {
                                    CollectionsViewModelFactory(repository)
                                }
                                CollectionsScreen(viewModel = collectionsViewModel)
                            }

                            // 5. Vault Screen
                            composable(Screen.Vault.route) {
                                val vaultViewModel: VaultViewModel by viewModels {
                                    VaultViewModelFactory(repository, securityManager, vaultCryptoManager)
                                }
                                VaultScreen(
                                    viewModel = vaultViewModel,
                                    biometricAuthManager = biometricAuthManager,
                                    onNavigateToDetail = { id -> navController.navigate(Screen.Detail.createRoute(id)) }
                                )
                            }

                            // 6. Search Screen
                            composable(Screen.Search.route) {
                                val searchViewModel: SearchViewModel by viewModels {
                                    SearchViewModelFactory(app, repository)
                                }
                                SearchScreen(
                                    viewModel = searchViewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToDetail = { id -> navController.navigate(Screen.Detail.createRoute(id)) }
                                )
                            }

                            // 7. Settings Screen
                            composable(Screen.Settings.route) {
                                val settingsViewModel: SettingsViewModel by viewModels {
                                    SettingsViewModelFactory(repository, backupManager, securityManager, this@MainActivity)
                                }
                                SettingsScreen(
                                    viewModel = settingsViewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToBackupRecovery = { navController.navigate(Screen.BackupRecovery.route) }
                                )
                            }

                            // 8. Item Detail Screen
                            composable(
                                route = Screen.Detail.route,
                                arguments = listOf(navArgument("itemId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
                                val detailViewModel: ItemDetailViewModel by viewModels {
                                    ItemDetailViewModelFactory(itemId, repository)
                                }
                                ItemDetailScreen(
                                    viewModel = detailViewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToRelated = { relatedId ->
                                        navController.navigate(Screen.Detail.createRoute(relatedId))
                                    },
                                    onNavigateToAsk = { q ->
                                        navController.navigate(Screen.AskBrain.createRoute(q))
                                    }
                                )
                            }

                            // 9. Backup & Recovery Subsystem Screen
                            composable(Screen.BackupRecovery.route) {
                                val backupViewModel: com.example.brain.ui.backup.BackupRecoveryViewModel by viewModels {
                                    com.example.brain.ui.backup.BackupRecoveryViewModelFactory(
                                        app.backupRecoveryManager,
                                        app.dataIntegrityManager,
                                        securityManager
                                    )
                                }
                                com.example.brain.ui.backup.BackupRecoveryScreen(
                                    viewModel = backupViewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            // 10. Ask Your Brain Screen (with optional query)
                            composable(
                                route = Screen.AskBrain.route + "?q={q}",
                                arguments = listOf(navArgument("q") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                })
                            ) { backStackEntry ->
                                val initialQ = backStackEntry.arguments?.getString("q")
                                val askBrainViewModel: AskBrainViewModel by viewModels {
                                    AskBrainViewModelFactory(repository, initialQ)
                                }
                                AskBrainScreen(
                                    viewModel = askBrainViewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToDetail = { id -> navController.navigate(Screen.Detail.createRoute(id)) }
                                )
                            }

                            composable(Screen.AskBrain.route) {
                                val askBrainViewModel: AskBrainViewModel by viewModels {
                                    AskBrainViewModelFactory(repository, null)
                                }
                                AskBrainScreen(
                                    viewModel = askBrainViewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToDetail = { id -> navController.navigate(Screen.Detail.createRoute(id)) }
                                )
                            }

                            // 11. Knowledge Inbox Screen
                            composable(Screen.Inbox.route) {
                                val inboxViewModel: com.example.brain.ui.inbox.InboxViewModel by viewModels {
                                    com.example.brain.ui.inbox.InboxViewModelFactory(repository)
                                }
                                com.example.brain.ui.inbox.InboxScreen(
                                    viewModel = inboxViewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToDetail = { id -> navController.navigate(Screen.Detail.createRoute(id)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

