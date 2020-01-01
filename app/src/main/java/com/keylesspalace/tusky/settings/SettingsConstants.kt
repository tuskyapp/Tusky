package com.keylesspalace.tusky.settings

enum class AppTheme(val value: String) {
    NIGHT("night"),
    DAY("day"),
    BLACK("black"),
    AUTO("auto"),
    AUTO_SYSTEM("auto_system");

    companion object {
        fun stringValues() = values().map { it.value }.toTypedArray()
    }
}

object PrefKeys {
    const val APP_THEME = "appTheme"
    const val EMOJI = "emojiCompat"
    const val FAB_HIDE = "fabHide"
    const val LANGUAGE = "language"
    const val STATUS_TEXT_SIZE = "statusTextSize"
    const val ABSOLUTE_TIME_VIEW = "absoluteTimeView"
    const val SHOW_BOT_OVERLAY = "showBotOverlay"
    const val ANIMATE_GIF_AVATARS = "animateGifAvatars"
    const val USE_BLURHASH = "useBlurhash"
    const val SHOW_NOTIFICATIONS_FILTER = "showNotificationsFilter"
    const val SHOW_CARDS_IN_TIMELINES = "showCardsInTimelines"
    const val ENABLE_SWIPE_FOR_TABS = "enableSwipeForTabs"

    const val CUSTOM_TABS = "customTabs"

    const val HTTP_PROXY_ENABLED = "httpProxyEnabled"
    const val HTTP_PROXY_SERVER = "httpProxyServer"
    const val HTTP_PROXY_PORT = "httpProxyPort"


}
