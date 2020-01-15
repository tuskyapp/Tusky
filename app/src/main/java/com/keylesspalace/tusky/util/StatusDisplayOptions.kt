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
        val useBlurhash: Boolean
)