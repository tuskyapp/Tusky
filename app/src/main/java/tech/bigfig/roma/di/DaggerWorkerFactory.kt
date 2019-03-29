package tech.bigfig.roma.di

import android.content.Context
import androidx.work.*

/**
 * Created by pandasoft (joelpyska1@gmail.com) on 15/03/2019.
 */
class DaggerWorkerFactory : WorkerFactory() {

    override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters
    ): ListenableWorker? {

        val constructor = Class.forName(workerClassName)
                .asSubclass(RxWorker::class.java)
                .getDeclaredConstructor(Context::class.java, WorkerParameters::class.java)

        return constructor.newInstance(appContext, workerParameters)
                .apply { AndroidWorkerInjection.inject(this) }
    }
}