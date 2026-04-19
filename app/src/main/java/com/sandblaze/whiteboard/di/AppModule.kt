package com.sandblaze.whiteboard.di

import android.content.Context
import com.sandblaze.whiteboard.data.WhiteboardRepositoryImpl
import com.sandblaze.whiteboard.data.local.LocalWhiteboardStorage
import com.sandblaze.whiteboard.domain.WhiteboardRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideLocalWhiteboardStorage(@ApplicationContext context: Context): LocalWhiteboardStorage {
        return LocalWhiteboardStorage(context)
    }

    @Provides
    @Singleton
    fun provideWhiteboardRepository(impl: WhiteboardRepositoryImpl): WhiteboardRepository {
        return impl
    }
}
