package rs.smobile.speak2act.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import rs.smobile.speak2act.core.config.DefaultRoutingConfig
import rs.smobile.speak2act.core.config.RoutingConfig
import rs.smobile.speak2act.core.logging.AndroidRouteLogger
import rs.smobile.speak2act.core.logging.RouteLogger
import rs.smobile.speak2act.core.network.ConnectivityNetworkMonitor
import rs.smobile.speak2act.core.network.NetworkMonitor
import rs.smobile.speak2act.core.privacy.CloudConsentStore
import rs.smobile.speak2act.core.privacy.SharedPreferencesCloudConsentStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformModule {
    @Binds
    @Singleton
    abstract fun bindRoutingConfig(impl: DefaultRoutingConfig): RoutingConfig

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(impl: ConnectivityNetworkMonitor): NetworkMonitor

    @Binds
    @Singleton
    abstract fun bindCloudConsentStore(impl: SharedPreferencesCloudConsentStore): CloudConsentStore

    @Binds
    abstract fun bindRouteLogger(impl: AndroidRouteLogger): RouteLogger
}
