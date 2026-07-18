package rs.smobile.speak2act.feature.voicerecorder.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import rs.smobile.speak2act.feature.voicerecorder.data.WhisperSpeechToTransactionService
import rs.smobile.speak2act.feature.voicerecorder.domain.SpeechToTransactionService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SpeechToTransactionModule {
    @Binds
    @Singleton
    abstract fun bindSpeechToTransactionService(
        impl: WhisperSpeechToTransactionService
    ): SpeechToTransactionService
}
