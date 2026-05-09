package com.warped.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFFD97757),
    inactiveColor: Color = Color(0xFF555555),
    activeSize: Dp = 10.dp,
    inactiveSize: Dp = 6.dp
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage
            val size by animateDpAsState(
                targetValue = if (isActive) activeSize else inactiveSize,
                label = "dotSize"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(size)
                    .clip(CircleShape)
                    .background(if (isActive) activeColor else inactiveColor)
            )
        }
    }
}
