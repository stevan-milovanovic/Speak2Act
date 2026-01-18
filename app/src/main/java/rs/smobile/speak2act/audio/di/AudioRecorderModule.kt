package rs.smobile.speak2act.audio.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import rs.smobile.speak2act.audio.data.AndroidAudioRecorder
import rs.smobile.speak2act.audio.domain.AudioRecorder
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
abstract class AudioRecorderModule {
    @Binds
    @Singleton
    abstract fun bindAudioRecorder(
        impl: AndroidAudioRecorder
    ): AudioRecorder
}