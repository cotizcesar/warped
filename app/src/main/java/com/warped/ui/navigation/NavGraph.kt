package com.warped.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.warped.ui.chat.ChatScreen
import com.warped.ui.endpoints.EndpointsScreen
import com.warped.ui.huggingface.HuggingFaceScreen
import com.warped.ui.models.ModelsScreen
import com.warped.ui.presets.PresetsScreen
import com.warped.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

private val DrawerBg = Color(0xFF1C1C1C)
private val DrawerAccent = Color(0xFFD97706)
private val DrawerTextPrimary = Color(0xFFECECEC)
private val DrawerTextSecondary = Color(0xFF9CA3AF)
private val DrawerSelectedBg = Color(0xFF2A2A2A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarpedNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val navItems = listOf(
        Triple("Chat", Screen.Chat.route, Icons.AutoMirrored.Filled.Chat),
        Triple("Models", Screen.Models.route, Icons.Filled.Memory),
        Triple("Discover", Screen.HuggingFace.route, Icons.Filled.Search),
        Triple("Presets", Screen.Presets.route, Icons.Filled.Settings),
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = DrawerBg,
                drawerContentColor = DrawerTextPrimary
            ) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Warped",
                    color = DrawerTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                HorizontalDivider(color = Color(0xFF333333), thickness = 0.5.dp)
                Spacer(Modifier.height(8.dp))
                navItems.forEach { (label, route, icon) ->
                    val selected = currentRoute == route
                    NavigationDrawerItem(
                        icon = { 
                            Icon(icon, contentDescription = label, 
                                tint = if (selected) DrawerAccent else DrawerTextSecondary)
                        },
                        label = { 
                            Text(label, 
                                color = if (selected) DrawerAccent else DrawerTextSecondary,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                        },
                        selected = selected,
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = DrawerSelectedBg,
                            unselectedContainerColor = Color.Transparent
                        ),
                        onClick = {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                            scope.launch { drawerState.close() }
                        }
                    )
                }
                Spacer(Modifier.weight(1f))
                HorizontalDivider(color = Color(0xFF333333), thickness = 0.5.dp)
                NavigationDrawerItem(
                    icon = { 
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = DrawerTextSecondary)
                    },
                    label = { Text("Settings", color = DrawerTextSecondary) },
                    selected = currentRoute == Screen.Settings.route,
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = DrawerSelectedBg,
                        unselectedContainerColor = Color.Transparent
                    ),
                    onClick = {
                        navController.navigate(Screen.Settings.route)
                        scope.launch { drawerState.close() }
                    }
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Chat.route
        ) {
            composable(Screen.Chat.route) {
                ChatScreen(onOpenDrawer = { scope.launch { drawerState.open() } })
            }
            composable(Screen.Models.route) {
                ModelsScreen(
                    onUseInChat = { navController.navigate(Screen.Chat.route) },
                    onOpenHuggingFace = { navController.navigate(Screen.HuggingFace.route) },
                    onOpenDrawer = { scope.launch { drawerState.open() } }
                )
            }
            composable(Screen.HuggingFace.route) {
                HuggingFaceScreen(
                    onNavigateToModels = {
                        navController.navigate(Screen.Models.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.Presets.route) {
                PresetsScreen()
            }
            composable(Screen.Endpoints.route) {
                EndpointsScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
