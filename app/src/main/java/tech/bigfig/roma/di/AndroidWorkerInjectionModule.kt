package tech.bigfig.roma.di

import androidx.work.ListenableWorker
import androidx.work.RxWorker
import androidx.work.Worker
import dagger.Module
import dagger.android.AndroidInjector
import dagger.multibindings.Multibinds

/**
 * Created by pandasoft (joelpyska1@gmail.com) on 15/03/2019.
 */
@Module
abstract class AndroidWorkerInjectionModule {

    @Multibinds
    abstract fun workerInjectorFactories(): Map<Class<out RxWorker>, AndroidInjector.Factory<out RxWorker>>
}