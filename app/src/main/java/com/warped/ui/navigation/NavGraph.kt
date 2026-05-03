package com.warped.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.warped.domain.model.Conversation
import com.warped.domain.repository.ChatRepository
import com.warped.ui.chat.ChatScreen
import com.warped.ui.huggingface.HuggingFaceScreen
import com.warped.ui.models.ModelsScreen
import com.warped.ui.presets.PresetsScreen
import com.warped.ui.settings.SettingsScreen
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private val DrawerBg = Color(0xFF1F1F1E)
private val DrawerAccent = Color(0xFFD97757)
private val DrawerTextPrimary = Color(0xFFECECEC)
private val DrawerTextSecondary = Color(0xFF9CA3AF)
private val DrawerSelectedBg = Color(0xFF121212)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarpedNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var activeConversationId by rememberSaveable { mutableStateOf<Long?>(null) }

    // Persist current route across process death
    var savedRoute by rememberSaveable { mutableStateOf<String?>(null) }
    var savedConvId by rememberSaveable { mutableStateOf<Long?>(null) }

    // Update saved state whenever navigation changes
    LaunchedEffect(currentRoute, activeConversationId) {
        savedRoute = currentRoute
        savedConvId = activeConversationId
    }

    // Restore navigation state after process death
    LaunchedEffect(Unit) {
        val route = savedRoute
        if (route != null && route != Screen.Chat.route) {
            activeConversationId = savedConvId
            navController.navigate(route) {
                popUpTo(Screen.Chat.route)
            }
        }
    }

    // Get ChatRepository for conversation list
    val context = androidx.compose.ui.platform.LocalContext.current
    val chatRepository = remember {
        val appContext = context.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(appContext, ChatRepoEntryPoint::class.java)
        entryPoint.chatRepository()
    }
    val conversations by chatRepository.observeConversations().collectAsStateWithLifecycle(emptyList())

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = DrawerBg,
                drawerContentColor = DrawerTextPrimary
            ) {
                Column(modifier = Modifier.fillMaxHeight()) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = com.warped.R.drawable.logo),
                            contentDescription = "Logo",
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Warped", color = DrawerTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                    }

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.Add, null, tint = DrawerAccent, modifier = Modifier.size(24.dp)) },
                        label = { Text("New Chat", color = DrawerAccent, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
                        selected = false,
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                        onClick = {
                            activeConversationId = null
                            navController.navigate(Screen.Chat.route) {
                                popUpTo(Screen.Chat.route) { inclusive = true }
                            }
                            scope.launch { drawerState.close() }
                        }
                    )
                    HorizontalDivider(color = Color(0xFF333333), thickness = 0.5.dp)
                    Text("Recents", color = DrawerTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(conversations, key = { it.id }) { conv ->
                            var showDeleteConfirm by remember { mutableStateOf(false) }
                            val isActive = conv.id == activeConversationId
                            if (showDeleteConfirm) {
                                AlertDialog(
                                    onDismissRequest = { showDeleteConfirm = false },
                                    title = { Text("Delete chat") },
                                    text = { Text("Delete \"${conv.title}\"? This cannot be undone.") },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            scope.launch {
                                                chatRepository.deleteConversation(conv.id)
                                                if (conv.id == activeConversationId) {
                                                    activeConversationId = null
                                                    navController.navigate(Screen.Chat.route) {
                                                        popUpTo(Screen.Chat.route) { inclusive = true }
                                                    }
                                                }
                                                showDeleteConfirm = false
                                            }
                                        }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                                    },
                                    dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isActive) Color(0xFF121212) else Color.Transparent)
                                    .clickable {
                                        activeConversationId = conv.id
                                        navController.navigate("${Screen.Chat.route}/${conv.id}") { launchSingleTop = true }
                                        scope.launch { drawerState.close() }
                                    }
                                    .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, null,
                                    tint = DrawerTextPrimary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(conv.title, color = DrawerTextPrimary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    fontSize = 15.sp, modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { showDeleteConfirm = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Close, "Delete", tint = DrawerTextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF333333), thickness = 0.5.dp)
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                        val isModels = currentRoute == Screen.Models.route
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.Memory, null, tint = if (isModels) DrawerAccent else DrawerTextSecondary, modifier = Modifier.size(22.dp)) },
                            label = { Text("Models", color = if (isModels) DrawerAccent else DrawerTextSecondary, fontSize = 14.sp) },
                            selected = isModels,
                            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = DrawerSelectedBg, unselectedContainerColor = Color.Transparent),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                navController.navigate(Screen.Models.route) {
                                    popUpTo(Screen.Chat.route) { inclusive = false }
                                    launchSingleTop = true
                                }
                                scope.launch { drawerState.close() }
                            }
                        )
                        val isSettings = currentRoute == Screen.Settings.route
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.Settings, null, tint = if (isSettings) DrawerAccent else DrawerTextSecondary, modifier = Modifier.size(22.dp)) },
                            label = { Text("Settings", color = if (isSettings) DrawerAccent else DrawerTextSecondary, fontSize = 14.sp) },
                            selected = isSettings,
                            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = DrawerSelectedBg, unselectedContainerColor = Color.Transparent),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                navController.navigate(Screen.Settings.route) {
                                    popUpTo(Screen.Chat.route) { inclusive = false }
                                    launchSingleTop = true
                                }
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                }
            }
        }
    ) {
        NavHost(navController, startDestination = Screen.Chat.route) {
            composable(Screen.Chat.route) {
                ChatScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onNavigateToModels = {
                        navController.navigate(Screen.Models.route) {
                            popUpTo(Screen.Chat.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(
                route = "${Screen.Chat.route}/{conversationId}",
                arguments = listOf(navArgument("conversationId") { type = NavType.LongType })
            ) { backStackEntry ->
                val convId = backStackEntry.arguments?.getLong("conversationId") ?: 0L
                ChatScreen(
                    conversationId = convId,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onNavigateToModels = {
                        navController.navigate(Screen.Models.route) {
                            popUpTo(Screen.Chat.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.Models.route) {
                ModelsScreen(
                    onUseInChat = {
                        navController.navigate(Screen.Chat.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }
                    },
                    onOpenHuggingFace = { navController.navigate(Screen.HuggingFace.route) },
                    onOpenDrawer = { scope.launch { drawerState.open() } }
                )
            }
            composable(Screen.HuggingFace.route) {
                HuggingFaceScreen(
                    onNavigateToModels = {
                        navController.popBackStack()
                    }
                )
            }
            composable(Screen.Presets.route) { PresetsScreen() }
            composable(Screen.Settings.route) {
                SettingsScreen(onOpenDrawer = { scope.launch { drawerState.open() } })
            }
        }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface ChatRepoEntryPoint {
    fun chatRepository(): ChatRepository
}
