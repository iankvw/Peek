package com.iank.im.peek.di

import com.iank.im.peek.data.repository.ConversationRepositoryImpl
import com.iank.im.peek.data.repository.SettingsRepositoryImpl
import com.iank.im.peek.data.repository.VisionAiRepositoryImpl
import com.iank.im.peek.domain.repository.ConversationRepository
import com.iank.im.peek.domain.repository.SettingsRepository
import com.iank.im.peek.domain.repository.VisionAiRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    @Singleton
    abstract fun bindConversationRepository(
        impl: ConversationRepositoryImpl
    ): ConversationRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindVisionAiRepository(
        impl: VisionAiRepositoryImpl
    ): VisionAiRepository
}
