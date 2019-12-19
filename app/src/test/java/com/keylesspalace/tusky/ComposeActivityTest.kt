/* Copyright 2018 charlag
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
 * see <http://www.gnu.org/licenses>. */


package com.keylesspalace.tusky

import android.text.SpannedString
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.keylesspalace.tusky.components.compose.ComposeActivity
import com.keylesspalace.tusky.components.compose.ComposeViewModel
import com.keylesspalace.tusky.components.compose.DEFAULT_CHARACTER_LIMIT
import com.keylesspalace.tusky.components.compose.MediaUploader
import com.keylesspalace.tusky.db.*
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Instance
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.service.ServiceClient
import com.keylesspalace.tusky.util.SaveTootHelper
import com.nhaarman.mockitokotlin2.any
import io.reactivex.Single
import io.reactivex.SingleObserver
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboMenuItem

/**
 * Created by charlag on 3/7/18.
 */

@Config(application = FakeTuskyApplication::class, sdk = [28])
@RunWith(AndroidJUnit4::class)
class ComposeActivityTest {
    private lateinit var activity: ComposeActivity
    private lateinit var accountManagerMock: AccountManager
    private lateinit var apiMock: MastodonApi

    private val instanceDomain = "example.domain"

    private val account = AccountEntity(
            id = 1,
            domain = instanceDomain,
            accessToken = "token",
            isActive = true,
            accountId = "1",
            username = "username",
            displayName = "Display Name",
            profilePictureUrl = "",
            notificationsEnabled = true,
            notificationsMentioned = true,
            notificationsFollowed = true,
            notificationsReblogged = true,
            notificationsFavorited = true,
            notificationSound = true,
            notificationVibration = true,
            notificationLight = true
    )
    var instanceResponseCallback: (()->Instance)? = null

    @Before
    fun setupActivity() {
        val controller = Robolectric.buildActivity(ComposeActivity::class.java)
        activity = controller.get()

        accountManagerMock = mock(AccountManager::class.java)
        `when`(accountManagerMock.activeAccount).thenReturn(account)

        apiMock = mock(MastodonApi::class.java)
        `when`(apiMock.getCustomEmojis()).thenReturn(Single.just(emptyList()))
        `when`(apiMock.getInstance()).thenReturn(object: Single<Instance>() {
            override fun subscribeActual(observer: SingleObserver<in Instance>) {
                val instance = instanceResponseCallback?.invoke()
                if (instance == null) {
                    observer.onError(Throwable())
                } else {
                    observer.onSuccess(instance)
                }
            }
        })

        val instanceDaoMock = mock(InstanceDao::class.java)
        `when`(instanceDaoMock.loadMetadataForInstance(any())).thenReturn(
                Single.just(InstanceEntity(instanceDomain, emptyList(),null, null, null, null))
        )

        val dbMock = mock(AppDatabase::class.java)
        `when`(dbMock.instanceDao()).thenReturn(instanceDaoMock)

        val viewModel = ComposeViewModel(
                apiMock,
                accountManagerMock,
                mock(MediaUploader::class.java),
                mock(ServiceClient::class.java),
                mock(SaveTootHelper::class.java),
                dbMock
        )

        val viewModelFactoryMock = mock(ViewModelFactory::class.java)
        `when`(viewModelFactoryMock.create(ComposeViewModel::class.java)).thenReturn(viewModel)

        activity.accountManager = accountManagerMock
        activity.viewModelFactory = viewModelFactoryMock

        controller.create().start()
    }

    @Test
    fun whenCloseButtonPressedAndEmpty_finish() {
        clickUp()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun whenCloseButtonPressedNotEmpty_notFinish() {
        insertSomeTextInContent()
        clickUp()
        assertFalse(activity.isFinishing)
        // We would like to check for dialog but Robolectric doesn't work with AppCompat v7 yet
    }

    @Test
    fun whenBackButtonPressedAndEmpty_finish() {
        clickBack()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun whenBackButtonPressedNotEmpty_notFinish() {
        insertSomeTextInContent()
        clickBack()
        assertFalse(activity.isFinishing)
        // We would like to check for dialog but Robolectric doesn't work with AppCompat v7 yet
    }

    @Test
    fun whenMaximumTootCharsIsNull_defaultLimitIsUsed() {
        instanceResponseCallback = { getInstanceWithMaximumTootCharacters(null) }
        setupActivity()
        assertEquals(DEFAULT_CHARACTER_LIMIT, activity.maximumTootCharacters)
    }

    @Test
    fun whenMaximumTootCharsIsPopulated_customLimitIsUsed() {
        val customMaximum = 1000
        instanceResponseCallback = { getInstanceWithMaximumTootCharacters(customMaximum) }
        setupActivity()
        assertEquals(customMaximum, activity.maximumTootCharacters)
    }

    @Test
    fun whenTextContainsNoUrl_everyCharacterIsCounted() {
        val content = "This is test content please ignore thx "
        insertSomeTextInContent(content)
        assertEquals(activity.calculateTextLength(), content.length)
    }

    @Test
    fun whenTextContainsUrl_onlyEllipsizedURLIsCounted() {
        val url = "https://www.google.dk/search?biw=1920&bih=990&tbm=isch&sa=1&ei=bmDrWuOoKMv6kwWOkIaoDQ&q=indiana+jones+i+hate+snakes+animated&oq=indiana+jones+i+hate+snakes+animated&gs_l=psy-ab.3...54174.55443.0.55553.9.7.0.0.0.0.255.333.1j0j1.2.0....0...1c.1.64.psy-ab..7.0.0....0.40G-kcDkC6A#imgdii=PSp15hQjN1JqvM:&imgrc=H0hyE2JW5wrpBM:"
        val additionalContent = "Check out this @image #search result: "
        insertSomeTextInContent(additionalContent + url)
        assertEquals(activity.calculateTextLength(), additionalContent.length + ComposeActivity.MAXIMUM_URL_LENGTH)
    }

    @Test
    fun whenTextContainsMultipleUrls_onlyEllipsizedURLIsCounted() {
        val shortUrl = "https://tusky.app"
        val url = "https://www.google.dk/search?biw=1920&bih=990&tbm=isch&sa=1&ei=bmDrWuOoKMv6kwWOkIaoDQ&q=indiana+jones+i+hate+snakes+animated&oq=indiana+jones+i+hate+snakes+animated&gs_l=psy-ab.3...54174.55443.0.55553.9.7.0.0.0.0.255.333.1j0j1.2.0....0...1c.1.64.psy-ab..7.0.0....0.40G-kcDkC6A#imgdii=PSp15hQjN1JqvM:&imgrc=H0hyE2JW5wrpBM:"
        val additionalContent = " Check out this @image #search result: "
        insertSomeTextInContent(shortUrl + additionalContent + url)
        assertEquals(activity.calculateTextLength(), additionalContent.length + shortUrl.length + ComposeActivity.MAXIMUM_URL_LENGTH)
    }

    @Test
    fun whenTextContainsMultipleURLs_allURLsGetEllipsized() {
        val url = "https://www.google.dk/search?biw=1920&bih=990&tbm=isch&sa=1&ei=bmDrWuOoKMv6kwWOkIaoDQ&q=indiana+jones+i+hate+snakes+animated&oq=indiana+jones+i+hate+snakes+animated&gs_l=psy-ab.3...54174.55443.0.55553.9.7.0.0.0.0.255.333.1j0j1.2.0....0...1c.1.64.psy-ab..7.0.0....0.40G-kcDkC6A#imgdii=PSp15hQjN1JqvM:&imgrc=H0hyE2JW5wrpBM:"
        val additionalContent = " Check out this @image #search result: "
        insertSomeTextInContent(url + additionalContent + url)
        assertEquals(activity.calculateTextLength(), additionalContent.length + (ComposeActivity.MAXIMUM_URL_LENGTH * 2))
    }

    private fun clickUp() {
        val menuItem = RoboMenuItem(android.R.id.home)
        activity.onOptionsItemSelected(menuItem)
    }

    private fun clickBack() {
        activity.onBackPressed()
    }

    private fun insertSomeTextInContent(text: String? = null) {
        activity.findViewById<EditText>(R.id.composeEditField).setText(text ?: "Some text")
    }

    private fun getInstanceWithMaximumTootCharacters(maximumTootCharacters: Int?): Instance
    {
        return Instance(
                "https://example.token",
                "Example dot Token",
                "Example instance for testing",
                "admin@example.token",
                "2.6.3",
                HashMap(),
                null,
                null,
                listOf("en"),
                Account(
                        "1",
                        "admin",
                        "admin",
                        "admin",
                        SpannedString(""),
                        "https://example.token",
                        "",
                        "",
                        false,
                        0,
                        0,
                        0,
                        null,
                        false,
                        emptyList(),
                        emptyList()
                ),
                maximumTootCharacters,
                null,
                null
        )
    }

}

