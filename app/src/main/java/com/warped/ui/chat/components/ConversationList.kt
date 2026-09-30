package com.warped.ui.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.warped.R
import com.warped.domain.model.Conversation
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ConversationList(
    conversations: List<Conversation>,
    activeConversationId: Long?,
    onSelect: (Long) -> Unit,
    onNewChat: () -> Unit
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.padding(16.dp)) {
            Button(
                onClick = onNewChat,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.new_chat_plus))
            }
        }
        HorizontalDivider()
        LazyColumn {
            items(conversations) { conversation ->
                ListItem(
                    headlineContent = { Text(conversation.title) },
                    supportingContent = {
                        Text(
                            conversation.updatedAt.atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
                        )
                    },
                    modifier = Modifier.clickable { onSelect(conversation.id) }
                )
                HorizontalDivider()
            }
        }
    }
}
