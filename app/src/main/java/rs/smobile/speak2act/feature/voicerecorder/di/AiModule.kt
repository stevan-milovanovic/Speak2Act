package rs.smobile.speak2act.feature.voicerecorder.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import rs.smobile.speak2act.feature.voicerecorder.data.FirebaseSpeechToTransactionAiService
import rs.smobile.speak2act.feature.voicerecorder.domain.SpeechToTransactionAiService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds
    @Singleton
    abstract fun bindAiService(
        impl: FirebaseSpeechToTransactionAiService
    ): SpeechToTransactionAiService
}
