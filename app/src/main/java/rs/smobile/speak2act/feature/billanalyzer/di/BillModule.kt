package rs.smobile.speak2act.feature.billanalyzer.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import rs.smobile.speak2act.feature.billanalyzer.data.MlKitFirebaseAiBillAnalyzerService
import rs.smobile.speak2act.feature.billanalyzer.domain.BillAnalyzerService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BillModule {
    @Binds
    @Singleton
    abstract fun bindBillAnalyzerService(impl: MlKitFirebaseAiBillAnalyzerService): BillAnalyzerService
}
