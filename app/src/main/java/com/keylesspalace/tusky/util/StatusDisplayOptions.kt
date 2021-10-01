package com.keylesspalace.tusky.util

data class StatusDisplayOptions(
    @get:JvmName("animateAvatars")
    val animateAvatars: Boolean,
    @get:JvmName("mediaPreviewEnabled")
    val mediaPreviewEnabled: Boolean,
    @get:JvmName("useAbsoluteTime")
    val useAbsoluteTime: Boolean,
    @get:JvmName("showBotOverlay")
    val showBotOverlay: Boolean,
    @get:JvmName("useBlurhash")
    val useBlurhash: Boolean,
    @get:JvmName("cardViewMode")
    val cardViewMode: CardViewMode,
    @get:JvmName("confirmReblogs")
    val confirmReblogs: Boolean,
    @get:JvmName("confirmFavourites")
    val confirmFavourites: Boolean,
    @get:JvmName("hideStats")
    val hideStats: Boolean,
    @get:JvmName("animateEmojis")
    val animateEmojis: Boolean
)
