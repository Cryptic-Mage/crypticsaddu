package com.helucryptic.android.ui.navigation

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
            modifier         = Modifier.padding(padding)
        ) {
            composable(Screen.Onboarding.route)    { OnboardingScreen(nav) }
            composable(Screen.ChatList.route)      { ChatListScreen(nav) }
            composable(Screen.Chat.route)          { ChatScreen(nav, it.arguments?.getString("peerId")!!) }
            composable(Screen.RoomList.route)      { RoomListScreen(nav) }
            composable(Screen.Room.route)          { RoomScreen(nav, it.arguments?.getString("roomCode")!!) }
            composable(Screen.Contacts.route)      { ContactsScreen(nav) }
            composable(Screen.ContactDetail.route) { ContactDetailScreen(nav, it.arguments?.getString("username")!!) }
            composable(Screen.Call.route)          { CallScreen(nav, it.arguments?.getString("peerId")!!) }
            composable(Screen.Settings.route)      { SettingsScreen(nav) }
            composable(Screen.KeyBackup.route)     { KeyBackupScreen(nav) }
            composable(Screen.Invite.route)        { InviteScreen(nav, it.arguments?.getString("roomCode")) }
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
