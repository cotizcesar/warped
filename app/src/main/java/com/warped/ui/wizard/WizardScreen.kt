package com.warped.ui.wizard

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warped.ui.components.WarpedAlertDialog

private val CardBg = Color(0xFF2B2B29)
private val Accent = Color(0xFFD97757)
private val TextPrimary = Color(0xFFECECEC)
private val TextSecondary = Color(0xFF9CA3AF)
private val DotInactive = Color(0xFF555555)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardScreen(
    onWizardComplete: () -> Unit,
    viewModel: WizardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(
        initialPage = uiState.currentPage,
        pageCount = { viewModel.pageCount }
    )

    LaunchedEffect(uiState.currentPage) {
        pagerState.animateScrollToPage(uiState.currentPage)
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != uiState.currentPage) {
            viewModel.goToPage(pagerState.currentPage)
        }
    }

    if (uiState.showSkipAllConfirm) {
        WarpedAlertDialog(
            onDismissRequest = { viewModel.dismissSkipAllConfirm() },
            title = { Text("Skip wizard", color = TextPrimary) },
            text = {
                Text(
                    "Skip the onboarding wizard? You can re-open it anytime from Settings.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.confirmSkipAll()
                    onWizardComplete()
                }) {
                    Text("Skip", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSkipAllConfirm() }) {
                    Text("Cancel")
                }
            }
        )
    }

    val isFirstPage = uiState.currentPage == 0
    val isLastPage = uiState.currentPage == viewModel.pageCount - 1

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = viewModel.stepLabels[uiState.currentPage],
                            color = TextPrimary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(viewModel.pageCount) { index ->
                                val isActive = index == uiState.currentPage
                                val size by animateDpAsState(
                                    targetValue = if (isActive) 10.dp else 6.dp,
                                    label = "dotSize"
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .size(size)
                                        .clip(CircleShape)
                                        .background(if (isActive) Accent else DotInactive)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (!isFirstPage) {
                        IconButton(onClick = { viewModel.goToPreviousPage() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = {
                        if (isFirstPage) {
                            viewModel.showSkipAllConfirm()
                        } else {
                            viewModel.skipCurrentStep()
                        }
                    }) {
                        Text(
                            text = if (isFirstPage) "Skip all" else "Skip",
                            color = Accent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            Surface(
                color = Color(0xFF1F1F1E),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isFirstPage) {
                        TextButton(onClick = { viewModel.goToPreviousPage() }) {
                            Text("Back", color = TextSecondary)
                        }
                    } else {
                        Spacer(Modifier.width(64.dp))
                    }

                    if (isLastPage) {
                        Button(
                            onClick = {
                                viewModel.completeWizard()
                                onWizardComplete()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Accent
                            )
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Done")
                        }
                    } else {
                        Button(
                            onClick = {
                                viewModel.skipCurrentStep()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Accent
                            )
                        ) {
                            Text("Next")
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFF1F1F1E)
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            userScrollEnabled = true
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${page + 1} / ${viewModel.pageCount}",
                            color = Accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = viewModel.stepLabels[page],
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = viewModel.stepDescriptions[page],
                            color = TextSecondary,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        }
    }
}
