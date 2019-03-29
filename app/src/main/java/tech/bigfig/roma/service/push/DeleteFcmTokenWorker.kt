package tech.bigfig.roma.service.push

import android.content.Context
import android.util.Log
import androidx.work.*
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import tech.bigfig.roma.db.AccountManager
import tech.bigfig.roma.network.MastodonApi
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject


/**
 * Created by pandasoft (joelpyska1@gmail.com) on 15/03/2019.
 */
class DeleteFcmTokenWorker(context:Context, workerParameters: WorkerParameters): RxWorker(context,workerParameters) {

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var mastodonApi: MastodonApi


    override fun createWork(): Single<Result> {
       return Single.create{ emitter->
           val auth = inputData.getString(KEY_AUTH)
           val domain = inputData.getString(KEY_DOMAIN)
           if (auth!=null && domain!=null) {
               try {
                   val subscription = mastodonApi.unsubscribePush("Bearer $auth", domain).execute()
                   if (subscription.isSuccessful) {
                       Log.i(TAG, "Token removed success")
                       emitter.onSuccess(Result.success())
                   } else {
                       Log.w(TAG, "Token remove failed: ${subscription.message()}")
                       emitter.onSuccess(Result.failure())
                   }
               } catch (e: IOException) {
                   Log.w(TAG, "Token update failed", e)
                   emitter.onSuccess(Result.retry())
               }
           }
           else{
               emitter.onSuccess(Result.failure())
           }
       }
    }


    override fun getBackgroundScheduler(): Scheduler {
        return Schedulers.io()
    }
    companion object {
        private const val NAME_DELETE_TOKENS = "delete.tokens"
        private const val KEY_AUTH = "auth"
        private const val KEY_DOMAIN = "domain"

        private  val TAG = DeleteFcmTokenWorker::class.java.simpleName
        fun removeTokens(auth:String,domain:String){
            val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            val data:Data = Data.Builder()
                    .putString(KEY_AUTH,auth)
                    .putString(KEY_DOMAIN,domain)
                    .build()
            val request = OneTimeWorkRequest.Builder(DeleteFcmTokenWorker::class.java)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, OneTimeWorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                    .setConstraints(constraints)
                    .setInputData(data)
                    .build()

            WorkManager.getInstance().enqueueUniqueWork(NAME_DELETE_TOKENS, ExistingWorkPolicy.APPEND, request)

        }
    }
}