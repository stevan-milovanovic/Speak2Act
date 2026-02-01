package rs.smobile.speak2act.ai

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
