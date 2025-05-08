@file:JvmName("AttachmentHelper")

package com.keylesspalace.tusky.util

import android.content.Context
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.Attachment
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

fun Attachment.getFormattedDescription(context: Context): CharSequence {
    val durationInSeconds = meta?.duration ?: 0f
    val duration = if (durationInSeconds > 0f) {
        durationInSeconds.roundToInt().seconds.toComponents { hours, minutes, seconds, _ ->
            "%d:%02d:%02d ".format(hours, minutes, seconds)
        }
    } else {
        ""
    }
    return duration + if (description.isNullOrEmpty()) {
        context.getString(R.string.description_post_media_no_description_placeholder)
    } else {
        description
    }
}

fun List<Attachment>.aspectRatios(): List<Double> {
    return map { attachment ->
        // clamp ratio between 2:1 & 1:2, defaulting to 16:9
        val size = (attachment.meta?.small ?: attachment.meta?.original) ?: return@map 1.7778
        if (size.aspect > 0) return@map size.aspect
        if (size.width == 0 || size.height == 0) return@map 1.7778
        val aspect = size.width.toDouble() / size.height
        aspect.coerceIn(0.5, 2.0)
    }
}
