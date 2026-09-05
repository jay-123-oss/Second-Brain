package com.example.brain.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Sealed class defining all navigation routes across Second Brain.
 */
sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector? = null,
    val unselectedIcon: ImageVector? = null
) {
    object Home : Screen(
        route = "home",
        title = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )

    object Explore : Screen(
        route = "explore",
        title = "Explore",
        selectedIcon = Icons.Filled.Explore,
        unselectedIcon = Icons.Outlined.Explore
    )

    object Capture : Screen(
        route = "capture",
        title = "Capture",
        selectedIcon = Icons.Filled.AddCircle,
        unselectedIcon = Icons.Outlined.AddCircleOutline
    )

    object Collections : Screen(
        route = "collections",
        title = "Collections",
        selectedIcon = Icons.Filled.Folder,
        unselectedIcon = Icons.Outlined.Folder
    )

    object Vault : Screen(
        route = "vault",
        title = "Vault",
        selectedIcon = Icons.Filled.Lock,
        unselectedIcon = Icons.Outlined.Lock
    )

    object Search : Screen(
        route = "search",
        title = "Search"
    )

    object Settings : Screen(
        route = "settings",
        title = "Settings"
    )

    object Detail : Screen(
        route = "detail/{itemId}",
        title = "Knowledge Detail"
    ) {
        fun createRoute(itemId: String): String = "detail/$itemId"
    }

    object BackupRecovery : Screen(
        route = "backup_recovery",
        title = "Backup & Recovery"
    )

    object Onboarding : Screen(
        route = "onboarding",
        title = "Welcome"
    )

    object AskBrain : Screen(
        route = "ask_brain",
        title = "Ask Your Brain"
    ) {
        fun createRoute(initialQuestion: String? = null): String {
            return if (!initialQuestion.isNullOrBlank()) {
                "ask_brain?q=" + android.net.Uri.encode(initialQuestion)
            } else {
                "ask_brain"
            }
        }
    }

    object Inbox : Screen(
        route = "inbox",
        title = "Knowledge Inbox"
    )


    companion object {
        val bottomNavItems: List<Screen>
            get() = listOf(
                Home,
                Explore,
                Capture,
                Collections,
                Vault
            )
    }
}
