/*
 * Copyright 2018 Tusky Contributors
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

package com.keylesspalace.tusky.components.compose

import android.content.Intent
import android.os.Looper.getMainLooper
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import at.connyduck.calladapter.networkresult.NetworkResult
import com.google.gson.Gson
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.instanceinfo.InstanceInfoRepository
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.AppDatabase
import com.keylesspalace.tusky.db.EmojisEntity
import com.keylesspalace.tusky.db.InstanceDao
import com.keylesspalace.tusky.db.InstanceInfoEntity
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.entity.Instance
import com.keylesspalace.tusky.entity.InstanceConfiguration
import com.keylesspalace.tusky.entity.InstanceV1
import com.keylesspalace.tusky.entity.StatusConfiguration
import com.keylesspalace.tusky.network.MastodonApi
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboMenuItem
import retrofit2.HttpException
import retrofit2.Response
import java.util.Locale

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
        clientId = "id",
        clientSecret = "secret",
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
    private var instanceV1ResponseCallback: (() -> InstanceV1)? = null
    private var instanceResponseCallback: (() -> Instance)? = null
    private var composeOptions: ComposeActivity.ComposeOptions? = null
    private val gson = Gson()

    @Before
    fun setupActivity() {
        val controller = Robolectric.buildActivity(ComposeActivity::class.java)
        activity = controller.get()

        accountManagerMock = mock {
            on { activeAccount } doReturn account
        }

        apiMock = mock {
            onBlocking { getCustomEmojis() } doReturn NetworkResult.success(emptyList())
            onBlocking { getInstance() } doReturn instanceResponseCallback?.invoke().let { instance ->
                if (instance == null) {
                    NetworkResult.failure(HttpException(Response.error<ResponseBody>(404, "Not found".toResponseBody())))
                } else {
                    NetworkResult.success(instance)
                }
            }
            onBlocking { getInstanceV1() } doReturn instanceV1ResponseCallback?.invoke().let { instance ->
                if (instance == null) {
                    NetworkResult.failure(Throwable())
                } else {
                    NetworkResult.success(instance)
                }
            }
        }

        val instanceDaoMock: InstanceDao = mock {
            onBlocking { getInstanceInfo(any()) } doReturn
                InstanceInfoEntity(instanceDomain, null, null, null, null, null, null, null, null, null, null, null, null, null, null)
            onBlocking { getEmojiInfo(any()) } doReturn
                EmojisEntity(instanceDomain, emptyList())
        }

        val dbMock: AppDatabase = mock {
            on { instanceDao() } doReturn instanceDaoMock
        }

        val instanceInfoRepo = InstanceInfoRepository(apiMock, dbMock, accountManagerMock)

        val viewModel = ComposeViewModel(
            apiMock,
            accountManagerMock,
            mock(),
            mock(),
            mock(),
            instanceInfoRepo
        )
        activity.intent = Intent(activity, ComposeActivity::class.java).apply {
            putExtra(ComposeActivity.COMPOSE_OPTIONS_EXTRA, composeOptions)
        }

        val viewModelFactoryMock: ViewModelFactory = mock {
            on { create(eq(ComposeViewModel::class.java), any()) } doReturn viewModel
        }

        activity.accountManager = accountManagerMock
        activity.viewModelFactory = viewModelFactoryMock

        controller.create().start()
        shadowOf(getMainLooper()).idle()
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
        instanceV1ResponseCallback = { getInstanceV1WithCustomConfiguration(null) }
        setupActivity()
        assertEquals(InstanceInfoRepository.DEFAULT_CHARACTER_LIMIT, activity.maximumTootCharacters)
    }

    @Test
    fun whenMaximumTootCharsIsPopulated_customLimitIsUsed() {
        val customMaximum = 1000
        instanceResponseCallback = { getInstanceWithCustomConfiguration(customMaximum) }
        setupActivity()
        shadowOf(getMainLooper()).idle()
        assertEquals(customMaximum, activity.maximumTootCharacters)
    }

    @Test
    fun whenOnlyLegacyMaximumTootCharsIsPopulated_customLimitIsUsed() {
        val customMaximum = 1000
        instanceV1ResponseCallback = { getInstanceV1WithCustomConfiguration(customMaximum) }
        setupActivity()
        shadowOf(getMainLooper()).idle()
        assertEquals(customMaximum, activity.maximumTootCharacters)
    }

    @Test
    fun whenOnlyConfigurationMaximumTootCharsIsPopulated_customLimitIsUsed() {
        val customMaximum = 1000
        instanceV1ResponseCallback = { getInstanceV1WithCustomConfiguration(null, getCustomInstanceConfiguration(maximumStatusCharacters = customMaximum)) }
        setupActivity()
        shadowOf(getMainLooper()).idle()
        assertEquals(customMaximum, activity.maximumTootCharacters)
    }

    @Test
    fun whenDifferentCharLimitsArePopulated_statusConfigurationLimitIsUsed() {
        val customMaximum = 1000
        instanceV1ResponseCallback = { getInstanceV1WithCustomConfiguration(customMaximum, getCustomInstanceConfiguration(maximumStatusCharacters = customMaximum * 2)) }
        setupActivity()
        shadowOf(getMainLooper()).idle()
        assertEquals(customMaximum * 2, activity.maximumTootCharacters)
    }

    @Test
    fun whenTextContainsNoUrl_everyCharacterIsCounted() {
        val content = "This is test content please ignore thx "
        insertSomeTextInContent(content)
        assertEquals(content.length, activity.calculateTextLength())
    }

    @Test
    fun whenTextContainsEmoji_emojisAreCountedAsOneCharacter() {
        val content = "Test 😜"
        insertSomeTextInContent(content)
        assertEquals(6, activity.calculateTextLength())
    }

    @Test
    fun whenTextContainsUrlWithEmoji_ellipsizedUrlIsCountedCorrectly() {
        val content = "https://🤪.com"
        insertSomeTextInContent(content)
        assertEquals(InstanceInfoRepository.DEFAULT_CHARACTERS_RESERVED_PER_URL, activity.calculateTextLength())
    }

    @Test
    fun whenTextContainsUrl_onlyEllipsizedURLIsCounted() {
        val url = "https://www.google.dk/search?biw=1920&bih=990&tbm=isch&sa=1&ei=bmDrWuOoKMv6kwWOkIaoDQ&q=indiana+jones+i+hate+snakes+animated&oq=indiana+jones+i+hate+snakes+animated&gs_l=psy-ab.3...54174.55443.0.55553.9.7.0.0.0.0.255.333.1j0j1.2.0....0...1c.1.64.psy-ab..7.0.0....0.40G-kcDkC6A#imgdii=PSp15hQjN1JqvM:&imgrc=H0hyE2JW5wrpBM:"
        val additionalContent = "Check out this @image #search result: "
        insertSomeTextInContent(additionalContent + url)
        assertEquals(additionalContent.length + InstanceInfoRepository.DEFAULT_CHARACTERS_RESERVED_PER_URL, activity.calculateTextLength())
    }

    @Test
    fun whenTextContainsShortUrls_allUrlsGetEllipsized() {
        val shortUrl = "https://tusky.app"
        val url = "https://www.google.dk/search?biw=1920&bih=990&tbm=isch&sa=1&ei=bmDrWuOoKMv6kwWOkIaoDQ&q=indiana+jones+i+hate+snakes+animated&oq=indiana+jones+i+hate+snakes+animated&gs_l=psy-ab.3...54174.55443.0.55553.9.7.0.0.0.0.255.333.1j0j1.2.0....0...1c.1.64.psy-ab..7.0.0....0.40G-kcDkC6A#imgdii=PSp15hQjN1JqvM:&imgrc=H0hyE2JW5wrpBM:"
        val additionalContent = " Check out this @image #search result: "
        insertSomeTextInContent(shortUrl + additionalContent + url)
        assertEquals(additionalContent.length + (InstanceInfoRepository.DEFAULT_CHARACTERS_RESERVED_PER_URL * 2), activity.calculateTextLength())
    }

    @Test
    fun whenTextContainsMultipleURLs_allURLsGetEllipsized() {
        val url = "https://www.google.dk/search?biw=1920&bih=990&tbm=isch&sa=1&ei=bmDrWuOoKMv6kwWOkIaoDQ&q=indiana+jones+i+hate+snakes+animated&oq=indiana+jones+i+hate+snakes+animated&gs_l=psy-ab.3...54174.55443.0.55553.9.7.0.0.0.0.255.333.1j0j1.2.0....0...1c.1.64.psy-ab..7.0.0....0.40G-kcDkC6A#imgdii=PSp15hQjN1JqvM:&imgrc=H0hyE2JW5wrpBM:"
        val additionalContent = " Check out this @image #search result: "
        insertSomeTextInContent(url + additionalContent + url)
        assertEquals(additionalContent.length + (InstanceInfoRepository.DEFAULT_CHARACTERS_RESERVED_PER_URL * 2), activity.calculateTextLength())
    }

    @Test
    fun whenTextContainsUrl_onlyEllipsizedURLIsCounted_withCustomConfiguration() {
        val url = "https://www.google.dk/search?biw=1920&bih=990&tbm=isch&sa=1&ei=bmDrWuOoKMv6kwWOkIaoDQ&q=indiana+jones+i+hate+snakes+animated&oq=indiana+jones+i+hate+snakes+animated&gs_l=psy-ab.3...54174.55443.0.55553.9.7.0.0.0.0.255.333.1j0j1.2.0....0...1c.1.64.psy-ab..7.0.0....0.40G-kcDkC6A#imgdii=PSp15hQjN1JqvM:&imgrc=H0hyE2JW5wrpBM:"
        val additionalContent = "Check out this @image #search result: "
        val customUrlLength = 16
        instanceResponseCallback = { getInstanceWithCustomConfiguration(null, customUrlLength) }
        setupActivity()
        shadowOf(getMainLooper()).idle()
        insertSomeTextInContent(additionalContent + url)
        assertEquals(additionalContent.length + customUrlLength, activity.calculateTextLength())
    }

    @Test
    fun whenTextContainsUrl_onlyEllipsizedURLIsCounted_withCustomConfigurationV1() {
        val url = "https://www.google.dk/search?biw=1920&bih=990&tbm=isch&sa=1&ei=bmDrWuOoKMv6kwWOkIaoDQ&q=indiana+jones+i+hate+snakes+animated&oq=indiana+jones+i+hate+snakes+animated&gs_l=psy-ab.3...54174.55443.0.55553.9.7.0.0.0.0.255.333.1j0j1.2.0....0...1c.1.64.psy-ab..7.0.0....0.40G-kcDkC6A#imgdii=PSp15hQjN1JqvM:&imgrc=H0hyE2JW5wrpBM:"
        val additionalContent = "Check out this @image #search result: "
        val customUrlLength = 16
        instanceV1ResponseCallback = { getInstanceV1WithCustomConfiguration(configuration = getCustomInstanceConfiguration(charactersReservedPerUrl = customUrlLength)) }
        setupActivity()
        shadowOf(getMainLooper()).idle()
        insertSomeTextInContent(additionalContent + url)
        assertEquals(additionalContent.length + customUrlLength, activity.calculateTextLength())
    }

    @Test
    fun whenTextContainsShortUrls_allUrlsGetEllipsized_withCustomConfiguration() {
        val shortUrl = "https://tusky.app"
        val url = "https://www.google.dk/search?biw=1920&bih=990&tbm=isch&sa=1&ei=bmDrWuOoKMv6kwWOkIaoDQ&q=indiana+jones+i+hate+snakes+animated&oq=indiana+jones+i+hate+snakes+animated&gs_l=psy-ab.3...54174.55443.0.55553.9.7.0.0.0.0.255.333.1j0j1.2.0....0...1c.1.64.psy-ab..7.0.0....0.40G-kcDkC6A#imgdii=PSp15hQjN1JqvM:&imgrc=H0hyE2JW5wrpBM:"
        val additionalContent = " Check out this @image #search result: "
        val customUrlLength = 18 // The intention is that this is longer than shortUrl.length
        instanceResponseCallback = { getInstanceWithCustomConfiguration(null, customUrlLength) }
        setupActivity()
        shadowOf(getMainLooper()).idle()
        insertSomeTextInContent(shortUrl + additionalContent + url)
        assertEquals(additionalContent.length + (customUrlLength * 2), activity.calculateTextLength())
    }

    @Test
    fun whenTextContainsShortUrls_allUrlsGetEllipsized_withCustomConfigurationV1() {
        val shortUrl = "https://tusky.app"
        val url = "https://www.google.dk/search?biw=1920&bih=990&tbm=isch&sa=1&ei=bmDrWuOoKMv6kwWOkIaoDQ&q=indiana+jones+i+hate+snakes+animated&oq=indiana+jones+i+hate+snakes+animated&gs_l=psy-ab.3...54174.55443.0.55553.9.7.0.0.0.0.255.333.1j0j1.2.0....0...1c.1.64.psy-ab..7.0.0....0.40G-kcDkC6A#imgdii=PSp15hQjN1JqvM:&imgrc=H0hyE2JW5wrpBM:"
        val additionalContent = " Check out this @image #search result: "
        val customUrlLength = 18 // The intention is that this is longer than shortUrl.length
        instanceV1ResponseCallback = { getInstanceV1WithCustomConfiguration(configuration = getCustomInstanceConfiguration(charactersReservedPerUrl = customUrlLength)) }
        setupActivity()
        shadowOf(getMainLooper()).idle()
        insertSomeTextInContent(shortUrl + additionalContent + url)
        assertEquals(additionalContent.length + (customUrlLength * 2), activity.calculateTextLength())
    }

    @Test
    fun whenTextContainsMultipleURLs_allURLsGetEllipsized_withCustomConfiguration() {
        val url = "https://www.google.dk/search?biw=1920&bih=990&tbm=isch&sa=1&ei=bmDrWuOoKMv6kwWOkIaoDQ&q=indiana+jones+i+hate+snakes+animated&oq=indiana+jones+i+hate+snakes+animated&gs_l=psy-ab.3...54174.55443.0.55553.9.7.0.0.0.0.255.333.1j0j1.2.0....0...1c.1.64.psy-ab..7.0.0....0.40G-kcDkC6A#imgdii=PSp15hQjN1JqvM:&imgrc=H0hyE2JW5wrpBM:"
        val additionalContent = " Check out this @image #search result: "
        val customUrlLength = 16
        instanceResponseCallback = { getInstanceWithCustomConfiguration(null, customUrlLength) }
        setupActivity()
        shadowOf(getMainLooper()).idle()
        insertSomeTextInContent(url + additionalContent + url)
        assertEquals(additionalContent.length + (customUrlLength * 2), activity.calculateTextLength())
    }

    @Test
    fun whenTextContainsMultipleURLs_allURLsGetEllipsized_withCustomConfigurationV1() {
        val url = "https://www.google.dk/search?biw=1920&bih=990&tbm=isch&sa=1&ei=bmDrWuOoKMv6kwWOkIaoDQ&q=indiana+jones+i+hate+snakes+animated&oq=indiana+jones+i+hate+snakes+animated&gs_l=psy-ab.3...54174.55443.0.55553.9.7.0.0.0.0.255.333.1j0j1.2.0....0...1c.1.64.psy-ab..7.0.0....0.40G-kcDkC6A#imgdii=PSp15hQjN1JqvM:&imgrc=H0hyE2JW5wrpBM:"
        val additionalContent = " Check out this @image #search result: "
        val customUrlLength = 16
        instanceV1ResponseCallback = { getInstanceV1WithCustomConfiguration(configuration = getCustomInstanceConfiguration(charactersReservedPerUrl = customUrlLength)) }
        setupActivity()
        shadowOf(getMainLooper()).idle()
        insertSomeTextInContent(url + additionalContent + url)
        assertEquals(additionalContent.length + (customUrlLength * 2), activity.calculateTextLength())
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

    @Test
    fun whenNoLanguageIsGiven_defaultLanguageIsSelected() {
        assertEquals(Locale.getDefault().language, activity.selectedLanguage)
    }

    @Test
    fun languageGivenInComposeOptionsIsRespected() {
        val language = "no"
        composeOptions = ComposeActivity.ComposeOptions(language = language)
        setupActivity()
        assertEquals(language, activity.selectedLanguage)
    }

    @Test
    fun modernLanguageCodeIsUsed() {
        // https://github.com/tuskyapp/Tusky/issues/2903
        // "ji" was deprecated in favor of "yi"
        composeOptions = ComposeActivity.ComposeOptions(language = "ji")
        setupActivity()
        assertEquals("yi", activity.selectedLanguage)
    }

    @Test
    fun unknownLanguageGivenInComposeOptionsIsRespected() {
        val language = "zzz"
        composeOptions = ComposeActivity.ComposeOptions(language = language)
        setupActivity()
        assertEquals(language, activity.selectedLanguage)
    }

    @Test
    fun sampleFriendicaInstanceResponseIsDeserializable() {
        // https://github.com/tuskyapp/Tusky/issues/4100
        instanceResponseCallback = { getSampleFriendicaInstance() }
        setupActivity()
        shadowOf(getMainLooper()).idle()
        assertEquals(friendicaMaximum, activity.maximumTootCharacters)
    }

    private fun clickUp() {
        val menuItem = RoboMenuItem(android.R.id.home)
        activity.onOptionsItemSelected(menuItem)
    }

    private fun clickBack() {
        activity.onBackPressedDispatcher.onBackPressed()
    }

    private fun insertSomeTextInContent(text: String? = null) {
        activity.findViewById<EditText>(R.id.composeEditField).setText(text ?: "Some text")
    }

    private fun getInstanceWithCustomConfiguration(maximumStatusCharacters: Int? = null, charactersReservedPerUrl: Int? = null): Instance {
        return Instance(
            domain = "https://example.token",
            version = "2.6.3",
            configuration = getConfiguration(maximumStatusCharacters, charactersReservedPerUrl),
            pleroma = null,
            rules = emptyList()
        )
    }

    private fun getConfiguration(maximumStatusCharacters: Int?, charactersReservedPerUrl: Int?): Instance.Configuration {
        return Instance.Configuration(
            Instance.Configuration.Urls(streamingApi = ""),
            Instance.Configuration.Accounts(1),
            Instance.Configuration.Statuses(
                maximumStatusCharacters ?: InstanceInfoRepository.DEFAULT_CHARACTER_LIMIT,
                InstanceInfoRepository.DEFAULT_MAX_MEDIA_ATTACHMENTS,
                charactersReservedPerUrl ?: InstanceInfoRepository.DEFAULT_CHARACTERS_RESERVED_PER_URL
            ),
            Instance.Configuration.MediaAttachments(0, 0, 0, 0, 0),
            Instance.Configuration.Polls(0, 0, 0, 0),
            Instance.Configuration.Translation(false),
        )
    }

    private fun getInstanceV1WithCustomConfiguration(maximumLegacyTootCharacters: Int? = null, configuration: InstanceConfiguration? = null): InstanceV1 {
        return InstanceV1(
            uri = "https://example.token",
            version = "2.6.3",
            maxTootChars = maximumLegacyTootCharacters,
            pollConfiguration = null,
            configuration = configuration,
            maxMediaAttachments = null,
            pleroma = null,
            uploadLimit = null,
            rules = emptyList()
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

    private fun getSampleFriendicaInstance(): Instance {
        return gson.fromJson(sampleFriendicaResponse, Instance::class.java)
    }

    companion object {
        private const val friendicaMaximum = 200000

        // https://github.com/tuskyapp/Tusky/issues/4100
        private val sampleFriendicaResponse = """{
                "domain": "loma.ml",
                "title": "[ˈloma]",
                "version": "2.8.0 (compatible; Friendica 2023.09-rc)",
                "source_url": "https://git.friendi.ca/friendica/friendica",
                "description": "loma.ml ist eine Friendica Community im Fediverse auf der vorwiegend DE \uD83C\uDDE9\uD83C\uDDEA gesprochen wird. \\r\\nServer in Germany/EU \uD83C\uDDE9\uD83C\uDDEA \uD83C\uDDEA\uD83C\uDDFA. Open to all with fun in new. \\r\\nServer in Deutschland. Offen für alle mit Spaß an Neuen.",
                "usage": {
                    "users": {
                        "active_month": 125
                    }
                },
                "thumbnail": {
                    "url": "https://loma.ml/ad/friendica-banner.jpg"
                },
                "languages": [
                    "de"
                ],
                "configuration": {
                    "statuses": {
                        "max_characters": $friendicaMaximum
                    },
                    "media_attachments": {
                        "supported_mime_types": {
                            "image/jpeg": "jpg",
                            "image/jpg": "jpg",
                            "image/png": "png",
                            "image/gif": "gif"
                        },
                        "image_size_limit": 10485760
                    }
                },
                "registrations": {
                    "enabled": true,
                    "approval_required": false
                },
                "contact": {
                    "email": "anony@miz.ed",
                    "account": {
                        "id": "9632",
                        "username": "webm",
                        "acct": "webm",
                        "display_name": "web m \uD83C\uDDEA\uD83C\uDDFA",
                        "locked": false,
                        "bot": false,
                        "discoverable": true,
                        "group": false,
                        "created_at": "2018-05-21T11:24:55.000Z",
                        "note": "\uD83C\uDDE9\uD83C\uDDEA Über diesen Account werden Änderungen oder geplante Beeinträchtigungen angekündigt. Wenn du einen Account auf Loma.ml besitzt, dann solltest du dich mit mir verbinden.\uD83C\uDDEA\uD83C\uDDFA Changes or planned impairments are announced via this account. If you have an account on Loma.ml, you should connect to me.\uD83C\uDD98 Fallbackaccount @webm@joinfriendica.de",
                        "url": "https://loma.ml/profile/webm",
                        "avatar": "https://loma.ml/photo/contact/320/373ebf56355ac895a09cb99264485383?ts=1686417730",
                        "avatar_static": "https://loma.ml/photo/contact/320/373ebf56355ac895a09cb99264485383?ts=1686417730&static=1",
                        "header": "https://loma.ml/photo/header/373ebf56355ac895a09cb99264485383?ts=1686417730",
                        "header_static": "https://loma.ml/photo/header/373ebf56355ac895a09cb99264485383?ts=1686417730&static=1",
                        "followers_count": 23,
                        "following_count": 25,
                        "statuses_count": 15,
                        "last_status_at": "2023-09-19T00:00:00.000Z",
                        "emojis": [],
                        "fields": []
                    }
                },
                "rules": [],
                "friendica": {
                    "version": "2023.09-rc",
                    "codename": "Giant Rhubarb",
                    "db_version": 1539
                }
            }
        """.trimIndent()
    }
}
