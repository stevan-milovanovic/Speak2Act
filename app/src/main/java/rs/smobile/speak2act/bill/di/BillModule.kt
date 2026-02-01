package rs.smobile.speak2act.bill.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import rs.smobile.speak2act.bill.BillOcrService
import rs.smobile.speak2act.bill.MlKitBillOcrService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BillModule {
    @Binds
    @Singleton
    abstract fun bindBillOcrService(impl: MlKitBillOcrService): BillOcrService
}
