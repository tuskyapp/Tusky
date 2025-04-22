package com.keylesspalace.tusky.settings

import androidx.preference.PreferenceDataStore
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.entity.AccountEntity
import com.keylesspalace.tusky.di.ApplicationScope
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AccountPreferenceDataStore @Inject constructor(
    private val accountManager: AccountManager,
    private val eventHub: EventHub,
    @ApplicationScope private val externalScope: CoroutineScope
) : PreferenceDataStore() {
    private val account: AccountEntity = accountManager.activeAccount!!

    override fun getBoolean(key: String, defValue: Boolean): Boolean = when (key) {
        PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA -> account.alwaysShowSensitiveMedia
        PrefKeys.ALWAYS_OPEN_SPOILER -> account.alwaysOpenSpoiler
        PrefKeys.MEDIA_PREVIEW_ENABLED -> account.mediaPreviewEnabled
        PrefKeys.TAB_FILTER_HOME_BOOSTS -> account.isShowHomeBoosts
        PrefKeys.TAB_FILTER_HOME_REPLIES -> account.isShowHomeReplies
        PrefKeys.TAB_SHOW_HOME_SELF_BOOSTS -> account.isShowHomeSelfBoosts
        else -> defValue
    }

    override fun putBoolean(key: String, value: Boolean) {
        externalScope.launch {
            accountManager.updateAccount(account) {
                when (key) {
                    PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA -> copy(alwaysShowSensitiveMedia = value)
                    PrefKeys.ALWAYS_OPEN_SPOILER -> copy(alwaysOpenSpoiler = value)
                    PrefKeys.MEDIA_PREVIEW_ENABLED -> copy(mediaPreviewEnabled = value)
                    PrefKeys.TAB_FILTER_HOME_BOOSTS -> copy(isShowHomeBoosts = value)
                    PrefKeys.TAB_FILTER_HOME_REPLIES -> copy(isShowHomeReplies = value)
                    PrefKeys.TAB_SHOW_HOME_SELF_BOOSTS -> copy(isShowHomeSelfBoosts = value)
                    else -> this
                }
            }
            eventHub.dispatch(PreferenceChangedEvent(key))
        }
    }
}
