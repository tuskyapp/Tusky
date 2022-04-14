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

import android.content.Intent
import android.os.Looper.getMainLooper
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.keylesspalace.tusky.components.compose.ComposeActivity
import com.keylesspalace.tusky.components.compose.ComposeViewModel
import com.keylesspalace.tusky.components.compose.DEFAULT_CHARACTER_LIMIT
import com.keylesspalace.tusky.components.compose.DEFAULT_MAXIMUM_URL_LENGTH
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.InstanceDao
import com.keylesspalace.tusky.db.InstanceEntity
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Instance
import com.keylesspalace.tusky.entity.InstanceConfiguration
import com.keylesspalace.tusky.entity.StatusConfiguration
import com.keylesspalace.tusky.network.MastodonApi
import io.reactivex.rxjava3.core.Single
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboMenuItem

/**
 * Created by charlag on 3/7/18.
 */

@Config(sdk = [28])
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
        notificationsFollowRequested = false,
        notificationsReblogged = true,
        notificationsFavorited = true,
        notificationSound = true,
        notificationVibration = true,
        notificationLight = true
    )
    private var instanceResponseCallback: (() -> Instance)? = null
    private var composeOptions: ComposeActivity.ComposeOptions? = null

    @Before
    fun setupActivity() {
        val controller = Robolectric.buildActivity(ComposeActivity::class.java)
        activity = controller.get()

        accountManagerMock = mock {
            on { activeAccount } doReturn account
        }

        apiMock = mock {
            on { getCustomEmojis() } doReturn Single.just(emptyList())
            onBlocking { getInstance() } doReturn instanceResponseCallback?.invoke().let { instance ->
                if (instance == null) {
                    Result.failure(Throwable())
                } else {
                    Result.success(instance)
                }
            }
        }

        val instanceDaoMock: InstanceDao = mock {
            on { loadMetadataForInstance(any()) } doReturn
                Single.just(InstanceEntity(instanceDomain, emptyList(), null, null, null, null, null, null, null))
            on { loadMetadataForInstance(any()) } doReturn
                Single.just(InstanceEntity(instanceDomain, emptyList(), null, null, null, null, null, null, null))
        }

        val dbMock: AppDatabase = mock {
            on { instanceDao() } doReturn instanceDaoMock
        }

        val viewModel = ComposeViewModel(
            apiMock,
            accountManagerMock,
            mock(),
            mock(),
            mock(),
            dbMock
        )
        activity.intent = Intent(activity, ComposeActivity::class.java).apply {
            putExtra(ComposeActivity.COMPOSE_OPTIONS_EXTRA, composeOptions)
        }

        val viewModelFactoryMock: ViewModelFactory = mock {
            on { create(ComposeViewModel::class.java) } doReturn viewModel
        }

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
    fun whenModifiedInitialState_andCloseButtonPressed_notFinish() {
        composeOptions = ComposeActivity.ComposeOptions(modifiedInitialState = true)
        setupActivity()
        clickUp()
        assertFalse(activity.isFinishing)
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
    fun whenModifiedInitialState_andBackButtonPressed_notFinish() {
        composeOptions = ComposeActivity.ComposeOptions(modifiedInitialState = true)
        setupActivity()
        clickBack()
        assertFalse(activity.isFinishing)
    }

    @Test
    fun whenMaximumTootCharsIsNull_defaultLimitIsUsed() {
        instanceResponseCallback = { getInstanceWithCustomConfiguration(null) }
        setupActivity()
        assertEquals(DEFAULT_CHARACTER_LIMIT, activity.maximumTootCharacters)
    }

    @Test
    fun whenMaximumTootCharsIsPopulated_customLimitIsUsed() {
        val customMaximum = 1000
        instanceResponseCallback = { getInstanceWithCustomConfiguration(customMaximum, getCustomInstanceConfiguration(maximumStatusCharacters = customMaximum)) }
        setupActivity()
        shadowOf(getMainLooper()).idle()
        assertEquals(customMaximum, activity.maximumTootCharacters)
    }

    @Test
    fun whenOnlyLegacyMaximumTootCharsIsPopulated_customLimitIsUsed() {
        val customMaximum = 1000
        instanceResponseCallback = { getInstanceWithCustomConfiguration(customMaximum) }
        setupActivity()
        shadowOf(getMainLooper()).idle()
        assertEquals(customMaximum, activity.maximumTootCharacters)
    }

    @Test
    fun whenOnlyConfigurationMaximumTootCharsIsPopulated_customLimitIsUsed() {
        val customMaximum = 1000
        instanceResponseCallback = { getInstanceWithCustomConfiguration(null, getCustomInstanceConfiguration(maximumStatusCharacters = customMaximum)) }
        setupActivity()
        shadowOf(getMainLooper()).idle()
        assertEquals(customMaximum, activity.maximumTootCharacters)
    }

    @Test
    fun whenDifferentCharLimitsArePopulated_statusConfigurationLimitIsUsed() {
        val customMaximum = 1000
        instanceResponseCallback = { getInstanceWithCustomConfiguration(customMaximum, getCustomInstanceConfiguration(maximumStatusCharacters = customMaximum * 2)) }
        setupActivity()
        shadowOf(getMainLooper()).idle()
        assertEquals(customMaximum * 2, activity.maximumTootCharacters)
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
        assertEquals(activity.calculateTextLength(), additionalContent.length + DEFAULT_MAXIMUM_URL_LENGTH)
    }

    @Test
    fun whenTextContainsShortUrls_allUrlsGetEllipsized() {
        val shortUrl = "https://tusky.app"
        val url = "https://www.google.dk/search?biw=1920&bih=990&tbm=isch&sa=1&ei=bmDrWuOoKMv6kwWOkIaoDQ&q=indiana+jones+i+hate+snakes+animated&oq=indiana+jones+i+hate+snakes+animated&gs_l=psy-ab.3...54174.55443.0.55553.9.7.0.0.0.0.255.333.1j0j1.2.0....0...1c.1.64.psy-ab..7.0.0....0.40G-kcDkC6A#imgdii=PSp15hQjN1JqvM:&imgrc=H0hyE2JW5wrpBM:"
        val additionalContent = " Check out this @image #search result: "
        insertSomeTextInContent(shortUrl + additionalContent + url)
        assertEquals(activity.calculateTextLength(), additionalContent.length + (DEFAULT_MAXIMUM_URL_LENGTH * 2))
    }

    @Test
    fun whenTextContainsMultipleURLs_allURLsGetEllipsized() {
        val url = "https://www.google.dk/search?biw=1920&bih=990&tbm=isch&sa=1&ei=bmDrWuOoKMv6kwWOkIaoDQ&q=indiana+jones+i+hate+snakes+animated&oq=indiana+jones+i+hate+snakes+animated&gs_l=psy-ab.3...54174.55443.0.55553.9.7.0.0.0.0.255.333.1j0j1.2.0....0...1c.1.64.psy-ab..7.0.0....0.40G-kcDkC6A#imgdii=PSp15hQjN1JqvM:&imgrc=H0hyE2JW5wrpBM:"
        val additionalContent = " Check out this @image #search result: "
        insertSomeTextInContent(url + additionalContent + url)
        assertEquals(activity.calculateTextLength(), additionalContent.length + (DEFAULT_MAXIMUM_URL_LENGTH * 2))
    }

    @Test
    fun whenTextContainsUrl_onlyEllipsizedURLIsCounted_withCustomConfiguration() {
        val url = "https://www.google.dk/search?biw=1920&bih=990&tbm=isch&sa=1&ei=bmDrWuOoKMv6kwWOkIaoDQ&q=indiana+jones+i+hate+snakes+animated&oq=indiana+jones+i+hate+snakes+animated&gs_l=psy-ab.3...54174.55443.0.55553.9.7.0.0.0.0.255.333.1j0j1.2.0....0...1c.1.64.psy-ab..7.0.0....0.40G-kcDkC6A#imgdii=PSp15hQjN1JqvM:&imgrc=H0hyE2JW5wrpBM:"
        val additionalContent = "Check out this @image #search result: "
        val customUrlLength = 16
        instanceResponseCallback = { getInstanceWithCustomConfiguration(configuration = getCustomInstanceConfiguration(charactersReservedPerUrl = customUrlLength)) }
        setupActivity()
        shadowOf(getMainLooper()).idle()
        insertSomeTextInContent(additionalContent + url)
        assertEquals(activity.calculateTextLength(), additionalContent.length + customUrlLength)
    }

    @Test
    fun whenTextContainsShortUrls_allUrlsGetEllipsized_withCustomConfiguration() {
        val shortUrl = "https://tusky.app"
        val url = "https://www.google.dk/search?biw=1920&bih=990&tbm=isch&sa=1&ei=bmDrWuOoKMv6kwWOkIaoDQ&q=indiana+jones+i+hate+snakes+animated&oq=indiana+jones+i+hate+snakes+animated&gs_l=psy-ab.3...54174.55443.0.55553.9.7.0.0.0.0.255.333.1j0j1.2.0....0...1c.1.64.psy-ab..7.0.0....0.40G-kcDkC6A#imgdii=PSp15hQjN1JqvM:&imgrc=H0hyE2JW5wrpBM:"
        val additionalContent = " Check out this @image #search result: "
        val customUrlLength = 18 // The intention is that this is longer than shortUrl.length
        instanceResponseCallback = { getInstanceWithCustomConfiguration(configuration = getCustomInstanceConfiguration(charactersReservedPerUrl = customUrlLength)) }
        setupActivity()
        shadowOf(getMainLooper()).idle()
        insertSomeTextInContent(shortUrl + additionalContent + url)
        assertEquals(activity.calculateTextLength(), additionalContent.length + (customUrlLength * 2))
    }

    @Test
    fun whenTextContainsMultipleURLs_allURLsGetEllipsized_withCustomConfiguration() {
        val url = "https://www.google.dk/search?biw=1920&bih=990&tbm=isch&sa=1&ei=bmDrWuOoKMv6kwWOkIaoDQ&q=indiana+jones+i+hate+snakes+animated&oq=indiana+jones+i+hate+snakes+animated&gs_l=psy-ab.3...54174.55443.0.55553.9.7.0.0.0.0.255.333.1j0j1.2.0....0...1c.1.64.psy-ab..7.0.0....0.40G-kcDkC6A#imgdii=PSp15hQjN1JqvM:&imgrc=H0hyE2JW5wrpBM:"
        val additionalContent = " Check out this @image #search result: "
        val customUrlLength = 16
        instanceResponseCallback = { getInstanceWithCustomConfiguration(configuration = getCustomInstanceConfiguration(charactersReservedPerUrl = customUrlLength)) }
        setupActivity()
        shadowOf(getMainLooper()).idle()
        insertSomeTextInContent(url + additionalContent + url)
        assertEquals(activity.calculateTextLength(), additionalContent.length + (customUrlLength * 2))
    }

    @Test
    fun whenSelectionIsEmpty_specialTextIsInsertedAtCaret() {
        val editor = activity.findViewById<EditText>(R.id.composeEditField)
        val insertText = "#"
        editor.setText("Some text")

        for (caretIndex in listOf(9, 1, 0)) {
            editor.setSelection(caretIndex)
            activity.prependSelectedWordsWith(insertText)
            // Text should be inserted at caret
            assertEquals("Unexpected value at $caretIndex", insertText, editor.text.substring(caretIndex, caretIndex + insertText.length))

            // Caret should be placed after inserted text
            assertEquals(caretIndex + insertText.length, editor.selectionStart)
            assertEquals(caretIndex + insertText.length, editor.selectionEnd)
        }
    }

    @Test
    fun whenSelectionDoesNotIncludeWordBreak_noSpecialTextIsInserted() {
        val editor = activity.findViewById<EditText>(R.id.composeEditField)
        val insertText = "#"
        val originalText = "Some text"
        val selectionStart = 1
        val selectionEnd = 4
        editor.setText(originalText)
        editor.setSelection(selectionStart, selectionEnd) // "ome"
        activity.prependSelectedWordsWith(insertText)

        // Text and selection should be unmodified
        assertEquals(originalText, editor.text.toString())
        assertEquals(selectionStart, editor.selectionStart)
        assertEquals(selectionEnd, editor.selectionEnd)
    }

    @Test
    fun whenSelectionIncludesWordBreaks_startsOfAllWordsArePrepended() {
        val editor = activity.findViewById<EditText>(R.id.composeEditField)
        val insertText = "#"
        val originalText = "one two three four"
        val selectionStart = 2
        val originalSelectionEnd = 15
        val modifiedSelectionEnd = 18
        editor.setText(originalText)
        editor.setSelection(selectionStart, originalSelectionEnd) // "e two three f"
        activity.prependSelectedWordsWith(insertText)

        // text should be inserted at word starts inside selection
        assertEquals("one #two #three #four", editor.text.toString())

        // selection should be expanded accordingly
        assertEquals(selectionStart, editor.selectionStart)
        assertEquals(modifiedSelectionEnd, editor.selectionEnd)
    }

    @Test
    fun whenSelectionIncludesEnd_textIsNotAppended() {
        val editor = activity.findViewById<EditText>(R.id.composeEditField)
        val insertText = "#"
        val originalText = "Some text"
        val selectionStart = 7
        val selectionEnd = 9
        editor.setText(originalText)
        editor.setSelection(selectionStart, selectionEnd) // "xt"
        activity.prependSelectedWordsWith(insertText)

        // Text and selection should be unmodified
        assertEquals(originalText, editor.text.toString())
        assertEquals(selectionStart, editor.selectionStart)
        assertEquals(selectionEnd, editor.selectionEnd)
    }

    @Test
    fun whenSelectionIncludesStartAndStartIsAWord_textIsPrepended() {
        val editor = activity.findViewById<EditText>(R.id.composeEditField)
        val insertText = "#"
        val originalText = "Some text"
        val selectionStart = 0
        val selectionEnd = 3
        editor.setText(originalText)
        editor.setSelection(selectionStart, selectionEnd) // "Som"
        activity.prependSelectedWordsWith(insertText)

        // Text should be inserted at beginning
        assert(editor.text.startsWith(insertText))

        // selection should be expanded accordingly
        assertEquals(selectionStart, editor.selectionStart)
        assertEquals(selectionEnd + insertText.length, editor.selectionEnd)
    }

    @Test
    fun whenSelectionIncludesStartAndStartIsNotAWord_textIsNotPrepended() {
        val editor = activity.findViewById<EditText>(R.id.composeEditField)
        val insertText = "#"
        val originalText = "  Some text"
        val selectionStart = 0
        val selectionEnd = 1
        editor.setText(originalText)
        editor.setSelection(selectionStart, selectionEnd) // " "
        activity.prependSelectedWordsWith(insertText)

        // Text and selection should be unmodified
        assertEquals(originalText, editor.text.toString())
        assertEquals(selectionStart, editor.selectionStart)
        assertEquals(selectionEnd, editor.selectionEnd)
    }

    @Test
    fun whenSelectionBeginsAtWordStart_textIsPrepended() {
        val editor = activity.findViewById<EditText>(R.id.composeEditField)
        val insertText = "#"
        val originalText = "Some text"
        val selectionStart = 5
        val selectionEnd = 9
        editor.setText(originalText)
        editor.setSelection(selectionStart, selectionEnd) // "text"
        activity.prependSelectedWordsWith(insertText)

        // Text is prepended
        assertEquals("Some #text", editor.text.toString())

        // Selection is expanded accordingly
        assertEquals(selectionStart, editor.selectionStart)
        assertEquals(selectionEnd + insertText.length, editor.selectionEnd)
    }

    @Test
    fun whenSelectionEndsAtWordStart_textIsAppended() {
        val editor = activity.findViewById<EditText>(R.id.composeEditField)
        val insertText = "#"
        val originalText = "Some text"
        val selectionStart = 1
        val selectionEnd = 5
        editor.setText(originalText)
        editor.setSelection(selectionStart, selectionEnd) // "ome "
        activity.prependSelectedWordsWith(insertText)

        // Text is prepended
        assertEquals("Some #text", editor.text.toString())

        // Selection is expanded accordingly
        assertEquals(selectionStart, editor.selectionStart)
        assertEquals(selectionEnd + insertText.length, editor.selectionEnd)
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

    private fun getInstanceWithCustomConfiguration(maximumLegacyTootCharacters: Int? = null, configuration: InstanceConfiguration? = null): Instance {
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
                "",
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
            maximumLegacyTootCharacters,
            null,
            null,
            configuration,
        )
    }

    private fun getCustomInstanceConfiguration(maximumStatusCharacters: Int? = null, charactersReservedPerUrl: Int? = null): InstanceConfiguration {
        return InstanceConfiguration(
            statuses = StatusConfiguration(
                maxCharacters = maximumStatusCharacters,
                maxMediaAttachments = null,
                charactersReservedPerUrl = charactersReservedPerUrl
            ),
            mediaAttachments = null,
            polls = null
        )
    }
}
