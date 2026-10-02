package com.warped.data.grounding

/**
 * Quick-task (image-grid): render-side image-list mapping.
 *
 * http(s) gate + trim + distinct + cap. Pure Kotlin — JVM-testable.
 * Defense-in-depth over the (empty) DDG fuse-time images list;
 * image-intent turns fuse zero images with text grounding preserved,
 * and the grid calls this on the ephemeral message field before
 * rendering.
 */
object GroundedImages {

    /** Render-list cap for the image grid (the DDG fuse path always emits an empty list). */
    const val MAX_GRID_IMAGES = 10

    fun visibleImages(images: List<String>): List<String> =
        images
            .map { it.trim() }
            .filter {
                it.startsWith("http://", ignoreCase = true) ||
                    it.startsWith("https://", ignoreCase = true)
            }
            .distinct()
            .take(MAX_GRID_IMAGES)
}
