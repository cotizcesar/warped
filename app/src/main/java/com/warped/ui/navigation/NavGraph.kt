package com.warped.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.warped.R
import com.warped.ui.components.WarpedAlertDialog
import com.warped.data.local.preferences.WizardPreferences
import com.warped.domain.repository.ChatRepository
import com.warped.ui.chat.ChatScreen
import com.warped.ui.help.HelpScreen
import com.warped.ui.huggingface.HuggingFaceScreen
import com.warped.ui.models.ModelsScreen
import com.warped.ui.presets.PresetsScreen
import com.warped.ui.selector.UnifiedSelectorScreen
import com.warped.ui.settings.SettingsScreen
import com.warped.ui.wizard.WizardScreen
import dagger.hilt.android.EntryPointAccessors
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
    val currentDestination = navBackStackEntry?.destination
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var activeConversationId by rememberSaveable { mutableStateOf<Long?>(null) }

    var savedRoute by rememberSaveable { mutableStateOf<String?>(null) }
    var savedConvId by rememberSaveable { mutableStateOf<Long?>(null) }

    val currentRoute = currentDestination?.route
    LaunchedEffect(currentRoute, activeConversationId) {
        savedRoute = currentRoute
        savedConvId = activeConversationId
    }

    LaunchedEffect(Unit) {
        val route = savedRoute
        if (route != null && currentDestination?.hasRoute(Screen.Chat::class) != true) {
            activeConversationId = savedConvId
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val entryPoint = remember {
        val appContext = context.applicationContext
        EntryPointAccessors.fromApplication(appContext, ChatRepoEntryPoint::class.java)
    }
    val chatRepository = remember { entryPoint.chatRepository() }
    val activeModelSelection = remember { entryPoint.activeModelSelection() }
    val engineManager = remember { entryPoint.engineManager() }
    val wizardPreferences = remember { entryPoint.wizardPreferences() }
    val conversations by chatRepository.observeConversations().collectAsStateWithLifecycle(emptyList())

    val isWizardComplete by wizardPreferences.isWizardComplete
        .collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(isWizardComplete) {
        if (isWizardComplete == false &&
            currentDestination?.hierarchy?.any { it.hasRoute(Screen.Wizard::class) } != true
        ) {
            navController.navigate(Screen.Wizard) {
                popUpTo(Screen.Chat) { inclusive = true }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (activeConversationId == null) {
            val lastConvId = activeModelSelection.getLastConversation()
            if (lastConvId > 0) activeConversationId = lastConvId
        }
    }

    fun isOn(destinationClass: kotlin.reflect.KClass<*>): Boolean =
        currentDestination?.hierarchy?.any { it.hasRoute(destinationClass) } == true

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            // QUICK-C: full-width sheet. ModalDrawerSheet caps width at 360dp
            // internally (M3 DrawerSheet sizeIn maxWidth = ContainerWidth,
            // applied AFTER the caller modifier — verified against the
            // material3 1.4.0 sources this BOM resolves), so a bare
            // fillMaxWidth on ModalDrawerSheet would NOT widen it. This
            // Surface replicates the sheet (0dp shape, DrawerBg /
            // DrawerTextPrimary, system-bars insets) at full size.
            // Gestures + scrim stay with ModalNavigationDrawer; every item
            // below is untouched.
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp),
                color = DrawerBg,
                contentColor = DrawerTextPrimary
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .windowInsetsPadding(DrawerDefaults.windowInsets)
                ) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = com.warped.R.drawable.logo),
                            contentDescription = stringResource(R.string.cd_logo_nav),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Warped", color = DrawerTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                    }

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.Add, null, tint = DrawerAccent, modifier = Modifier.size(24.dp)) },
                        label = { Text(stringResource(R.string.new_chat), color = DrawerAccent, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
                        selected = false,
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                        onClick = {
                            activeConversationId = null
                            navController.navigate(Screen.NewChat(newChat = true)) {
                                popUpTo(Screen.Chat) { inclusive = true }
                            }
                            scope.launch { drawerState.close() }
                        }
                    )
                    HorizontalDivider(color = Color(0xFF333333), thickness = 0.5.dp)
                    Text(stringResource(R.string.recents), color = DrawerTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(conversations, key = { it.id }) { conv ->
                            var showDeleteConfirm by remember { mutableStateOf(false) }
                            val isActive = conv.id == activeConversationId
                            if (showDeleteConfirm) {
                                WarpedAlertDialog(
                                    onDismissRequest = { showDeleteConfirm = false },
                                    title = { Text(stringResource(R.string.delete_chat_title)) },
                                    text = { Text(stringResource(R.string.delete_chat_message, conv.title)) },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            scope.launch {
                                                val wasActive = conv.id == activeConversationId
                                                chatRepository.deleteConversation(conv.id)
                                                showDeleteConfirm = false
                                                if (wasActive) {
                                                    activeConversationId = null
                                                    activeModelSelection.clearLastConversation()
                                                    engineManager.scheduleUnload()
                                                    navController.navigate(Screen.NewChat(newChat = true)) {
                                                        popUpTo(Screen.Chat) { inclusive = true }
                                                    }
                                                }
                                            }
                                        }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                                    },
                                    dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) } }
                                )
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isActive) Color(0xFF2B2B29) else Color.Transparent
                                ),
                                shape = RoundedCornerShape(12.dp),
                                onClick = {
                                    activeConversationId = conv.id
                                    navController.navigate(Screen.ChatDetail(conv.id)) { launchSingleTop = true }
                                    scope.launch { drawerState.close() }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Chat, null,
                                        tint = if (isActive) DrawerAccent else DrawerTextPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        conv.title, color = DrawerTextPrimary,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        fontSize = 14.sp,
                                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { showDeleteConfirm = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Close, "Delete",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF333333), thickness = 0.5.dp)
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                        val isModels = isOn(Screen.Selector::class)
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.Memory, null, tint = if (isModels) DrawerAccent else DrawerTextSecondary, modifier = Modifier.size(20.dp)) },
                            label = { Text(stringResource(R.string.tab_models), color = if (isModels) DrawerAccent else DrawerTextSecondary, fontSize = 12.sp) },
                            selected = isModels,
                            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = DrawerSelectedBg, unselectedContainerColor = Color.Transparent),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                navController.navigate(Screen.Selector) {
                                    popUpTo(Screen.Chat) { inclusive = false }
                                    launchSingleTop = true
                                }
                                scope.launch { drawerState.close() }
                            }
                        )
                        val isHelp = isOn(Screen.Help::class)
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.Info, null, tint = if (isHelp) DrawerAccent else DrawerTextSecondary, modifier = Modifier.size(20.dp)) },
                            label = { Text(stringResource(R.string.help_title), color = if (isHelp) DrawerAccent else DrawerTextSecondary, fontSize = 12.sp) },
                            selected = isHelp,
                            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = DrawerSelectedBg, unselectedContainerColor = Color.Transparent),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                navController.navigate(Screen.Help) {
                                    popUpTo(Screen.Chat) { inclusive = false }
                                    launchSingleTop = true
                                }
                                scope.launch { drawerState.close() }
                            }
                        )
                        val isSettings = isOn(Screen.Settings::class)
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Filled.Settings, null, tint = if (isSettings) DrawerAccent else DrawerTextSecondary, modifier = Modifier.size(20.dp)) },
                            label = { Text(stringResource(R.string.settings), color = if (isSettings) DrawerAccent else DrawerTextSecondary, fontSize = 12.sp) },
                            selected = isSettings,
                            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = DrawerSelectedBg, unselectedContainerColor = Color.Transparent),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                navController.navigate(Screen.Settings) {
                                    popUpTo(Screen.Chat) { inclusive = false }
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
        if (isWizardComplete == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DrawerBg)
            )
            return@ModalNavigationDrawer
        }

        NavHost(navController, startDestination = Screen.Chat) {
            composable<Screen.Chat> {
                ChatScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onNavigateToSelector = {
                        navController.navigate(Screen.Selector) {
                            popUpTo(Screen.Selector) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable<Screen.NewChat> {
                ChatScreen(
                    newChat = true,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onNavigateToSelector = {
                        navController.navigate(Screen.Selector) {
                            popUpTo(Screen.Selector) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable<Screen.ChatDetail> { backStackEntry ->
                val args = backStackEntry.toRoute<Screen.ChatDetail>()
                LaunchedEffect(args.conversationId) {
                    activeConversationId = args.conversationId
                }
                ChatScreen(
                    conversationId = args.conversationId,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onNavigateToSelector = {
                        navController.navigate(Screen.Selector) {
                            popUpTo(Screen.Chat) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable<Screen.Selector> {
                UnifiedSelectorScreen(
                    onNavigateToChat = {
                        navController.navigate(Screen.NewChat(newChat = true)) {
                            popUpTo(Screen.Chat) { inclusive = true }
                        }
                    },
                    onOpenHuggingFace = { navController.navigate(Screen.HuggingFace) },
                    onNavigateToPresets = {
                        navController.navigate(Screen.Presets) {
                            launchSingleTop = true
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable<Screen.Models> {
                ModelsScreen(
                    onUseInChat = { conversationId ->
                        navController.navigate(Screen.ChatDetail(conversationId)) {
                            popUpTo(Screen.Chat) { inclusive = true }
                        }
                    },
                    onOpenHuggingFace = { navController.navigate(Screen.HuggingFace) },
                    onOpenDrawer = { scope.launch { drawerState.open() } }
                )
            }
            composable<Screen.HuggingFace> {
                HuggingFaceScreen(
                    onNavigateToModels = {
                        navController.popBackStack()
                    }
                )
            }
            composable<Screen.Presets> {
                PresetsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable<Screen.Settings> {
                SettingsScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onNavigateToWizard = {
                        navController.navigate(Screen.WizardReview(review = true)) {
                            launchSingleTop = true
                        }
                    },
                    // API-03: system back on Settings pops — same destination
                    // the drawer back-stack would resolve to.
                    onBack = { navController.popBackStack() }
                )
            }
            composable<Screen.Help> {
                HelpScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable<Screen.Wizard> {
                WizardScreen(
                    isReEntry = false,
                    onWizardComplete = {
                        navController.navigate(Screen.Chat) {
                            popUpTo(Screen.Wizard) { inclusive = true }
                        }
                    },
                    onNavigate = { destination ->
                        navController.navigate(destination) {
                            launchSingleTop = true
                        }
                    },
                    onBackFromReEntry = {
                        navController.popBackStack()
                    }
                )
            }
            composable<Screen.WizardReview> { backStackEntry ->
                val args = backStackEntry.toRoute<Screen.WizardReview>()
                WizardScreen(
                    isReEntry = args.review,
                    onWizardComplete = {
                        navController.navigate(Screen.Chat) {
                            popUpTo(Screen.Wizard) { inclusive = true }
                        }
                    },
                    onNavigate = { destination ->
                        navController.navigate(destination) {
                            launchSingleTop = true
                        }
                    },
                    onBackFromReEntry = {
                        navController.popBackStack()
                    }
                )
            }
            composable<Screen.PromptLab> {
                com.warped.ui.promptlab.PromptLabScreen()
            }
            composable<Screen.Benchmark> {
                com.warped.ui.benchmark.BenchmarkScreen()
            }
        }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface ChatRepoEntryPoint {
    fun chatRepository(): ChatRepository
    fun activeModelSelection(): com.warped.domain.model.ActiveModelSelection
    fun engineManager(): com.warped.data.local.inference.EngineManager
    fun wizardPreferences(): WizardPreferences
}
