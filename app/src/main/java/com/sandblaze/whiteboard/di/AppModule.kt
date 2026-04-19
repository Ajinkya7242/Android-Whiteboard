package com.sandblaze.whiteboard.di

import com.sandblaze.whiteboard.data.WhiteboardRepositoryImpl
import com.sandblaze.whiteboard.data.local.LocalWhiteboardStorage
import com.sandblaze.whiteboard.data.local.WhiteboardStorage
import com.sandblaze.whiteboard.domain.repository.WhiteboardRepository
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
    abstract fun bindWhiteboardStorage(impl: LocalWhiteboardStorage): WhiteboardStorage

    @Binds
    @Singleton
    abstract fun bindWhiteboardRepository(impl: WhiteboardRepositoryImpl): WhiteboardRepository
}
