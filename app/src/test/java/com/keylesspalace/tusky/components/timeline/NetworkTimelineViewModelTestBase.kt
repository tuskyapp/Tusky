/*
 * Copyright 2023 Tusky Contributors
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.components.timeline

import android.content.SharedPreferences
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.components.timeline.viewmodel.NetworkTimelineViewModel
import com.keylesspalace.tusky.components.timeline.viewmodel.TimelineViewModel
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.network.FilterModel
import com.keylesspalace.tusky.settings.AccountPreferenceDataStore
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.usecase.TimelineCases
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
abstract class NetworkTimelineViewModelTestBase {
    protected lateinit var networkTimelineRepository: NetworkTimelineRepository
    protected lateinit var sharedPreferencesMap: MutableMap<String, Boolean>
    protected lateinit var sharedPreferences: SharedPreferences
    protected lateinit var accountPreferencesMap: MutableMap<String, Boolean>
    protected lateinit var accountPreferenceDataStore: AccountPreferenceDataStore
    protected lateinit var accountManager: AccountManager
    protected lateinit var timelineCases: TimelineCases
    protected lateinit var eventHub: EventHub
    protected lateinit var filtersRepository: FiltersRepository
    protected lateinit var filterModel: FilterModel
    protected lateinit var viewModel: TimelineViewModel

    /** Empty success response, for API calls that return one */
    protected var emptySuccess = Response.success("".toResponseBody())

    /** Empty error response, for API calls that return one */
    protected var emptyError: Response<ResponseBody> = Response.error(404, "".toResponseBody())

    /** Exception to throw when testing errors */
    protected val httpException = HttpException(emptyError)

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    @Before
    fun setup() {
        shadowOf(Looper.getMainLooper()).idle()

        networkTimelineRepository = mock()

        // Backing store for sharedPreferences, to allow mutation in tests
        sharedPreferencesMap = mutableMapOf(
            PrefKeys.ANIMATE_GIF_AVATARS to false,
            PrefKeys.ANIMATE_CUSTOM_EMOJIS to false,
            PrefKeys.ABSOLUTE_TIME_VIEW to false,
            PrefKeys.SHOW_BOT_OVERLAY to true,
            PrefKeys.USE_BLURHASH to true,
            PrefKeys.CONFIRM_REBLOGS to true,
            PrefKeys.CONFIRM_FAVOURITES to false,
            PrefKeys.WELLBEING_HIDE_STATS_POSTS to false,
            PrefKeys.SHOW_NOTIFICATIONS_FILTER to true,
            PrefKeys.FAB_HIDE to false
        )

        // Any getBoolean() call looks for the result in sharedPreferencesMap
        sharedPreferences = mock {
            on { getBoolean(any(), any()) } doAnswer { sharedPreferencesMap[it.arguments[0]] }
        }

        // Backing store for account preferences, to allow mutation in tests
        accountPreferencesMap = mutableMapOf(
            PrefKeys.ALWAYS_SHOW_SENSITIVE_MEDIA to false,
            PrefKeys.ALWAYS_OPEN_SPOILER to false,
            PrefKeys.MEDIA_PREVIEW_ENABLED to true
        )

        // Any getBoolean() call looks for the result in accountPreferencesMap.
        // Any putBoolean() call updates the map and dispatches an event
        accountPreferenceDataStore = mock {
            on { getBoolean(any(), any()) } doAnswer { accountPreferencesMap[it.arguments[0]] }
            on { putBoolean(anyString(), anyBoolean()) } doAnswer {
                accountPreferencesMap[it.arguments[0] as String] = it.arguments[1] as Boolean
                runBlocking { eventHub.dispatch(PreferenceChangedEvent(it.arguments[0] as String)) }
            }
        }

        accountManager = mock {
            on { activeAccount } doReturn AccountEntity(
                id = 1,
                domain = "mastodon.test",
                accessToken = "fakeToken",
                clientId = "fakeId",
                clientSecret = "fakeSecret",
                isActive = true,
                lastVisibleHomeTimelineStatusId = null,
                notificationsFilter = "['follow']",
                mediaPreviewEnabled = true,
                alwaysShowSensitiveMedia = true,
                alwaysOpenSpoiler = true
            )
        }
        eventHub = EventHub()
        timelineCases = mock()
        filtersRepository = mock()
        filterModel = mock()

        viewModel = NetworkTimelineViewModel(
            networkTimelineRepository,
            timelineCases,
            eventHub,
            filtersRepository,
            accountManager,
            sharedPreferences,
            accountPreferenceDataStore,
            filterModel
        )
        // Initialisation with any timeline kind, as long as it's not Home
        // (Home uses CachedTimelineViewModel)
        viewModel.init(TimelineKind.Bookmarks)
    }
}
