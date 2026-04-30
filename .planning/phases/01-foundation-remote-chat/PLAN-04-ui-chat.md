---
plan: 04-ui-chat
wave: 4
depends_on: [02-data-layer, 03-networking-providers]
autonomous: false
requirements_addressed: [PROV-01, PROV-02, PROV-03, CHAT-01, CHAT-02, CHAT-03, CHAT-04, CHAT-05, PERS-01, PERS-02, SEC-01]
files_modified:
  - app/src/main/java/com/warped/ui/navigation/Screen.kt
  - app/src/main/java/com/warped/ui/navigation/NavGraph.kt
  - app/src/main/java/com/warped/ui/chat/ChatUiState.kt
  - app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
  - app/src/main/java/com/warped/ui/chat/ChatScreen.kt
  - app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt
  - app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt
  - app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt
  - app/src/main/java/com/warped/ui/chat/components/ConversationList.kt
  - app/src/main/java/com/warped/ui/chat/components/StreamingText.kt
  - app/src/main/java/com/warped/ui/endpoints/EndpointsUiState.kt
  - app/src/main/java/com/warped/ui/endpoints/EndpointsViewModel.kt
  - app/src/main/java/com/warped/ui/endpoints/EndpointsScreen.kt
  - app/src/main/java/com/warped/ui/endpoints/components/EndpointCard.kt
  - app/src/main/java/com/warped/ui/endpoints/components/EndpointForm.kt
  - app/src/main/java/com/warped/ui/models/ModelsPlaceholderScreen.kt
  - app/src/main/java/com/warped/di/DatabaseModule.kt
  - app/src/main/java/com/warped/di/NetworkModule.kt
  - app/src/main/java/com/warped/di/RepositoryModule.kt
  - app/src/main/java/com/warped/di/SecurityModule.kt
  - app/src/main/java/com/warped/di/ProviderModule.kt
  - app/src/main/java/com/warped/WarpedApplication.kt
  - app/src/main/java/com/warped/MainActivity.kt
---

# Plan 04: Navigation Shell, Chat UI, Endpoints UI & Hilt DI Wiring

## Objective
Wire the entire app together: create the bottom-navigation shell with 3 tabs (Chat, Endpoints, Models placeholder), the ChatScreen with streaming token display and stop/cancel, the EndpointsScreen with CRUD and connection testing, and all 5 Hilt DI modules that connect every component from Plans 01-03 into a working app. Also update `WarpedApplication` to use the `RedactingTree` from this plan.

## must_haves
- Bottom nav with Chat/Endpoints/Models tabs, Chat as start destination
- `ChatViewModel.sendMessage()` creates conversation on first message, streams tokens via `ProviderRouter`, saves messages to Room on completion
- `ChatViewModel.stopGeneration()` cancels the generation coroutine and preserves partial response
- ChatScreen shows model selector at top, `LazyColumn` messages in center, `ChatInputBar` pinned at bottom with animated send→stop button
- Token batching at 50ms intervals per PITFALLS.md §3.1 — emits batched deltas to prevent recomposition jank
- EndpointsScreen lets user add/edit/delete endpoints with name, URL, provider type, API key, and test connection
- `EndpointForm` saves API key via `ApiKeyStore` (CharArray, zero-filled after use)
- All 5 Hilt modules compile: `DatabaseModule`, `NetworkModule`, `RepositoryModule`, `SecurityModule`, `ProviderModule`
- `WarpedApplication.kt` plants `RedactingTree` instead of `DebugTree`

## Verification
1. `grep -q 'sealed class Screen' app/src/main/java/com/warped/ui/navigation/Screen.kt`
2. `grep -q 'NavigationBar' app/src/main/java/com/warped/ui/navigation/NavGraph.kt`
3. `grep -q 'fun sendMessage' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
4. `grep -q 'fun stopGeneration' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
5. `grep -q 'LazyColumn' app/src/main/java/com/warped/ui/chat/ChatScreen.kt`
6. `grep -q 'ChatInputBar' app/src/main/java/com/warped/ui/chat/ChatScreen.kt`
7. `grep -q 'stopGeneration' app/src/main/java/com/warped/ui/chat/ChatScreen.kt`
8. `grep -q 'MessageBubble' app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt`
9. `grep -q 'class ChatViewModel' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
10. `grep -q '@HiltViewModel' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
11. `grep -q 'EndpointCard' app/src/main/java/com/warped/ui/endpoints/EndpointsScreen.kt`
12. `grep -q 'apiKeyStore.storeKey' app/src/main/java/com/warped/ui/endpoints/EndpointsViewModel.kt`
13. `grep -q '@Module.*DatabaseModule' app/src/main/java/com/warped/di/DatabaseModule.kt`
14. `grep -q '@Module.*NetworkModule' app/src/main/java/com/warped/di/NetworkModule.kt`
15. `grep -q 'RedactingTree' app/src/main/java/com/warped/WarpedApplication.kt`

## Tasks

### Task 1: Navigation Shell (Screen definitions + NavGraph)
<read_first>
- app/src/main/java/com/warped/MainActivity.kt (will be updated to use NavGraph)
- app/src/main/java/com/warped/ui/theme/Theme.kt (theme to wrap)
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §6 (lines 1240-1310 for exact navigation code)
</read_first>
<action>
1. **`app/src/main/java/com/warped/ui/navigation/Screen.kt`** — Exact from RESEARCH.md §6.1 lines 1244-1251:
```kotlin
package com.warped.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Chat : Screen("chat", "Chat", Icons.Filled.Chat)
    data object Endpoints : Screen("endpoints", "Endpoints", Icons.Filled.Dns)
    data object Models : Screen("models", "Models", Icons.Filled.Memory)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
}
```

2. **`app/src/main/java/com/warped/ui/navigation/NavGraph.kt`** — Exact from RESEARCH.md §6.2 lines 1256-1310. Key structure:
   - `Scaffold` with `NavigationBar` bottom bar containing Chat, Endpoints, Models tabs
   - `NavHost` with 4 destinations: Chat, Endpoints, Models (placeholder), Settings (placeholder)
   - `ChatScreen` receives `onNavigateToSettings` callback
   - Navigation uses `popUpTo` with `saveState=true`, `launchSingleTop=true`, `restoreState=true`
   - IMPORTANT: Wrap `Scaffold` in `WarpedTheme` and use `Modifier.padding(innerPadding)` on NavHost

3. **Update `app/src/main/java/com/warped/MainActivity.kt`** — Replace the placeholder `Text("Warped")` with `WarpedNavGraph()`. Keep `@AndroidEntryPoint`, `enableEdgeToEdge()`, `WarpedTheme`.
</action>
<acceptance_criteria>
- `grep -q 'sealed class Screen' app/src/main/java/com/warped/ui/navigation/Screen.kt`
- `grep -q 'data object Chat.*"chat"' app/src/main/java/com/warped/ui/navigation/Screen.kt`
- `grep -q 'data object Endpoints.*"endpoints"' app/src/main/java/com/warped/ui/navigation/Screen.kt`
- `grep -q 'data object Models.*"models"' app/src/main/java/com/warped/ui/navigation/Screen.kt`
- `grep -q 'fun WarpedNavGraph' app/src/main/java/com/warped/ui/navigation/NavGraph.kt`
- `grep -q 'NavigationBar' app/src/main/java/com/warped/ui/navigation/NavGraph.kt`
- `grep -q 'NavHost' app/src/main/java/com/warped/ui/navigation/NavGraph.kt`
- `grep -q 'Scaffold' app/src/main/java/com/warped/ui/navigation/NavGraph.kt`
- `grep -q 'WarpedNavGraph()' app/src/main/java/com/warped/MainActivity.kt`
- `grep -q 'WarpedTheme' app/src/main/java/com/warped/MainActivity.kt`
</acceptance_criteria>

### Task 2: Endpoints UI (Screen + ViewModel + Components)
<read_first>
- app/src/main/java/com/warped/domain/model/Endpoint.kt
- app/src/main/java/com/warped/domain/model/ProviderType.kt
- app/src/main/java/com/warped/domain/repository/EndpointRepository.kt
- app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt
- app/src/main/java/com/warped/data/local/security/ApiKeyStore.kt
- .planning/phases/01-foundation-remote-chat/01-CONTEXT.md (D-14 for error messages, "specific ideas" for EndpointsScreen design)
</read_first>
<action>
Create the endpoints management UI. This covers PROV-01 (add endpoint), PROV-02 (edit/delete), PROV-03 (test connection).

1. **`app/src/main/java/com/warped/ui/endpoints/EndpointsUiState.kt`**:
```kotlin
package com.warped.ui.endpoints

import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.Endpoint

data class EndpointsUiState(
    val endpoints: List<Endpoint> = emptyList(),
    val isLoading: Boolean = false,
    val isFormVisible: Boolean = false,
    val editingEndpoint: Endpoint? = null,
    val formName: String = "",
    val formUrl: String = "",
    val formApiType: String = "OPENAI",
    val formApiKey: String = "",
    val testStatus: Map<Long, ConnectionStatus> = emptyMap(),
    val error: String? = null
)
```

2. **`app/src/main/java/com/warped/ui/endpoints/EndpointsViewModel.kt`** — `@HiltViewModel` with:
   - Injects `EndpointRepository`, `ProviderRouter`, `ApiKeyStore`, `SavedStateHandle`
   - `observeEndpoints()` → collects endpoint flow into `_uiState`
   - `showAddForm()` → sets `isFormVisible=true`, `editingEndpoint=null`, clears form fields
   - `showEditForm(endpoint: Endpoint)` → sets form fields from endpoint
   - `saveEndpoint()` → validates name/URL non-empty, calls `endpointRepository.saveEndpoint()`, if API key is non-empty calls `apiKeyStore.storeKey(endpointId, apiKey.toCharArray())`, then hides form
   - `deleteEndpoint(endpointId: Long)` → calls `endpointRepository.deleteEndpoint()` (which also deletes key via repo)
   - `testConnection(endpointId: Long)` → gets endpoint from dao, resolves provider via `ProviderRouter.resolve(endpoint)`, calls `provider.testConnection()`, updates `testStatus` map
   - `activateEndpoint(endpointId: Long)` → calls `endpointRepository.activateEndpoint()`
   - `dismissForm()` → hides form
   - `updateFormField(field, value)` → updates form state fields

3. **`app/src/main/java/com/warped/ui/endpoints/EndpointsScreen.kt`** — Compose screen:
   - Observes `uiState.collectAsStateWithLifecycle()`
   - `Scaffold` with `TopAppBar` ("Endpoints") and FAB ("+" to add)
   - `LazyColumn` of `EndpointCard` items
   - If `isFormVisible`, show `EndpointForm` as a bottom sheet or full-screen dialog
   - Uses `hiltViewModel()`

4. **`app/src/main/java/com/warped/ui/endpoints/components/EndpointCard.kt`** — Card composable showing:
   - Endpoint name, URL, provider type badge (colored chip)
   - "Test" button with loading spinner (shows `ConnectionStatus`)
   - Active indicator (green dot if `isActive`)
   - Edit/Delete icon buttons
   - On click → navigates to edit, or can be configured to activate

5. **`app/src/main/java/com/warped/ui/endpoints/components/EndpointForm.kt`** — Form composable with:
   - `OutlinedTextField` for name (required)
   - `OutlinedTextField` for URL (required, with keyboard type = Uri)
   - `ExposedDropdownMenuBox` for provider type (OPENAI, OLLAMA, LM_STUDIO, CUSTOM)
   - `OutlinedTextField` for API key (with visibility toggle, `visualTransformation = PasswordVisualTransformation()`)
   - Save and Cancel buttons
   - URL validation: warn if `http://` is used on non-LAN IP

Note: `apiKeyStore.storeKey()` is called in the ViewModel after the endpoint is saved (so we have the endpoint ID). The API key field in the form is a simple String for input; the ViewModel converts it to `CharArray` for storage.
</action>
<acceptance_criteria>
- `grep -q 'data class EndpointsUiState' app/src/main/java/com/warped/ui/endpoints/EndpointsUiState.kt`
- `grep -q 'isFormVisible' app/src/main/java/com/warped/ui/endpoints/EndpointsUiState.kt`
- `grep -q 'testStatus.*ConnectionStatus' app/src/main/java/com/warped/ui/endpoints/EndpointsUiState.kt`
- `grep -q 'class EndpointsViewModel' app/src/main/java/com/warped/ui/endpoints/EndpointsViewModel.kt`
- `grep -q '@HiltViewModel' app/src/main/java/com/warped/ui/endpoints/EndpointsViewModel.kt`
- `grep -q 'ProviderRouter' app/src/main/java/com/warped/ui/endpoints/EndpointsViewModel.kt`
- `grep -q 'ApiKeyStore' app/src/main/java/com/warped/ui/endpoints/EndpointsViewModel.kt`
- `grep -q 'fun testConnection' app/src/main/java/com/warped/ui/endpoints/EndpointsViewModel.kt`
- `grep -q 'fun saveEndpoint' app/src/main/java/com/warped/ui/endpoints/EndpointsViewModel.kt`
- `grep -q 'fun deleteEndpoint' app/src/main/java/com/warped/ui/endpoints/EndpointsViewModel.kt`
- `grep -q 'fun EndpointsScreen' app/src/main/java/com/warped/ui/endpoints/EndpointsScreen.kt`
- `grep -q 'EndpointCard' app/src/main/java/com/warped/ui/endpoints/EndpointsScreen.kt`
- `grep -q 'fun EndpointCard' app/src/main/java/com/warped/ui/endpoints/components/EndpointCard.kt`
- `grep -q 'fun EndpointForm' app/src/main/java/com/warped/ui/endpoints/components/EndpointForm.kt`
- `grep -q 'PasswordVisualTransformation' app/src/main/java/com/warped/ui/endpoints/components/EndpointForm.kt` OR `grep -q 'visualTransformation' app/src/main/java/com/warped/ui/endpoints/components/EndpointForm.kt`
</acceptance_criteria>

### Task 3: Chat UI State + ViewModel
<read_first>
- app/src/main/java/com/warped/domain/model/ChatMessage.kt
- app/src/main/java/com/warped/domain/model/Conversation.kt
- app/src/main/java/com/warped/domain/model/ProviderType.kt
- app/src/main/java/com/warped/domain/model/StreamToken.kt
- app/src/main/java/com/warped/domain/model/ChatRequest.kt
- app/src/main/java/com/warped/domain/model/ConnectionStatus.kt
- app/src/main/java/com/warped/domain/repository/ChatRepository.kt
- app/src/main/java/com/warped/domain/repository/EndpointRepository.kt
- app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §7.1, §7.2, §7.3, §7.4 (lines 1332-1597 for ChatUiState, ChatViewModel, ChatScreen structure, token batching)
- .planning/research/PITFALLS.md §3.1 (token batching), §3.4 (scroll position)
</read_first>
<action>
Create the chat state management and ViewModel. This is the core of the app — it drives streaming token-by-token chat.

1. **`app/src/main/java/com/warped/ui/chat/ChatUiState.kt`** — Exact from RESEARCH.md §7.1 lines 1335-1360:
```kotlin
package com.warped.ui.chat

import com.warped.domain.model.ChatMessage
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.Conversation
import com.warped.domain.model.ProviderType

data class ChatUiState(
    val conversationId: Long? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val streamingContent: String = "",
    val selectedProvider: ProviderType? = null,
    val selectedModelId: String? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Unknown,
    val error: ChatError? = null,
    val conversations: List<Conversation> = emptyList(),
    val isStreaming: Boolean = false
)

sealed class ChatError {
    data class Network(val message: String) : ChatError()
    data class Server(val code: Int, val message: String) : ChatError()
    data class Auth(val message: String) : ChatError()
    data object ConnectionLost : ChatError()
    data class Unknown(val message: String) : ChatError()
}
```

2. **`app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`** — Based on RESEARCH.md §7.2 lines 1364-1503 with token batching from §7.4 lines 1575-1598. Full `@HiltViewModel` implementation:
   - Inject: `ChatRepository`, `EndpointRepository`, `ProviderRouter`, `SavedStateHandle`
   - `SendMessage(text: String)`:
     - Guard: text not blank, model/provider selected
     - Append user message to `_uiState.messages`
     - Call `ensureConversation()` → creates conversation if first message (via `chatRepository.createConversation()`)
     - Save user message via `chatRepository.saveMessage(conversationId, userMsg)`
     - Get active endpoint → resolve provider via `providerRouter.resolve(endpoint)`
     - Build `ChatRequest(messages, GenerationParameters())`
     - Launch generation coroutine stored in `generationJob`
     - **Token batching**: Accumulate tokens in `tokenBuffer` (list), emit batched to `streamingContent` every 50ms using `System.currentTimeMillis()` comparison. Flush remaining on `Done`.
     - On `StreamToken.Done`: create assistant `ChatMessage`, save via `chatRepository.saveMessage()`, clear streaming content, set `isGenerating=false`
     - On `StreamToken.Error`: set error state, stop generating
   - `stopGeneration()`: Cancel `generationJob`, set `isGenerating=false`, `isStreaming=false`. Partial streaming content is preserved.
   - `selectConversation(conversationId: Long)`: Load conversation+messages via `chatRepository.loadConversation()`, restore UI state
   - `newConversation()`: Clear messages, reset conversationId, preserve provider/model selection
   - `updateInput(text: String)`: Update `inputText`
   - `setSelectedModel(modelId: String)`: Update `selectedModelId`
   - `setSelectedProvider(providerType: ProviderType)`: Update `selectedProvider`
   - `observeConversations()` in init block: Collect `chatRepository.observeConversations()` flow
   - `ensureConversation()`: Private suspend — create conversation if `conversationId` is null, using first message content (truncated 50 chars) as title
</action>
<acceptance_criteria>
- `grep -q 'data class ChatUiState' app/src/main/java/com/warped/ui/chat/ChatUiState.kt`
- `grep -q 'sealed class ChatError' app/src/main/java/com/warped/ui/chat/ChatUiState.kt`
- `grep -q 'Network(val message: String)' app/src/main/java/com/warped/ui/chat/ChatUiState.kt`
- `grep -q 'ConnectionLost' app/src/main/java/com/warped/ui/chat/ChatUiState.kt`
- `grep -q 'class ChatViewModel' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
- `grep -q '@HiltViewModel' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
- `grep -q 'fun sendMessage' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
- `grep -q 'fun stopGeneration' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
- `grep -q 'ProviderRouter' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
- `grep -q 'ChatRepository' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
- `grep -q 'fun selectConversation' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
- `grep -q 'fun newConversation' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
- `grep -q 'generationJob' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt`
- `grep -q 'tokenBuffer\|lastEmitTime\|50' app/src/main/java/com/warped/ui/chat/ChatViewModel.kt` — token batching at 50ms
</acceptance_criteria>

### Task 4: ChatScreen + Chat Components
<read_first>
- app/src/main/java/com/warped/ui/chat/ChatViewModel.kt
- app/src/main/java/com/warped/ui/chat/ChatUiState.kt
- app/src/main/java/com/warped/domain/model/ChatMessage.kt
- app/src/main/java/com/warped/domain/model/Role.kt
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §7.3 (lines 1507-1568 for ChatScreen structure)
- .planning/research/PITFALLS.md §3.4 (scroll position during streaming — auto-scroll only if near bottom)
</read_first>
<action>
Create the ChatScreen and all sub-components.

1. **`app/src/main/java/com/warped/ui/chat/ChatScreen.kt`** — Main chat composable following RESEARCH.md §7.3:
   - `viewModel: ChatViewModel = hiltViewModel()`
   - `val uiState by viewModel.uiState.collectAsStateWithLifecycle()`
   - `Scaffold` with:
     - `topBar`: `TopAppBar` with title = ModelSelector dropdown (shows selected model/provider or "Select a model"). Settings gear icon → `onNavigateToSettings`.
     - `bottomBar`: `ChatInputBar` with text, send/stop button, input field
     - `content` area: `LazyColumn` with `rememberLazyListState()`
       - Messages rendered as `MessageBubble` with key = message.id
       - Streaming content rendered as a special `MessageBubble` with key = "streaming" (assistant role, streaming content)
       - Error banner if `uiState.error != null`
       - Conversation list drawer (expandable from top bar hamburger or pull from left)
   - **Scroll behavior** (PITFALLS.md §3.4): In a `LaunchedEffect` keyed on `uiState.streamingContent.length`, if `lazyListState.firstVisibleItemIndex >= lazyListState.layoutInfo.totalItemsCount - 3`, auto-scroll to bottom. Otherwise, show a floating "↓" button.

2. **`app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt`**:
   - Takes `ChatMessage` parameter
   - User messages: right-aligned, colored bubble (using theme UserBubble colors)
   - Assistant messages: left-aligned, surfaceVariant bubble
   - Shows role label ("You" / "Assistant") in caption text
   - Supports markdown rendering via `buildAnnotatedString` (basic: bold, italic, code blocks)
   - Rounded shape with 12dp corners

3. **`app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt`**:
   - `OutlinedTextField` or `TextField` with `Modifier.weight(1f)` and placeholder "Type a message..."
   - Single row: text field + send/stop button
   - When `isGenerating=true` → show Stop button (red square icon), disable text field
   - When `isGenerating=false` → show Send button (arrow icon), enable text field
   - Send on IME action `ImeAction.Send` and on button click
   - Clear input after send

4. **`app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt`**:
   - Dropdown composable showing selected model/provider
   - When expanded, show list of available models from active endpoint
   - Each item shows model name and provider type
   - On model selection, calls `viewModel.setSelectedModel(id)` and `viewModel.setSelectedProvider(type)`
   - Shows "No models loaded" if empty

5. **`app/src/main/java/com/warped/ui/chat/components/ConversationList.kt`**:
   - `ModalDrawerSheet` or side panel showing list of conversations from `uiState.conversations`
   - Each item shows conversation title and timestamp
   - On click → `viewModel.selectConversation(id)`, close drawer
   - "New Chat" button at top → `viewModel.newConversation()`, close drawer
   - Active conversation highlighted

6. **`app/src/main/java/com/warped/ui/chat/components/StreamingText.kt`**:
   - Animated text composable for streaming content
   - Shows a blinking cursor at end during active streaming (`isStreaming=true`)
   - Uses `AnimatedVisibility` for fade-in effect on new tokens

7. **`app/src/main/java/com/warped/ui/models/ModelsPlaceholderScreen.kt`**:
   - Simple composable with centered text "Models coming in Phase 2"
   - Uses Material 3 `Text` with headline style
</action>
<acceptance_criteria>
- `grep -q 'fun ChatScreen' app/src/main/java/com/warped/ui/chat/ChatScreen.kt`
- `grep -q 'hiltViewModel()' app/src/main/java/com/warped/ui/chat/ChatScreen.kt`
- `grep -q 'collectAsStateWithLifecycle()' app/src/main/java/com/warped/ui/chat/ChatScreen.kt`
- `grep -q 'LazyColumn' app/src/main/java/com/warped/ui/chat/ChatScreen.kt`
- `grep -q 'ChatInputBar' app/src/main/java/com/warped/ui/chat/ChatScreen.kt`
- `grep -q 'ModelSelector' app/src/main/java/com/warped/ui/chat/ChatScreen.kt`
- `grep -q 'rememberLazyListState' app/src/main/java/com/warped/ui/chat/ChatScreen.kt`
- `grep -q 'fun MessageBubble' app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt`
- `grep -q 'Role.USER\|Role.ASSISTANT' app/src/main/java/com/warped/ui/chat/components/MessageBubble.kt`
- `grep -q 'fun ChatInputBar' app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt`
- `grep -q 'isGenerating' app/src/main/java/com/warped/ui/chat/components/ChatInputBar.kt`
- `grep -q 'fun ModelSelector' app/src/main/java/com/warped/ui/chat/components/ModelSelector.kt`
- `grep -q 'fun ConversationList' app/src/main/java/com/warped/ui/chat/components/ConversationList.kt`
- `grep -q 'fun StreamingText' app/src/main/java/com/warped/ui/chat/components/StreamingText.kt`
- `grep -q 'fun ModelsPlaceholderScreen' app/src/main/java/com/warped/ui/models/ModelsPlaceholderScreen.kt`
- `grep -q 'Phase 2' app/src/main/java/com/warped/ui/models/ModelsPlaceholderScreen.kt`
</acceptance_criteria>

### Task 5: Hilt DI Modules (All 5)
<read_first>
- app/src/main/java/com/warped/data/local/db/AppDatabase.kt
- app/src/main/java/com/warped/data/local/db/dao/ConversationDao.kt
- app/src/main/java/com/warped/data/local/db/dao/MessageDao.kt
- app/src/main/java/com/warped/data/local/db/dao/RemoteEndpointDao.kt
- app/src/main/java/com/warped/data/local/security/KeystoreManager.kt
- app/src/main/java/com/warped/data/local/security/ApiKeyStore.kt
- app/src/main/java/com/warped/data/repository/ChatRepositoryImpl.kt
- app/src/main/java/com/warped/data/repository/EndpointRepositoryImpl.kt
- app/src/main/java/com/warped/data/repository/ModelRepositoryImpl.kt
- app/src/main/java/com/warped/data/remote/network/HttpClientFactory.kt
- app/src/main/java/com/warped/data/remote/network/AuthInterceptor.kt
- app/src/main/java/com/warped/data/remote/provider/OpenAIProvider.kt
- app/src/main/java/com/warped/data/remote/provider/OllamaProvider.kt
- app/src/main/java/com/warped/data/remote/provider/LMStudioProvider.kt
- app/src/main/java/com/warped/data/remote/provider/CustomProvider.kt
- app/src/main/java/com/warped/data/remote/provider/ProviderRouter.kt
- .planning/phases/01-foundation-remote-chat/01-RESEARCH.md §5 (lines 1082-1236 for all Hilt module code)
</read_first>
<action>
Create all 5 Hilt DI modules in `app/src/main/java/com/warped/di/`. These wire the entire dependency graph.

1. **`app/src/main/java/com/warped/di/DatabaseModule.kt`** — Exact from RESEARCH.md §5.2 lines 1106-1127:
   - `@Module @InstallIn(SingletonComponent::class) object DatabaseModule`
   - `provideDatabase(@ApplicationContext context: Context): AppDatabase` — `Room.databaseBuilder(context, AppDatabase::class.java, "warped.db").fallbackToDestructiveMigration().build()`
   - `provideConversationDao(db)`, `provideMessageDao(db)`, `provideRemoteEndpointDao(db)`

2. **`app/src/main/java/com/warped/di/NetworkModule.kt`** — Exact from RESEARCH.md §5.3 lines 1133-1169:
   - `@Module @InstallIn(SingletonComponent::class) object NetworkModule`
   - `provideLoggingInterceptor(): HttpLoggingInterceptor` — `HEADERS` in debug, `NONE` in release. NEVER `BODY` level (would log tokens per PITFALLS §6.2).
   - `provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor, authInterceptor: AuthInterceptor): OkHttpClient` — 30s connect, 120s read, 30s write, connection pool (5, 1 min), `retryOnConnectionFailure(true)`. Add both interceptors.
   - `provideJson(): Json` — `Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true; coerceInputValues = true }`

3. **`app/src/main/java/com/warped/di/RepositoryModule.kt`** — Exact from RESEARCH.md §5.4 lines 1174-1191:
   - `@Module @InstallIn(SingletonComponent::class) abstract class RepositoryModule`
   - `@Binds @Singleton abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository`
   - `@Binds @Singleton abstract fun bindEndpointRepository(impl: EndpointRepositoryImpl): EndpointRepository`
   - `@Binds @Singleton abstract fun bindModelRepository(impl: ModelRepositoryImpl): ModelRepository`

4. **`app/src/main/java/com/warped/di/SecurityModule.kt`** — Exact from RESEARCH.md §5.5 lines 1196-1211:
   - `@Module @InstallIn(SingletonComponent::class) object SecurityModule`
   - `provideKeystoreManager(@ApplicationContext context: Context): KeystoreManager`
   - `provideApiKeyStore(keystoreManager: KeystoreManager): ApiKeyStore`

5. **`app/src/main/java/com/warped/di/ProviderModule.kt`** — Exact from RESEARCH.md §5.6 lines 1217-1236:
   - `@Module @InstallIn(SingletonComponent::class) object ProviderModule`
   - `provideProviderRouter(openAIProvider: Provider<OpenAIProvider>, ollamaProvider: Provider<OllamaProvider>, lmStudioProvider: Provider<LMStudioProvider>, customProvider: Provider<CustomProvider>): ProviderRouter`
   - Uses `javax.inject.Provider` to lazy-load provider instances

6. **Update `app/src/main/java/com/warped/WarpedApplication.kt`** — Replace `Timber.plant(Timber.DebugTree())` with the `RedactingTree` implementation (inline inner class or separate file). The `RedactingTree` from RESEARCH.md §8.3 lines 1711-1724:
   - Extends `Timber.DebugTree()`
   - Overrides `log()` to redact API key patterns (`api[_-]?key|secret|token|authorization`, `Bearer\s+\S+`) with `[REDACTED]`
</action>
<acceptance_criteria>
- `grep -q '@Module.*DatabaseModule' app/src/main/java/com/warped/di/DatabaseModule.kt`
- `grep -q 'InstallIn(SingletonComponent::class)' app/src/main/java/com/warped/di/DatabaseModule.kt`
- `grep -q 'Room.databaseBuilder' app/src/main/java/com/warped/di/DatabaseModule.kt`
- `grep -q 'fallbackToDestructiveMigration()' app/src/main/java/com/warped/di/DatabaseModule.kt`
- `grep -q 'provideConversationDao' app/src/main/java/com/warped/di/DatabaseModule.kt`
- `grep -q '@Module.*NetworkModule' app/src/main/java/com/warped/di/NetworkModule.kt`
- `grep -q 'provideOkHttpClient' app/src/main/java/com/warped/di/NetworkModule.kt`
- `grep -q 'provideJson' app/src/main/java/com/warped/di/NetworkModule.kt`
- `grep -q 'ignoreUnknownKeys = true' app/src/main/java/com/warped/di/NetworkModule.kt`
- `grep -q 'BODY\|HEADERS' app/src/main/java/com/warped/di/NetworkModule.kt` — logging level NOT BODY
- `grep -q '@Module.*RepositoryModule' app/src/main/java/com/warped/di/RepositoryModule.kt`
- `grep -q 'abstract class RepositoryModule' app/src/main/java/com/warped/di/RepositoryModule.kt`
- `grep -q '@Binds.*bindChatRepository' app/src/main/java/com/warped/di/RepositoryModule.kt`
- `grep -q '@Binds.*bindEndpointRepository' app/src/main/java/com/warped/di/RepositoryModule.kt`
- `grep -q '@Binds.*bindModelRepository' app/src/main/java/com/warped/di/RepositoryModule.kt`
- `grep -q '@Module.*SecurityModule' app/src/main/java/com/warped/di/SecurityModule.kt`
- `grep -q 'provideKeystoreManager' app/src/main/java/com/warped/di/SecurityModule.kt`
- `grep -q 'provideApiKeyStore' app/src/main/java/com/warped/di/SecurityModule.kt`
- `grep -q '@Module.*ProviderModule' app/src/main/java/com/warped/di/ProviderModule.kt`
- `grep -q 'provideProviderRouter' app/src/main/java/com/warped/di/ProviderModule.kt`
- `grep -q 'Provider<OpenAIProvider>' app/src/main/java/com/warped/di/ProviderModule.kt`
- `grep -q 'RedactingTree' app/src/main/java/com/warped/WarpedApplication.kt`
- `grep -q 'REDACTED' app/src/main/java/com/warped/WarpedApplication.kt`
</acceptance_criteria>
