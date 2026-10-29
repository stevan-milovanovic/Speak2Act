package rs.smobile.speak2act.feature.voicerecorder.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import rs.smobile.speak2act.feature.voicerecorder.data.FakeTransactionExecutor
import rs.smobile.speak2act.feature.voicerecorder.domain.TransactionExecutor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TransactionExecutorModule {
    @Binds
    @Singleton
    abstract fun bindTransactionExecutor(impl: FakeTransactionExecutor): TransactionExecutor
}
