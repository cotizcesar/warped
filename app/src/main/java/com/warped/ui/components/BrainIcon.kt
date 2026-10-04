package com.warped.ui.components

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Thinking brain mark (user decision 2026-10-03): the Material
 * `Psychology` head-with-cog reads wrong, so the thinking affordance
 * (header toggle, capability badges, model cards) uses this stylized
 * brain — symmetric lobes, center groove, side sulci. Same geometry as
 * `R.drawable/brain` (kept for non-Compose use); both verified against
 * the rendered preview before shipping.
 */
val BrainIcon: ImageVector by lazy {
    materialIcon(name = "Brain") {
        materialPath(fillAlpha = 1f, strokeAlpha = 1f, pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd) {
            moveTo(12f, 2.6f)
            curveTo(8.8f, 2.6f, 6.3f, 4f, 5f, 6.1f)
            curveTo(3.5f, 6.7f, 2.4f, 8.2f, 2.4f, 10f)
            curveTo(2.4f, 11f, 2.9f, 12f, 3.7f, 12.8f)
            curveTo(3.2f, 13.6f, 2.9f, 14.6f, 2.9f, 15.7f)
            curveTo(2.9f, 18.5f, 5.2f, 20.7f, 8f, 20.7f)
            curveTo(8.7f, 20.7f, 9.4f, 20.5f, 10f, 20.2f)
            curveTo(10.6f, 21.1f, 11.2f, 21.6f, 12f, 21.6f)
            curveTo(12.8f, 21.6f, 13.4f, 21.1f, 14f, 20.2f)
            curveTo(14.6f, 20.5f, 15.3f, 20.7f, 16f, 20.7f)
            curveTo(18.8f, 20.7f, 21.1f, 18.5f, 21.1f, 15.7f)
            curveTo(21.1f, 14.6f, 20.8f, 13.6f, 20.3f, 12.8f)
            curveTo(21.1f, 12f, 21.6f, 11f, 21.6f, 10f)
            curveTo(21.6f, 8.2f, 20.5f, 6.7f, 19f, 6.1f)
            curveTo(17.7f, 4f, 15.2f, 2.6f, 12f, 2.6f)
            close()
            moveTo(11.75f, 4.2f)
            lineTo(12.25f, 4.2f)
            lineTo(12.25f, 19.8f)
            lineTo(11.75f, 19.8f)
            close()
            moveTo(13.9f, 7.1f)
            curveTo(15f, 7.1f, 16.1f, 7.6f, 17f, 8.4f)
            lineTo(16.6f, 8.9f)
            curveTo(15.8f, 8.2f, 14.8f, 7.8f, 13.8f, 7.7f)
            close()
            moveTo(13.9f, 11.3f)
            curveTo(15.1f, 11.5f, 16.3f, 12.2f, 17.1f, 13.2f)
            lineTo(16.6f, 13.6f)
            curveTo(15.9f, 12.7f, 14.8f, 12.1f, 13.7f, 11.9f)
            close()
            moveTo(13.7f, 15.6f)
            curveTo(14.7f, 15.9f, 15.6f, 16.6f, 16.2f, 17.5f)
            lineTo(15.7f, 17.8f)
            curveTo(15.1f, 17f, 14.3f, 16.4f, 13.4f, 16.2f)
            close()
            moveTo(10.1f, 7.1f)
            curveTo(9f, 7.1f, 7.9f, 7.6f, 7f, 8.4f)
            lineTo(7.4f, 8.9f)
            curveTo(8.2f, 8.2f, 9.2f, 7.8f, 10.2f, 7.7f)
            close()
            moveTo(10.1f, 11.3f)
            curveTo(8.9f, 11.5f, 7.7f, 12.2f, 6.9f, 13.2f)
            lineTo(7.4f, 13.6f)
            curveTo(8.1f, 12.7f, 9.2f, 12.1f, 10.3f, 11.9f)
            close()
            moveTo(10.3f, 15.6f)
            curveTo(9.3f, 15.9f, 8.4f, 16.6f, 7.8f, 17.5f)
            lineTo(8.3f, 17.8f)
            curveTo(8.9f, 17f, 9.7f, 16.4f, 10.6f, 16.2f)
            close()
        }
    }
}
