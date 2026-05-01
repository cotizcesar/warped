package com.warped.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warped.R
import com.warped.domain.model.Role
import com.warped.ui.chat.components.ChatInputBar
import com.warped.ui.chat.components.ConversationList
import com.warped.ui.chat.components.MessageBubble
import com.warped.ui.chat.components.ModelSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    LaunchedEffect(uiState.streamingContent.length, uiState.messages.size) {
        if (uiState.messages.isNotEmpty() || uiState.streamingContent.isNotEmpty()) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            if (lastVisible >= totalItems - 3 || totalItems == 0) {
                listState.animateScrollToItem(maxOf(0, totalItems - 1))
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationList(
                conversations = uiState.conversations,
                activeConversationId = uiState.conversationId,
                onSelect = { id ->
                    viewModel.selectConversation(id)
                },
                onNewChat = { viewModel.newConversation() }
            )
        }
    ) {
        Scaffold(
            bottomBar = {
                ChatInputBar(
                    text = uiState.inputText,
                    isGenerating = uiState.isStreaming,
                    canSend = uiState.selectedModelId != null,
                    onTextChange = { viewModel.updateInput(it) },
                    onSend = { viewModel.sendMessage(uiState.inputText) },
                    onStop = { viewModel.stopGeneration() }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    ModelSelector(
                        selectedModelId = uiState.selectedModelId,
                        selectedProvider = uiState.selectedProvider,
                        localModels = uiState.localModels,
                        onModelSelected = { modelId, provider ->
                            viewModel.setSelectedModel(modelId, provider)
                        }
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        MessageBubble(message = message)
                    }
                    if (uiState.streamingContent.isNotEmpty()) {
                        item(key = "streaming") {
                            MessageBubble(
                                message = com.warped.domain.model.ChatMessage(
                                    role = Role.ASSISTANT,
                                    content = uiState.streamingContent
                                ),
                                isStreaming = true
                            )
                        }
                    }
                }

                if (uiState.error != null) {
                    Snackbar(
                        modifier = Modifier.padding(16.dp),
                        action = {
                            TextButton(onClick = { viewModel.clearError() }) {
                                Text("Dismiss")
                            }
                        }
                    ) {
                        Text(
                            when (val error = uiState.error) {
                                is ChatError.Network -> error.message
                                is ChatError.Server -> error.message
                                is ChatError.Auth -> error.message
                                is ChatError.NoModelSelected -> stringResource(R.string.no_model_selected)
                                is ChatError.DownloadModelFirst -> stringResource(R.string.download_model_first)
                                is ChatError.ConnectionLost -> "Connection lost. Tap to retry."
                                is ChatError.Unknown -> error.message
                                null -> ""
                            }
                        )
                    }
                }
            }
        }
    }
}
