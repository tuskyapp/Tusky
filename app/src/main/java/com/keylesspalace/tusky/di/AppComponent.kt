package com.keylesspalace.tusky.di

import android.app.Application
import com.keylesspalace.tusky.TuskyApplication
import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule
import javax.inject.Singleton


/**
 * Created by charlag on 3/21/18.
 */

@Singleton
@Component(modules = [
    AppModule::class,
    NetworkModule::class,
    AndroidInjectionModule::class,
    ActivitiesModule::class
])
interface AppComponent {
    @Component.Builder
    interface Builder {
        @BindsInstance
        fun application(application: Application): Builder

        fun build(): AppComponent
    }

    fun inject(app: TuskyApplication)
}