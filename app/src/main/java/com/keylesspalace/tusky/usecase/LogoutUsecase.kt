package com.keylesspalace.tusky.usecase

import android.content.Context
import com.keylesspalace.tusky.components.drafts.DraftHelper
import com.keylesspalace.tusky.components.systemnotifications.NotificationService
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.DatabaseCleaner
import com.keylesspalace.tusky.db.entity.AccountEntity
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.ShareShortcutHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class LogoutUsecase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: MastodonApi,
    private val databaseCleaner: DatabaseCleaner,
    private val accountManager: AccountManager,
    private val draftHelper: DraftHelper,
    private val shareShortcutHelper: ShareShortcutHelper,
    private val notificationService: NotificationService,
) {

    /**
     * Logs the current account out and clears all caches associated with it
     * @return true if the user is logged in with other accounts, false if it was the only one
     */
    suspend fun logout(account: AccountEntity): Boolean {
        // invalidate the oauth token, if we have the client id & secret
        // (could be missing if user logged in with a previous version of Tusky)
        val clientId = account.clientId
        val clientSecret = account.clientSecret
        if (clientId != null && clientSecret != null) {
            api.revokeOAuthToken(
                clientId = clientId,
                clientSecret = clientSecret,
                token = account.accessToken
            )
        }

        // disable push notifications
        notificationService.disableUnifiedPushNotificationsForAccount(account)

        // disable pull notifications
        if (!notificationService.areNotificationsEnabled()) {
            // TODO this is working very wrong
            notificationService.disablePullNotifications()
        }

        // clear notification channels
        notificationService.deleteNotificationChannelsForAccount(account)

        // remove account from local AccountManager
        val otherAccountAvailable = accountManager.remove(account) != null

        // clear the database - this could trigger network calls so do it last when all tokens are gone
        databaseCleaner.cleanupEverything(account.id)
        draftHelper.deleteAllDraftsAndAttachmentsForAccount(account.id)

        // remove shortcut associated with the account
        shareShortcutHelper.removeShortcut(account)

        return otherAccountAvailable
    }
}
