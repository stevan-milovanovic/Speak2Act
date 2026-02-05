package rs.smobile.speak2act.feature.actionfigure.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import rs.smobile.speak2act.BuildConfig
import rs.smobile.speak2act.feature.actionfigure.data.CloudinaryImageUploadService
import rs.smobile.speak2act.feature.actionfigure.data.ReplicateActionFigureService
import rs.smobile.speak2act.feature.actionfigure.domain.ActionFigureService
import rs.smobile.speak2act.feature.actionfigure.domain.ImageUploadService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ActionFigureModule {

    @Provides
    fun provideReplicateApiKey(): String = BuildConfig.REPLICATE_API_KEY

    @Provides
    @Singleton
    fun provideActionFigureService(apiKey: String): ActionFigureService =
        ReplicateActionFigureService(apiKey = apiKey)

    @Provides
    @Singleton
    fun provideImageUploadService(): ImageUploadService =
        CloudinaryImageUploadService()
}
