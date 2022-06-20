package com.keylesspalace.tusky.usecase

import android.content.Context
import com.keylesspalace.tusky.components.drafts.DraftHelper
import com.keylesspalace.tusky.components.notifications.NotificationHelper
import com.keylesspalace.tusky.components.notifications.disableUnifiedPushNotificationsForAccount
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.removeShortcut
import javax.inject.Inject

class LogoutUsecase @Inject constructor(
    private val context: Context,
    private val api: MastodonApi,
    private val db: AppDatabase,
    private val accountManager: AccountManager,
    private val draftHelper: DraftHelper
) {

    /**
     * Logs the current account out and clears all caches associated with it
     * @return true if the user is logged in with other accounts, false if it was the only one
     */
    suspend fun logout(): Boolean {
        accountManager.activeAccount?.let { activeAccount ->

            // invalidate the oauth token, if we have the client id & secret
            // (could be missing if user logged in with a previous version of Tusky)
            val clientId = activeAccount.clientId
            val clientSecret = activeAccount.clientSecret
            if (clientId != null && clientSecret != null) {
                api.revokeOAuthToken(
                    clientId = clientId,
                    clientSecret = clientSecret,
                    token = activeAccount.accessToken
                )
            }

            // disable push notifications
            disableUnifiedPushNotificationsForAccount(context, activeAccount)

            // disable pull notifications
            if (!NotificationHelper.areNotificationsEnabled(context, accountManager)) {
                NotificationHelper.disablePullNotifications(context)
            }

            // clear notification channels
            NotificationHelper.deleteNotificationChannelsForAccount(activeAccount, context)

            // remove account from local AccountManager
            val otherAccountAvailable = accountManager.logActiveAccountOut() != null

            // clear the database - this could trigger network calls so do it last when all tokens are gone
            db.timelineDao().removeAll(activeAccount.id)
            db.conversationDao().deleteForAccount(activeAccount.id)
            draftHelper.deleteAllDraftsAndAttachmentsForAccount(activeAccount.id)

            // remove shortcut associated with the account
            removeShortcut(context, activeAccount)

            return otherAccountAvailable
        }
        return false
    }
}
