package com.keylesspalace.tusky.components.compose

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.db.dao.AccountDao
import com.keylesspalace.tusky.db.entity.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.network.MastodonApi
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.annotation.Config

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
class ComposeViewModelTest {

    private lateinit var api: MastodonApi
    private lateinit var accountDao: AccountDao
    private lateinit var accountManager: AccountManager
    private lateinit var eventHub: EventHub
    private lateinit var viewModel: ComposeViewModel

    @Before
    fun setup() {
        api = mock()
        accountDao = mock()
        accountManager = AccountManager(accountDao)
        eventHub = EventHub()
        accountManager.activeAccount = AccountEntity(
            id = 1,
            domain = "test.domain",
            accessToken = "fakeToken",
            clientId = "fakeId",
            clientSecret = "fakeSecret",
            isActive = true
        )
        viewModel = ComposeViewModel(
            api = api,
            accountManager = accountManager,
            mediaUploader = mock(),
            serviceClient = mock(),
            draftHelper = mock(),
            instanceInfoRepo = mock(),
        )
    }

    @Test
    fun `startingVisibility initially set to defaultPostPrivacy for post`() {
        viewModel.setup(null)

        assertEquals(Status.Visibility.PUBLIC, viewModel.statusVisibility.value)
    }

    @Test
    fun `startingVisibility initially set to replyPostPrivacy for reply`() {
        viewModel.setup(ComposeActivity.ComposeOptions(inReplyToId = "123"))

        assertEquals(Status.Visibility.UNLISTED, viewModel.statusVisibility.value)
    }
}
