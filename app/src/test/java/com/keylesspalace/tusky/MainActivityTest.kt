package com.keylesspalace.tusky

import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import androidx.work.testing.WorkManagerTestInitHelper
import at.connyduck.calladapter.networkresult.NetworkResult
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.components.accountlist.AccountListActivity
import com.keylesspalace.tusky.components.systemnotifications.NotificationService
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.entity.AccountEntity
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.getSerializableExtraCompat
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.util.concurrent.BackgroundExecutor.runInBackground
import org.robolectric.annotation.Config

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val account = Account(
        id = "1",
        localUsername = "",
        username = "",
        displayName = "",
        createdAt = Date(),
        note = "",
        url = "",
        avatar = "",
        header = ""
    )
    private val accountEntity = AccountEntity(
        id = 1,
        domain = "test.domain",
        accessToken = "fakeToken",
        clientId = "fakeId",
        clientSecret = "fakeSecret",
        isActive = true
    )

    @Before
    fun setup() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @After
    fun teardown() {
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun `clicking notification of type FOLLOW shows notification tab`() {
        val intent = showNotification(Notification.Type.Follow)

        val activity = startMainActivity(intent)
        val currentTab = activity.findViewById<ViewPager2>(R.id.viewPager).currentItem

        val notificationTab = defaultTabs().indexOfFirst { it.id == NOTIFICATIONS }

        assertEquals(currentTab, notificationTab)
    }

    @Test
    fun `clicking notification of type FOLLOW_REQUEST shows follow requests`() {
        val intent = showNotification(Notification.Type.FollowRequest)

        val activity = startMainActivity(intent)
        val nextActivity = shadowOf(activity).peekNextStartedActivity()

        assertNotNull(nextActivity)
        assertEquals(ComponentName(context, AccountListActivity::class.java.name), nextActivity.component)
        assertEquals(AccountListActivity.Type.FOLLOW_REQUESTS, nextActivity.getSerializableExtraCompat("type"))
    }

    private fun showNotification(type: Notification.Type): Intent {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val shadowNotificationManager = shadowOf(notificationManager)

        val notificationService = NotificationService(
            notificationManager,
            mock {
                on { areNotificationsEnabled() } doReturn true
            },
            mock(),
            mock(),
            context,
            mock(),
        )

        notificationService.createNotificationChannelsForAccount(accountEntity)

        runInBackground {
            val notification = notificationService.createBaseNotification(
                Notification(
                    type = type,
                    id = "id",
                    account = TimelineAccount(
                        id = "1",
                        localUsername = "connyduck",
                        username = "connyduck@mastodon.example",
                        displayName = "Conny Duck",
                        note = "This is their bio",
                        url = "https://mastodon.example/@ConnyDuck",
                        avatar = "https://mastodon.example/system/accounts/avatars/000/150/486/original/ab27d7ddd18a10ea.jpg"
                    ),
                    status = null,
                    report = null
                ),
                accountEntity
            )
            notificationManager.notify("id", 1, notification)
        }

        val notification = shadowNotificationManager.allNotifications.first()
        return shadowOf(notification.contentIntent).savedIntent
    }

    private fun startMainActivity(intent: Intent): Activity {
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent)
        val activity = controller.get()
        val eventHub = EventHub()
        activity.eventHub = eventHub
        val accountManager: AccountManager = mock {
            on { accounts } doReturn listOf(accountEntity)
            on { accountsFlow } doReturn MutableStateFlow(listOf(accountEntity))
            on { activeAccount } doReturn accountEntity
        }
        activity.accountManager = accountManager
        activity.draftsAlert = mock { }
        val api: MastodonApi = mock {
            onBlocking { accountVerifyCredentials() } doReturn NetworkResult.success(account)
            onBlocking { announcements() } doReturn NetworkResult.success(emptyList())
        }
        activity.mastodonApi = api
        activity.preferences = mock(defaultAnswer = {
            when (it.method.returnType) {
                String::class.java -> "test"
                Boolean::class.java -> false
                else -> null
            }
        })
        val viewModel = MainViewModel(
            api = api,
            eventHub = eventHub,
            accountManager = accountManager,
            shareShortcutHelper = mock(),
            notificationService = mock(),
        )
        val testViewModelFactory = viewModelFactory {
            initializer { viewModel }
        }
        activity.viewModelProviderFactory = testViewModelFactory

        controller.create().start()
        return activity
    }
}
