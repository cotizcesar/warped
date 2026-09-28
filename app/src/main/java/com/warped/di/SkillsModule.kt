package com.warped.di

import com.warped.data.skills.NoopToolExecutor
import com.warped.data.skills.SkillRepositoryImpl
import com.warped.domain.skills.SkillRepository
import com.warped.domain.skills.ToolExecutor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 47-01: Hilt bindings for the Skills Lite surface. The [ToolExecutor]
 * binding points at [NoopToolExecutor] until Plan 02 provides the real
 * local executor (binding target swap only, no call-site rewiring).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SkillsModule {

    @Binds
    @Singleton
    abstract fun bindSkillRepository(impl: SkillRepositoryImpl): SkillRepository

    @Binds
    @Singleton
    abstract fun bindToolExecutor(impl: NoopToolExecutor): ToolExecutor
}
