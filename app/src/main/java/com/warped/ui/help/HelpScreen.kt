package com.warped.ui.help

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.warped.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.help_how_to),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD97757)
                )
                Text(
                    stringResource(R.string.help_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9CA3AF)
                )
            }

            // Section 1: Download Models
            item {
                HelpSection(
                    icon = Icons.Filled.CloudDownload,
                    title = stringResource(R.string.help_s1_title),
                    steps = listOf(
                        stringResource(R.string.help_s1_step1),
                        stringResource(R.string.help_s1_step2),
                        stringResource(R.string.help_s1_step3),
                        stringResource(R.string.help_s1_step4),
                        stringResource(R.string.help_s1_step5),
                        stringResource(R.string.help_s1_step6),
                        stringResource(R.string.help_s1_step7),
                    )
                )
            }

            // Section 2: Use Local Models
            item {
                HelpSection(
                    icon = Icons.Filled.Memory,
                    title = stringResource(R.string.help_s2_title),
                    steps = listOf(
                        stringResource(R.string.help_s2_step1),
                        stringResource(R.string.help_s2_step2),
                        stringResource(R.string.help_s2_step3),
                        stringResource(R.string.help_s2_step4),
                        stringResource(R.string.help_s2_step5),
                    )
                )
            }

            // Section 3: Remote Providers
            item {
                HelpSection(
                    icon = Icons.Filled.Dns,
                    title = stringResource(R.string.help_s3_title),
                    steps = listOf(
                        stringResource(R.string.help_s3_step1),
                        stringResource(R.string.help_s3_step2),
                        stringResource(R.string.help_s3_step3),
                        stringResource(R.string.help_s3_step4),
                        stringResource(R.string.help_s3_step5),
                    )
                )
            }

            // Section 4: Multimodal
            item {
                HelpSection(
                    icon = Icons.Filled.Image,
                    title = stringResource(R.string.help_s4_title),
                    steps = listOf(
                        stringResource(R.string.help_s4_step1),
                        stringResource(R.string.help_s4_step2),
                        stringResource(R.string.help_s4_step3),
                        stringResource(R.string.help_s4_step4),
                    )
                )
            }

            // Section 5: Model Capabilities
            item {
                HelpSection(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = stringResource(R.string.help_s5_title),
                    steps = listOf(
                        stringResource(R.string.help_s5_step1),
                        stringResource(R.string.help_s5_step2),
                        stringResource(R.string.help_s5_step3),
                        stringResource(R.string.help_s5_step4),
                    )
                )
            }

            // Section 6: Tools
            item {
                HelpSection(
                    icon = Icons.Filled.Settings,
                    title = stringResource(R.string.help_s6_title),
                    steps = listOf(
                        stringResource(R.string.help_s6_step1),
                        stringResource(R.string.help_s6_step2),
                        stringResource(R.string.help_s6_step3),
                        stringResource(R.string.help_s6_step4),
                        stringResource(R.string.help_s6_step5),
                    )
                )
            }

            // Section 7: Web Grounding
            item {
                HelpSection(
                    icon = Icons.Filled.Public,
                    title = stringResource(R.string.help_s7_title),
                    steps = listOf(
                        stringResource(R.string.help_s7_step1),
                        stringResource(R.string.help_s7_step2),
                        stringResource(R.string.help_s7_step3),
                        stringResource(R.string.help_s7_step4),
                        stringResource(R.string.help_s7_step5),
                    )
                )
            }

            // Section 8: Tips
            item {
                HelpSection(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.help_s8_title),
                    steps = listOf(
                        stringResource(R.string.help_s8_step1),
                        stringResource(R.string.help_s8_step2),
                        stringResource(R.string.help_s8_step3),
                        stringResource(R.string.help_s8_step4),
                        stringResource(R.string.help_s8_step5),
                        stringResource(R.string.help_s8_step6),
                    )
                )
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun HelpSection(
    icon: ImageVector,
    title: String,
    steps: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2B2B29)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color(0xFFD97757),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
            steps.forEachIndexed { i, step ->
                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                    Text(
                        "${i + 1}. ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD97757)
                    )
                    Text(
                        step,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
