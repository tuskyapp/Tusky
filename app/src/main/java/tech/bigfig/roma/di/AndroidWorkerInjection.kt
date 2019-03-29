package tech.bigfig.roma.di

import androidx.work.ListenableWorker
import androidx.work.RxWorker
import androidx.work.Worker

/**
 * Created by pandasoft (joelpyska1@gmail.com) on 15/03/2019.
 */
object AndroidWorkerInjection {

    fun inject(worker: RxWorker) {
        val application = worker.applicationContext
        if (application !is HasWorkerInjector) {
            throw RuntimeException(
                    "${application.javaClass.canonicalName} does not implement ${HasWorkerInjector::class.java.canonicalName}")
        }

        val workerInjector = (application as HasWorkerInjector).workerInjector()
        checkNotNull(workerInjector) { "${application.javaClass}.workerInjector() return null" }
        workerInjector.inject(worker)
    }
}