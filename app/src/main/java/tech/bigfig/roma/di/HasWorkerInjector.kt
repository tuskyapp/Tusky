package tech.bigfig.roma.di

import androidx.work.ListenableWorker
import androidx.work.RxWorker
import androidx.work.Worker
import dagger.android.AndroidInjector

/**
 * Created by pandasoft (joelpyska1@gmail.com) on 15/03/2019.
 */
interface HasWorkerInjector {

    fun workerInjector(): AndroidInjector<RxWorker>
}