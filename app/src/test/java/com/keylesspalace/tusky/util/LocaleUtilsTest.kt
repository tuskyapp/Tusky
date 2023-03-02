package com.keylesspalace.tusky.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.keylesspalace.tusky.db.AccountEntity
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.annotation.Config

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
class LocaleUtilsTest {
    @Test
    fun initialLanguagesContainReplySelectedAppAndSystem() {
        val expectedLanguages = arrayOf<String?>("yi", "tok", "da", "fr", "sv", "kab")
        val languages = getMockedInitialLanguages(expectedLanguages)
        Assert.assertArrayEquals(expectedLanguages, languages.subList(0, expectedLanguages.size).toTypedArray())
    }

    @Test
    fun whenReplyLanguageIsNull_DefaultLanguageIsFirst() {
        val defaultLanguage = "tok"
        val languages = getMockedInitialLanguages(arrayOf(null, defaultLanguage, "da", "fr", "sv", "kab"))
        Assert.assertEquals(defaultLanguage, languages[0])
    }

    @Test
    fun initialLanguagesAreDistinct() {
        val defaultLanguage = "da"
        val languages = getMockedInitialLanguages(arrayOf(defaultLanguage, defaultLanguage, "fr", defaultLanguage, "kab", defaultLanguage))
        Assert.assertEquals(1, languages.count { it == defaultLanguage })
    }

    @Test
    fun initialLanguageDeduplicationDoesNotReorder() {
        val defaultLanguage = "da"

        Assert.assertEquals(
            defaultLanguage,
            getMockedInitialLanguages(arrayOf(defaultLanguage, defaultLanguage, "fr", defaultLanguage, "kab", defaultLanguage))[0]
        )
        Assert.assertEquals(
            defaultLanguage,
            getMockedInitialLanguages(arrayOf(null, defaultLanguage, "fr", defaultLanguage, "kab", defaultLanguage))[0]
        )
    }

    @Test
    fun emptyInitialLanguagesAreDropped() {
        val languages = getMockedInitialLanguages(arrayOf("", "", "fr", "", "kab", ""))
        Assert.assertFalse(languages.any { it.isEmpty() })
    }

    private fun getMockedInitialLanguages(configuredLanguages: Array<String?>): List<String> {
        val appLanguages = LocaleListCompat.forLanguageTags(configuredLanguages.slice(2 until 4).joinToString(","))
        val systemLanguages = LocaleListCompat.forLanguageTags(configuredLanguages.slice(4 until configuredLanguages.size).joinToString(","))

        Mockito.mockStatic(AppCompatDelegate::class.java).use { appCompatDelegate ->
            appCompatDelegate.`when`<LocaleListCompat> { AppCompatDelegate.getApplicationLocales() }.thenReturn(appLanguages)

            Mockito.mockStatic(LocaleListCompat::class.java).use { localeListCompat ->
                localeListCompat.`when`<LocaleListCompat> { LocaleListCompat.getDefault() }.thenReturn(systemLanguages)

                return getInitialLanguages(
                    configuredLanguages[0],
                    AccountEntity(
                        id = 0,
                        domain = "foo.bar",
                        accessToken = "",
                        clientId = null,
                        clientSecret = null,
                        isActive = true,
                        defaultPostLanguage = configuredLanguages[1].orEmpty(),
                    )
                )
            }
        }
    }
}
