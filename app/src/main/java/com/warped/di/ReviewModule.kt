package com.warped.di

import com.warped.domain.review.DefaultReviewFlowLauncher
import com.warped.domain.review.ReviewFlowLauncher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Phase 66 review-fix (WR-03): binds the Play review flow seam so
 * [com.warped.domain.review.ReviewHelper] depends on the
 * [ReviewFlowLauncher] interface (JVM-testable) while production gets
 * the Play-backed [DefaultReviewFlowLauncher].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReviewModule {

    @Binds
    @Singleton
    abstract fun bindReviewFlowLauncher(
        impl: DefaultReviewFlowLauncher
    ): ReviewFlowLauncher
}
