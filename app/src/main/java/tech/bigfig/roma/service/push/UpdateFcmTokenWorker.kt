package tech.bigfig.roma.service.push

import android.content.Context
import android.util.Log
import androidx.work.*
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import tech.bigfig.roma.db.AccountManager
import tech.bigfig.roma.network.MastodonApi
import tech.bigfig.roma.util.getPushSubscriptionBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject


/**
 * Created by pandasoft (joelpyska1@gmail.com) on 15/03/2019.
 */
class UpdateFcmTokenWorker(context:Context, workerParameters: WorkerParameters): RxWorker(context,workerParameters) {

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var mastodonApi: MastodonApi


    override fun createWork(): Single<Result> {
       return Single.create{ emitter->
           val token = inputData.getString(KEY_TOKEN)
           val accountForUpdate = inputData.getString(KEY_ACCOUNT)
           if (token!=null){
               var retResult = Result.success()
               accountManager.getAllAccountsOrderedByActive().forEach { account ->
                   if (accountForUpdate == null || accountForUpdate == account.username) {
                       try {
                           Log.d(TAG, "update token ${account.fullName}")
                           val subscription = mastodonApi.subscribePush(
                                   "Bearer ${account.accessToken}",
                                   account.domain, getPushSubscriptionBody(token,account))
                                   .execute()
                           if (subscription.isSuccessful) {
                               Log.i(TAG, "Token updated success for ${account.fullName}")
                           } else {
                               Log.w(TAG, "Token update failed: ${subscription.message()}")
                           }
                       } catch (e: IOException) {
                           retResult = Result.retry()
                           Log.w(TAG, "Token update failed", e)
                       }
                   }
               }
               emitter.onSuccess(retResult)
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
        private const val NAME_UPDATE_TOKENS = "update.tokens"
        private const val KEY_TOKEN = "token"
        private const val KEY_ACCOUNT = "account"
        private  val TAG = UpdateFcmTokenWorker::class.java.simpleName
        fun updateTokens(token:String,account:String?=null){
            val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            val data:Data = Data.Builder()
                    .putString(KEY_TOKEN,token)
                    .putString(KEY_ACCOUNT,account)
                    .build()
            val request = OneTimeWorkRequest.Builder(UpdateFcmTokenWorker::class.java)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, OneTimeWorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                    .setConstraints(constraints)
                    .setInputData(data)
                    .build()

            WorkManager.getInstance().enqueueUniqueWork(NAME_UPDATE_TOKENS, ExistingWorkPolicy.KEEP, request)

        }
    }
}