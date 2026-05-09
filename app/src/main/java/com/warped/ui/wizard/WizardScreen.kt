package com.warped.ui.wizard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warped.ui.components.PageIndicator
import com.warped.ui.components.WarpedAlertDialog

private val Accent = Color(0xFFD97757)
private val TextPrimary = Color(0xFFECECEC)
private val TextSecondary = Color(0xFF9CA3AF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardScreen(
    onWizardComplete: () -> Unit,
    onNavigate: (String) -> Unit = {},
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
    val currentStep = viewModel.steps[uiState.currentPage]

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentStep.title,
                            color = TextPrimary
                        )
                        PageIndicator(
                            pageCount = viewModel.pageCount,
                            currentPage = uiState.currentPage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
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
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            shape = RoundedCornerShape(12.dp)
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
                            onClick = { viewModel.skipCurrentStep() },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            shape = RoundedCornerShape(12.dp)
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
            val step = viewModel.steps[page]
            StepContent(
                step = step,
                contextData = uiState.contextData,
                onCtaClick = {
                    if (step.ctaRoute.isNotEmpty()) {
                        onNavigate(step.ctaRoute)
                    }
                }
            )
        }
    }
}
