package com.keylesspalace.tusky.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.Keep
import androidx.preference.PreferenceManager
import com.keylesspalace.tusky.util.ThemeUtils
import javax.inject.Inject
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

// TODO: do we every plan to use them as writable properties?
// TODO: is this enough to preserve fields/names?
// TODO: what about observing changes? Do we keep PrefKeys and reference them?
@Keep
class Prefs @Inject constructor(context: Context) {
    // TODO: not sure if should be lazy or non-cached at all
    private val sharedPreferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    var appTheme by stringProperty(ThemeUtils.APP_THEME_DEFAULT)
    var emojiFont by intProperty(0, "selected_emoji_font")
    val hideFab by booleanProperty(false, "fabHide")
    var language by stringProperty(defaultValue = "default")
    val statusTextSize by stringProperty("medium")
    val mainNavPosition by stringProperty()
    val hideTopToolbar by booleanProperty(false)

    val animateAvatars by booleanProperty(false, "animateGifAvatars")
    val useAbsoluteTime by booleanProperty(false, "absoluteTimeView")
    val showBotOverlay by booleanProperty(true)
    val useBlurhash by booleanProperty(true)
    val showNotificationsFilter by booleanProperty(true)
    val showCardsInTimelines by booleanProperty(false)
    val confirmReblogs by booleanProperty(true)
    val confirmFavourites by booleanProperty(false)
    val enableSwipeForTabs by booleanProperty(true)
    val customTabs by booleanProperty(false)
    val hideStatsPosts by booleanProperty(false, "wellbeingHideStatsPosts")
    val hideStatsProfile by booleanProperty(false, "wellbeingHideStatsProfile")
    val animateEmojis by booleanProperty(false, "animateCustomEmojis")
    val tabFilterHomeReplies by booleanProperty(true)
    val tabFilterHomeBoosts by booleanProperty(true)

    val httpProxyEnabled by booleanProperty(false)
    val httpProxyServer by stringProperty(defaultValue = "")
    val httpProxyPort by stringProperty(defaultValue = "")

    private fun stringProperty(overrideName: String? = null) =
        StringProperty(sharedPreferences, overrideName)

    private fun stringProperty(
        defaultValue: String,
        overrideName: String? = null,
    ): ReadWriteProperty<Prefs, String> =
        this.stringProperty(overrideName).withDefault(defaultValue)

    private fun booleanProperty(
        defaultValue: Boolean,
        overrideName: String? = null,
    ) = BooleanProperty(sharedPreferences, overrideName, defaultValue)

    private fun intProperty(
        defaultValue: Int,
        overrideName: String? = null,
    ) = IntProperty(sharedPreferences, overrideName, defaultValue)
}

private fun <T, P> ReadWriteProperty<T, P?>.withDefault(
    default: P
): ReadWriteProperty<T, P> = object : ReadWriteProperty<T, P> {
    override fun getValue(thisRef: T, property: KProperty<*>): P {
        return this@withDefault.getValue(thisRef, property) ?: default
    }

    override fun setValue(thisRef: T, property: KProperty<*>, value: P) {
        this@withDefault.setValue(thisRef, property, value)
    }
}

private class StringProperty(
    private val sharedPreferences: SharedPreferences,
    private val overrideName: String?,
) : ReadWriteProperty<Prefs, String?> {
    override fun getValue(thisRef: Prefs, property: KProperty<*>): String? {
        return sharedPreferences.getString(overrideName ?: property.name, null)
    }

    override fun setValue(thisRef: Prefs, property: KProperty<*>, value: String?) {
        sharedPreferences.edit().putString(overrideName ?: property.name, value)
            .apply()
    }
}

private class BooleanProperty(
    private val sharedPreferences: SharedPreferences,
    private val overrideName: String?,
    private val defaultValue: Boolean,
) : ReadWriteProperty<Prefs, Boolean> {
    override fun getValue(thisRef: Prefs, property: KProperty<*>): Boolean {
        return sharedPreferences.getBoolean(
            overrideName ?: property.name,
            defaultValue,
        )
    }

    override fun setValue(thisRef: Prefs, property: KProperty<*>, value: Boolean) {
        sharedPreferences.edit().putBoolean(overrideName ?: property.name, value)
            .apply()
    }
}

private class IntProperty(
    private val sharedPreferences: SharedPreferences,
    private val overrideName: String?,
    private val defaultValue: Int,
) : ReadWriteProperty<Prefs, Int> {
    override fun getValue(thisRef: Prefs, property: KProperty<*>): Int {
        return sharedPreferences.getInt(
            overrideName ?: property.name,
            defaultValue,
        )
    }

    override fun setValue(thisRef: Prefs, property: KProperty<*>, value: Int) {
        sharedPreferences.edit().putInt(overrideName ?: property.name, value)
            .apply()
    }
}
