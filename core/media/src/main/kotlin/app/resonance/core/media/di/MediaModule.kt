package app.resonance.core.media.di

import app.resonance.core.domain.playback.PlaybackController
import app.resonance.core.media.controller.MediaControllerPlaybackController
import app.resonance.core.media.controller.PlaybackConnection
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface MediaModule {
    @Binds fun playbackController(impl: MediaControllerPlaybackController): PlaybackController

    @Binds fun playbackConnection(impl: MediaControllerPlaybackController): PlaybackConnection
}
