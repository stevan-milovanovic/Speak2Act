package rs.smobile.speak2act.feature.actionfigure.di

import javax.inject.Qualifier
import kotlin.annotation.AnnotationRetention.RUNTIME

@Qualifier
@Retention(RUNTIME)
annotation class ReplicateApiKey

@Qualifier
@Retention(RUNTIME)
annotation class ReplicateOkHttpClient

@Qualifier
@Retention(RUNTIME)
annotation class ReplicateRetrofit

@Qualifier
@Retention(RUNTIME)
annotation class MeshyApiKey

@Qualifier
@Retention(RUNTIME)
annotation class MeshyOkHttpClient

@Qualifier
@Retention(RUNTIME)
annotation class MeshyRetrofit
