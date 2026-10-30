package rs.smobile.speak2act.feature.voicerecorder.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import rs.smobile.speak2act.core.config.DemoTalk
import rs.smobile.speak2act.feature.voicerecorder.data.llm.FunctionGemmaTransactionModel
import rs.smobile.speak2act.feature.voicerecorder.data.llm.NoOpOnDeviceTransactionModel
import rs.smobile.speak2act.feature.voicerecorder.domain.OnDeviceTransactionModel
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OnDeviceModelModule {
    /** Talk 3 gets FunctionGemma; talks 1 and 2 never construct it (Provider). */
    @Provides
    @Singleton
    fun provideOnDeviceTransactionModel(
        demoTalk: DemoTalk,
        functionGemma: Provider<FunctionGemmaTransactionModel>,
    ): OnDeviceTransactionModel =
        if (demoTalk.usesOnDeviceLlm) functionGemma.get() else NoOpOnDeviceTransactionModel
}
