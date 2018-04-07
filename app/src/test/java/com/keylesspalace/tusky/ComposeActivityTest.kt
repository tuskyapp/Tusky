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

import android.widget.EditText
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.network.MastodonApi
import okhttp3.Request
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboMenuItem
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Created by charlag on 3/7/18.
 */

@Config(application = FakeTuskyApplication::class)
@RunWith(RobolectricTestRunner::class)
class ComposeActivityTest {

    lateinit var activity: ComposeActivity
    lateinit var application: FakeTuskyApplication
    lateinit var serviceLocator: TuskyApplication.ServiceLocator
    lateinit var accountManagerMock: AccountManager
    lateinit var apiMock: MastodonApi

    val account = AccountEntity(
            id = 1,
            domain = "example.token",
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

    @Before
    fun before() {
        val controller = Robolectric.buildActivity(ComposeActivity::class.java)
        activity = controller.get()

        accountManagerMock = Mockito.mock(AccountManager::class.java)
        serviceLocator = Mockito.mock(TuskyApplication.ServiceLocator::class.java)
        `when`(serviceLocator.get(AccountManager::class.java)).thenReturn(accountManagerMock)
        `when`(accountManagerMock.activeAccount).thenReturn(account)

        apiMock = Mockito.mock(MastodonApi::class.java)
        `when`(apiMock.customEmojis).thenReturn(object: Call<List<Emoji>> {
            override fun isExecuted(): Boolean {
                return false
            }
            override fun clone(): Call<List<Emoji>> {
                throw Error("not implemented")
            }
            override fun isCanceled(): Boolean {
                throw Error("not implemented")
            }
            override fun cancel() {
                throw Error("not implemented")
            }
            override fun execute(): Response<List<Emoji>> {
                throw Error("not implemented")
            }
            override fun request(): Request {
                throw Error("not implemented")
            }

            override fun enqueue(callback: Callback<List<Emoji>>?) {}
        })

        activity.mastodonApi = apiMock
        activity.accountManager = accountManagerMock
        application = activity.application as FakeTuskyApplication
        application.locator = serviceLocator

        `when`(accountManagerMock.activeAccount).thenReturn(account)


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

    private fun clickUp() {
        val menuItem = RoboMenuItem(android.R.id.home)
        activity.onOptionsItemSelected(menuItem)
    }

    private fun clickBack() {
        activity.onBackPressed()
    }

    private fun insertSomeTextInContent() {
        activity.findViewById<EditText>(R.id.composeEditField).setText("Some text")
    }
}