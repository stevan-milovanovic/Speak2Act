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
import rs.smobile.speak2act.feature.actionfigure.data.MeshyActionFigureModelService
import rs.smobile.speak2act.feature.actionfigure.data.MeshyApi
import rs.smobile.speak2act.feature.actionfigure.data.ReplicateActionFigureService
import rs.smobile.speak2act.feature.actionfigure.data.ReplicateApi
import rs.smobile.speak2act.feature.actionfigure.domain.ActionFigureModelService
import rs.smobile.speak2act.feature.actionfigure.domain.ActionFigureService
import rs.smobile.speak2act.feature.actionfigure.domain.ImageUploadService
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ActionFigureModule {

    @Provides
    @ReplicateApiKey
    fun provideReplicateApiKey(): String = BuildConfig.REPLICATE_API_KEY

    @Provides
    @ReplicateOkHttpClient
    @Singleton
    fun provideReplicateOkHttpClient(@ReplicateApiKey apiKey: String): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.MINUTES)
            .addInterceptor { chain ->
                val original = chain.request()
                val newReq = original.newBuilder()
                    .header("Authorization", "Bearer $apiKey")
                    .header("Prefer", "wait")
                    .build()
                val d = chain.proceed(newReq)
                return@addInterceptor d
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
    @ReplicateRetrofit
    @Singleton
    fun provideRetrofit(
        factory: Converter.Factory,
        @ReplicateOkHttpClient client: OkHttpClient
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(ActionFigureConstants.REPLICATE_API_BASE_URL)
            .client(client)
            .addConverterFactory(factory)
            .build()

    @Provides
    @Singleton
    fun provideReplicateApi(@ReplicateRetrofit retrofit: Retrofit): ReplicateApi =
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

    @Provides
    @MeshyApiKey
    fun provideMeshyApiKey(): String = BuildConfig.MESHY_API_KEY

    @Provides
    @MeshyOkHttpClient
    @Singleton
    fun provideMeshyOkHttpClient(@MeshyApiKey apiKey: String): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.MINUTES)
            .addInterceptor { chain ->
                val original = chain.request()
                val newReq = original.newBuilder()
                    .header("Authorization", "Bearer $apiKey")
                    .build()
                val d = chain.proceed(newReq)
                return@addInterceptor d
            }
            .build()

    @OptIn(ExperimentalSerializationApi::class)
    @Provides
    @MeshyRetrofit
    @Singleton
    fun provideMeshyRetrofit(
        factory: Converter.Factory,
        @MeshyOkHttpClient client: OkHttpClient
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(ActionFigureConstants.MESHY_API_BASE_URL)
            .client(client)
            .addConverterFactory(factory)
            .build()

    @Provides
    @Singleton
    fun provideMeshyApi(@MeshyRetrofit retrofit: Retrofit): MeshyApi =
        retrofit.create(MeshyApi::class.java)

    @Provides
    @Singleton
    fun provideActionFigureModelService(
        meshyApi: MeshyApi
    ): ActionFigureModelService = MeshyActionFigureModelService(meshyApi)
}
