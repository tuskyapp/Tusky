package com.keylesspalace.tusky

import android.widget.EditText
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
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
        activity.accountManager = accountManagerMock
        application = activity.application as FakeTuskyApplication
        application.locator = serviceLocator
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
        activity.findViewById<EditText>(R.id.compose_edit_field).setText("Some text")
    }
}