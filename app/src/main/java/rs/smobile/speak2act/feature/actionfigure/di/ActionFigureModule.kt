package rs.smobile.speak2act.feature.actionfigure.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Converter
import retrofit2.Retrofit
import rs.smobile.speak2act.BuildConfig
import rs.smobile.speak2act.feature.actionfigure.ActionFigureConstants
import rs.smobile.speak2act.feature.actionfigure.data.CloudinaryImageUploadService
import rs.smobile.speak2act.feature.actionfigure.data.ReplicateActionFigureService
import rs.smobile.speak2act.feature.actionfigure.data.ReplicateApi
import rs.smobile.speak2act.feature.actionfigure.domain.ActionFigureService
import rs.smobile.speak2act.feature.actionfigure.domain.ImageUploadService
import rs.smobile.speak2act.feature.actionfigure.di.ReplicateApiKey
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ActionFigureModule {

    @Provides
    @ReplicateApiKey
    fun provideReplicateApiKey(): String = BuildConfig.REPLICATE_API_KEY

    @Provides
    @Singleton
    fun provideOkHttpClient(@ReplicateApiKey apiKey: String): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(2, java.util.concurrent.TimeUnit.MINUTES)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(2, java.util.concurrent.TimeUnit.MINUTES)
            .addInterceptor { chain ->
                val original = chain.request()
                val newReq = original.newBuilder()
                    .header("Authorization", "Bearer $apiKey")
                    .header("Prefer", "wait")
                    .build()
                chain.proceed(newReq)
            }
            .build()

    @OptIn(ExperimentalSerializationApi::class)
    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalSerializationApi::class)
    @Provides
    @Singleton
    fun provideJsonConverterFactory(json: Json): Converter.Factory =
        json.asConverterFactory("application/json".toMediaType())

    @OptIn(ExperimentalSerializationApi::class)
    @Provides
    @Singleton
    fun provideRetrofit(factory: Converter.Factory, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(ActionFigureConstants.REPLICATE_API_BASE_URL)
            .client(client)
            .addConverterFactory(factory)
            .build()

    @Provides
    @Singleton
    fun provideReplicateApi(retrofit: Retrofit): ReplicateApi =
        retrofit.create(ReplicateApi::class.java)

    @Provides
    @Singleton
    fun provideActionFigureService(
        replicateApi: ReplicateApi
    ): ActionFigureService =
        ReplicateActionFigureService(api = replicateApi)

    @Provides
    @Singleton
    fun provideImageUploadService(): ImageUploadService =
        CloudinaryImageUploadService()
}
