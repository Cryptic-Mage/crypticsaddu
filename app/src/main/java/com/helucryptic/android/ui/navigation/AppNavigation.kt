package com.helucryptic.android.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.*
import androidx.navigation.compose.*
import com.helucryptic.android.ui.call.CallScreen
import com.helucryptic.android.ui.chat.ChatListScreen
import com.helucryptic.android.ui.chat.ChatScreen
import com.helucryptic.android.ui.contacts.ContactDetailScreen
import com.helucryptic.android.ui.contacts.ContactsScreen
import com.helucryptic.android.ui.onboarding.OnboardingScreen
import com.helucryptic.android.ui.room.RoomListScreen
import com.helucryptic.android.ui.room.RoomScreen
import com.helucryptic.android.ui.room.InviteScreen
import com.helucryptic.android.ui.settings.SettingsScreen
import com.helucryptic.android.ui.settings.KeyBackupScreen

// Slide-in from right / slide-out to left (forward push navigation)
private val pushEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))
}
private val pushExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(tween(250)) { -it / 3 } + fadeOut(tween(200))
}
// Slide-in from left / slide-out to right (back-pop navigation)
private val pushPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(300))
}
private val pushPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(tween(250)) { it } + fadeOut(tween(200))
}

sealed class Screen(val route: String) {
    object Onboarding     : Screen("onboarding")
    object ChatList       : Screen("chat_list")
    object Chat           : Screen("chat/{peerId}") { fun go(id: String) = "chat/$id" }
    object RoomList       : Screen("room_list")
    object Room           : Screen("room/{roomCode}") { fun go(c: String) = "room/$c" }
    object Contacts       : Screen("contacts")
    object ContactDetail  : Screen("contact/{username}") { fun go(u: String) = "contact/$u" }
    object Call           : Screen("call/{peerId}") { fun go(id: String) = "call/$id" }
    object Settings       : Screen("settings")
    object KeyBackup      : Screen("key_backup")
    object Invite         : Screen("invite/{roomCode}") { fun go(c: String) = "invite/$c" }
}

private val bottomTabs = listOf(Screen.ChatList, Screen.RoomList, Screen.Contacts, Screen.Settings)
private val tabRoutes  = bottomTabs.map { it.route }.toSet()

@Composable
fun AppNavigation(startDestination: String) {
    val nav = rememberNavController()
    val currentEntry by nav.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in tabRoutes) PillNavBar(nav, currentRoute)
        }
    ) { padding ->
        NavHost(
            navController    = nav,
            startDestination = startDestination,
            modifier         = Modifier.padding(padding),
            // Default tab transitions — simple crossfade
            enterTransition  = { fadeIn(tween(220)) },
            exitTransition   = { fadeOut(tween(180)) },
            popEnterTransition  = { fadeIn(tween(220)) },
            popExitTransition   = { fadeOut(tween(180)) }
        ) {
            // Onboarding slides in like a push screen (only ever navigated forward from)
            composable(
                Screen.Onboarding.route,
                enterTransition = pushEnter, exitTransition = pushExit,
                popEnterTransition = pushPopEnter, popExitTransition = pushPopExit
            ) { OnboardingScreen(nav) }

            // Tab-level screens use default fade (set at NavHost level)
            composable(Screen.ChatList.route) { ChatListScreen(nav) }
            composable(Screen.RoomList.route) { RoomListScreen(nav) }
            composable(Screen.Contacts.route) { ContactsScreen(nav) }
            composable(Screen.Settings.route) { SettingsScreen(nav) }

            // Push screens — horizontal slide
            composable(
                Screen.Chat.route,
                enterTransition = pushEnter, exitTransition = pushExit,
                popEnterTransition = pushPopEnter, popExitTransition = pushPopExit
            ) { ChatScreen(nav, it.arguments?.getString("peerId")!!) }

            composable(
                Screen.Room.route,
                enterTransition = pushEnter, exitTransition = pushExit,
                popEnterTransition = pushPopEnter, popExitTransition = pushPopExit
            ) { RoomScreen(nav, it.arguments?.getString("roomCode")!!) }

            composable(
                Screen.ContactDetail.route,
                enterTransition = pushEnter, exitTransition = pushExit,
                popEnterTransition = pushPopEnter, popExitTransition = pushPopExit
            ) { ContactDetailScreen(nav, it.arguments?.getString("username")!!) }

            composable(
                Screen.Call.route,
                enterTransition = pushEnter, exitTransition = pushExit,
                popEnterTransition = pushPopEnter, popExitTransition = pushPopExit
            ) { CallScreen(nav, it.arguments?.getString("peerId")!!) }

            composable(
                Screen.KeyBackup.route,
                enterTransition = pushEnter, exitTransition = pushExit,
                popEnterTransition = pushPopEnter, popExitTransition = pushPopExit
            ) { KeyBackupScreen(nav) }

            composable(
                Screen.Invite.route,
                enterTransition = pushEnter, exitTransition = pushExit,
                popEnterTransition = pushPopEnter, popExitTransition = pushPopExit
            ) { InviteScreen(nav, it.arguments?.getString("roomCode")) }
        }
    }
}

@Composable
private fun PillNavBar(nav: NavHostController, currentRoute: String?) {
    Surface(
        modifier      = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape         = MaterialTheme.shapes.extraLarge,
        color         = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp
    ) {
        Row(Modifier.fillMaxWidth()) {
            bottomTabs.forEach { tab ->
                val selected = tab.route == currentRoute
                val icon  = when (tab) {
                    Screen.ChatList -> Icons.AutoMirrored.Rounded.Chat
                    Screen.RoomList -> Icons.Rounded.MeetingRoom
                    Screen.Contacts -> Icons.Rounded.Group
                    else            -> Icons.Rounded.Settings
                }
                val label = when (tab) {
                    Screen.ChatList -> "Chats"
                    Screen.RoomList -> "Rooms"
                    Screen.Contacts -> "Contacts"
                    else            -> "Settings"
                }
                NavigationBarItem(
                    selected = selected,
                    onClick  = {
                        nav.navigate(tab.route) {
                            popUpTo(nav.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    },
                    icon  = { Icon(icon, contentDescription = label) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}
