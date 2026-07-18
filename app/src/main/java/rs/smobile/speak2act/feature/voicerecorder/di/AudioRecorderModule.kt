package rs.smobile.speak2act.feature.voicerecorder.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import rs.smobile.speak2act.feature.voicerecorder.data.WhisperAudioRecorder
import rs.smobile.speak2act.feature.voicerecorder.domain.AudioRecorder
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioRecorderModule {
    @Binds
    @Singleton
    abstract fun bindAudioRecorder(
        impl: WhisperAudioRecorder
    ): AudioRecorder
}
