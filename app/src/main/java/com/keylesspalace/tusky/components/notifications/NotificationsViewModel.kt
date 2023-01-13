package com.keylesspalace.tusky.components.notifications

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.keylesspalace.tusky.components.timeline.util.ifExpected
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.usecase.TimelineCases
import com.keylesspalace.tusky.util.CardViewMode
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.toViewData
import com.keylesspalace.tusky.viewdata.NotificationViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import javax.inject.Inject

data class NotificationsUiState(
    // Dummy, just to have something to represent in the state
    val foo: Int
)

// TODO: The status functions this exposes (reblog, favourite, bookmark, etc) are very similar
// to those in TimelineViewModel. Investigate how to de-duplicate them where practical.

// TODO: Maybe view models should implement StatusActionListener? If the interface's methods
// included the status data as well as the position there's no need to go through the hosting
// fragment or activity to get the data. That would simplify the code slightly and save a function
// call.

class NotificationsViewModel @Inject constructor(
    private val mastodonApi: MastodonApi,
    private val preferences: SharedPreferences,
    private val accountManager: AccountManager,
    private val timelineCases: TimelineCases
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState(foo = 1))
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    val flow: Flow<PagingData<NotificationViewData.Concrete>>

    val statusDisplayOptions: StatusDisplayOptions

    init {
        statusDisplayOptions = StatusDisplayOptions(
            animateAvatars = preferences.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false),
            mediaPreviewEnabled = accountManager.activeAccount!!.mediaPreviewEnabled,
            useAbsoluteTime = preferences.getBoolean(PrefKeys.ABSOLUTE_TIME_VIEW, false),
            showBotOverlay = preferences.getBoolean(PrefKeys.SHOW_BOT_OVERLAY, true),
            useBlurhash = preferences.getBoolean(PrefKeys.USE_BLURHASH, true),
            cardViewMode = CardViewMode.NONE,
            confirmReblogs = preferences.getBoolean(PrefKeys.CONFIRM_REBLOGS, true),
            confirmFavourites = preferences.getBoolean(PrefKeys.CONFIRM_FAVOURITES, false),
            hideStats = preferences.getBoolean(PrefKeys.WELLBEING_HIDE_STATS_POSTS, false),
            animateEmojis = preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false),
            showSensitiveMedia = accountManager.activeAccount!!.alwaysShowSensitiveMedia,
            openSpoiler = accountManager.activeAccount!!.alwaysOpenSpoiler
        )

        flow = Pager(
            config = PagingConfig(pageSize = 30),
            pagingSourceFactory = {
                NotificationsPagingSource(mastodonApi)
            }
        )
            .flow
            .map { pagingData ->
                pagingData.map { notification ->
                    notification.toViewData(
                        isShowingContent = statusDisplayOptions.showSensitiveMedia ||
                            !(notification.status?.actionableStatus?.sensitive ?: false),
                        isExpanded = statusDisplayOptions.openSpoiler,
                        isCollapsed = true
                    )
                }
            }.cachedIn(viewModelScope)
    }

    // TODO: Copied from TimelineViewModel
    fun reblog(
        reblog: Boolean,
        statusViewData: StatusViewData.Concrete
    ): Job = viewModelScope.launch {
        try {
            timelineCases.reblog(statusViewData.actionableId, reblog).await()
        } catch (t: Exception) {
            ifExpected(t) {
                Log.d(TAG, "Failed to reblog status " + statusViewData.actionableId, t)
            }
        }
    }

    // TODO: Copied from TimelineViewModel
    fun favorite(
        favorite: Boolean,
        statusViewData: StatusViewData.Concrete
    ): Job = viewModelScope.launch {
        try {
            timelineCases.favourite(statusViewData.actionableId, favorite).await()
        } catch (t: Exception) {
            ifExpected(t) {
                Log.d(TAG, "Failed to favourite status " + statusViewData.actionableId, t)
            }
        }
    }

    // TODO: Copied from TimelineViewModel
    fun bookmark(
        bookmark: Boolean,
        statusViewData: StatusViewData.Concrete
    ): Job = viewModelScope.launch {
        try {
            timelineCases.bookmark(statusViewData.actionableId, bookmark).await()
        } catch (t: Exception) {
            ifExpected(t) {
                Log.d(TAG, "Failed to bookmark status " + statusViewData.actionableId, t)
            }
        }
    }

    companion object {
        private const val TAG = "NotificationsViewModel"
    }
}
