@file:JvmName("Prefs")

package com.keylesspalace.tusky.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import com.google.gson.Gson
import com.keylesspalace.tusky.util.ThemeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.InputStream
import java.io.OutputStream

data class PrefData(
    var appTheme: String = ThemeUtils.APP_THEME_DEFAULT,
    var emojiFont: Int = 0,
    val hideFab: Boolean = false,
    var language: String = "default",
    val statusTextSize: String = "medium",
    val mainNavPosition: String? = null,
    val hideTopToolbar: Boolean = false,
    val animateAvatars: Boolean = true,
    val useAbsoluteTime: Boolean = false,
    val showBotOverlay: Boolean = true,
    val useBlurhash: Boolean = true,
    val showNotificationsFilter: Boolean = true,
    val showCardsInTimelines: Boolean = false,
    val confirmReblogs: Boolean = true,
    val confirmFavourites: Boolean = false,
    val enableSwipeForTabs: Boolean = true,
    val customTabs: Boolean = false,
    val hideStatsPosts: Boolean = false,
    val hideStatsProfile: Boolean = false,
    val animateEmojis: Boolean = false,
    val tabFilterHomeReplies: Boolean = true,
    val tabFilterHomeBoosts: Boolean = true,

    val httpProxyEnabled: Boolean = false,
    val httpProxyServer: String = "",
    val httpProxyPort: String = ""
)

abstract class GsonSerializer<T>(
    private val classOfData: Class<T>,
) : Serializer<T> {
    private val gson = Gson()

    override suspend fun readFrom(input: InputStream): T {
        return gson.fromJson(input.reader(), classOfData)
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        gson.toJson(t, output.writer())
    }
}

object PrefDataSerializer : GsonSerializer<PrefData>(PrefData::class.java) {
    override val defaultValue: PrefData
        get() = PrefData()
}

fun <T> DataStore<T>.getBlocking() = runBlocking { this@getBlocking.data.first() }
suspend fun <T> DataStore<T>.get() = this.data.first()

typealias PrefStore = DataStore<PrefData>

/** Exposed for special cases, please inject singleton instead! */
fun makePrefStore(context: Context, scope: CoroutineScope): PrefStore {
    return DataStoreFactory.create(
        PrefDataSerializer,
        scope = scope,
    ) {
        // Would love to use dataStoreFile() here but it needs app context which we might not have
        // yet.
        File(context.filesDir, "datastore/prefs.json")
    }
}