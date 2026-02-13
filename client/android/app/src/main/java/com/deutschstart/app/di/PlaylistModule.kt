package com.deutschstart.app.di

import com.deutschstart.app.playlist.PlaylistBuilder
import com.deutschstart.app.playlist.PlaylistConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object PlaylistModule {
    
    @Provides
    @ViewModelScoped
    fun providePlaylistBuilder(): PlaylistBuilder {
        return PlaylistBuilder()
    }
}
