package com.warped.di

import com.warped.data.skills.LocalToolExecutor
import com.warped.data.skills.SkillRepositoryImpl
import com.warped.domain.skills.SkillRepository
import com.warped.domain.skills.ToolExecutor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 47-02: Hilt bindings for the Skills Lite surface. The [ToolExecutor]
 * binding now points at [LocalToolExecutor] (allowlisted local dispatch);
 * the 47-01 `NoopToolExecutor` placeholder is removed.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SkillsModule {

    @Binds
    @Singleton
    abstract fun bindSkillRepository(impl: SkillRepositoryImpl): SkillRepository

    @Binds
    @Singleton
    abstract fun bindToolExecutor(impl: LocalToolExecutor): ToolExecutor
}
