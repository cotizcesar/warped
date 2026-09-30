package com.warped.ui.endpoints.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.warped.R
import com.warped.domain.model.ConnectionStatus
import com.warped.domain.model.Endpoint
import com.warped.domain.model.displayNameRes

@Composable
fun EndpointCard(
    endpoint: Endpoint,
    connectionStatus: ConnectionStatus?,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onActivate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(endpoint.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        endpoint.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(endpoint.apiType.displayNameRes()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (endpoint.isActive) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.FiberManualRecord,
                            contentDescription = stringResource(R.string.badge_active),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onTest, modifier = Modifier.weight(1f)) {
                    Text(
                        when (connectionStatus) {
                            ConnectionStatus.Connected -> stringResource(R.string.connected)
                            ConnectionStatus.Connecting -> stringResource(R.string.endpoint_testing)
                            ConnectionStatus.Disconnected -> stringResource(R.string.disconnected)
                            else -> stringResource(R.string.endpoint_test)
                        }
                    )
                }
                TextButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
                TextButton(onClick = onActivate) { Text(stringResource(R.string.activate)) }
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )) { Text(stringResource(R.string.delete)) }
            }
        }
    }
}
