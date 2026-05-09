package com.warped.ui.wizard

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warped.R
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
    isReEntry: Boolean = false,
    onBackFromReEntry: () -> Unit = {},
    viewModel: WizardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(isReEntry) {
        viewModel.setIsReEntry(isReEntry)
    }

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
            title = {
                Text(
                    text = stringResource(
                        if (uiState.isReEntry) R.string.wizard_close_confirm_title
                        else R.string.wizard_skip_confirm_title
                    ),
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = stringResource(
                        if (uiState.isReEntry) R.string.wizard_close_confirm_text
                        else R.string.wizard_skip_confirm_text
                    ),
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.confirmSkipAll()
                    if (uiState.isReEntry) {
                        onBackFromReEntry()
                    } else {
                        onWizardComplete()
                    }
                }) {
                    Text(
                        text = stringResource(
                            if (uiState.isReEntry) R.string.wizard_close
                            else R.string.wizard_skip
                        ),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSkipAllConfirm() }) {
                    Text(stringResource(R.string.wizard_cancel))
                }
            }
        )
    }

    if (uiState.showExitConfirm) {
        WarpedAlertDialog(
            onDismissRequest = { viewModel.dismissExitDialog() },
            title = { Text(stringResource(R.string.wizard_exit_title), color = TextPrimary) },
            text = {
                Text(stringResource(R.string.wizard_exit_text), color = TextSecondary)
            },
            confirmButton = {
                TextButton(onClick = onWizardComplete) {
                    Text(stringResource(R.string.wizard_exit), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissExitDialog() }) {
                    Text(stringResource(R.string.wizard_cancel))
                }
            }
        )
    }

    val isFirstPage = uiState.currentPage == 0
    val isLastPage = uiState.currentPage == viewModel.pageCount - 1
    val currentStep = viewModel.steps[uiState.currentPage]

    BackHandler {
        if (isFirstPage) {
            if (uiState.isReEntry) {
                onBackFromReEntry()
            } else {
                viewModel.showExitDialog()
            }
        } else {
            viewModel.goToPreviousPage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(currentStep.titleRes),
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
                    if (isFirstPage) {
                        if (uiState.isReEntry) {
                            IconButton(onClick = onBackFromReEntry) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.wizard_back),
                                    tint = TextPrimary
                                )
                            }
                        } else {
                            IconButton(onClick = { viewModel.showExitDialog() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.wizard_exit),
                                    tint = TextPrimary
                                )
                            }
                        }
                    } else {
                        IconButton(onClick = { viewModel.goToPreviousPage() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.wizard_back),
                                tint = TextPrimary
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.showSkipAllConfirm() }) {
                        Text(
                            text = when {
                                uiState.isReEntry -> stringResource(R.string.wizard_close)
                                isFirstPage -> stringResource(R.string.wizard_skip_all)
                                else -> stringResource(R.string.wizard_skip)
                            },
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
                            Text(stringResource(R.string.wizard_back), color = TextSecondary)
                        }
                    } else {
                        Spacer(Modifier.width(64.dp))
                    }

                    if (isLastPage) {
                        Button(
                            onClick = {
                                viewModel.completeWizard()
                                if (uiState.isReEntry) {
                                    onBackFromReEntry()
                                } else {
                                    onWizardComplete()
                                }
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
                            Text(stringResource(R.string.wizard_done))
                        }
                    } else {
                        Button(
                            onClick = { viewModel.skipCurrentStep() },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (uiState.isReEntry) stringResource(R.string.wizard_revisar)
                                else stringResource(R.string.wizard_next)
                            )
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
