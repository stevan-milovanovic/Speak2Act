package rs.smobile.speak2act.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import rs.smobile.speak2act.core.config.DemoTalk

@Module
@InstallIn(SingletonComponent::class)
object DemoTalkModule {
    @Provides
    fun provideDemoTalk(): DemoTalk = DemoTalk.current
}
