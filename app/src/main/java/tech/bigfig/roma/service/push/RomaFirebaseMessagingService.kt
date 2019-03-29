package tech.bigfig.roma.service.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.android.AndroidInjection
import tech.bigfig.roma.db.AccountEntity
import tech.bigfig.roma.db.AccountManager
import tech.bigfig.roma.di.Injectable
import tech.bigfig.roma.entity.Notification
import tech.bigfig.roma.util.NotificationHelper
import tech.bigfig.roma.util.isLessThan
import javax.inject.Inject

class RomaFirebaseMessagingService : FirebaseMessagingService(), Injectable {
    private val TAG = RomaFirebaseMessagingService::class.java.simpleName

    @Inject
    lateinit var accountManager: AccountManager

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun onNewToken(token: String?) {
        Log.d(TAG,"New token: $token")
        token?.let {
            UpdateFcmTokenWorker.updateTokens(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage?) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage?.data
        data?.let {
            val accountUsername = data["account"]
            val accountDomain = data["server"]
            val notificationTypeString = data["notification_type"]
            val notificationId = data["notification_id"]
            val title = data["title"]
            val body = data["body"]
            val avatar = data["icon"]
            if (notificationId.isNullOrBlank() || accountUsername.isNullOrBlank() || accountDomain.isNullOrBlank() || notificationTypeString.isNullOrBlank())
                return
            var accountEntity: AccountEntity?=null
            accountManager.getAllAccountsOrderedByActive().forEach { account->
                if (account.username == accountUsername && account.domain == accountDomain){
                    accountEntity = account
                }
            }

            val notificationType = Notification.Type.byString(notificationTypeString)
            if (accountEntity!=null && notificationType!=Notification.Type.UNKNOWN) {

                val newId = accountEntity!!.lastNotificationId
                var newestId = ""

                if (newestId.isLessThan(notificationId)) {
                    newestId = notificationId
                }
                if (newId.isLessThan(notificationId)) {
                    NotificationHelper.make(this,notificationId, notificationType,avatar,title,body, accountEntity!!)
                }

                accountEntity!!.lastNotificationId = newestId
                accountManager.saveAccount(accountEntity!!)
            }

        }
    }
}
