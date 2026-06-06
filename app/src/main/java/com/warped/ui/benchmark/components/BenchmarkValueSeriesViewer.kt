package com.warped.ui.benchmark.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun BenchmarkValueSeriesViewer(
    values: List<Float>,
    modifier: Modifier = Modifier,
) {
    if (values.size < 2) return
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.size(width = 96.dp, height = 28.dp)) {
        val min = values.min()
        val max = values.max()
        val range = (max - min).coerceAtLeast(0.001f)
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - min) / range) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
