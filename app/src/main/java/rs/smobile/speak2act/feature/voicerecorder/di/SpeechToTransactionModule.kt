package rs.smobile.speak2act.feature.voicerecorder.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import rs.smobile.speak2act.feature.voicerecorder.data.GeminiTransactionService
import rs.smobile.speak2act.feature.voicerecorder.data.HybridSpeechToTransactionService
import rs.smobile.speak2act.feature.voicerecorder.data.whisper.WhisperTranscriber
import rs.smobile.speak2act.feature.voicerecorder.domain.CloudTransactionService
import rs.smobile.speak2act.feature.voicerecorder.domain.SpeechToTransactionService
import rs.smobile.speak2act.feature.voicerecorder.domain.Transcriber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SpeechToTransactionModule {
    @Binds
    @Singleton
    abstract fun bindSpeechToTransactionService(
        impl: HybridSpeechToTransactionService
    ): SpeechToTransactionService

    @Binds
    @Singleton
    abstract fun bindTranscriber(impl: WhisperTranscriber): Transcriber

    @Binds
    @Singleton
    abstract fun bindCloudTransactionService(impl: GeminiTransactionService): CloudTransactionService
}
