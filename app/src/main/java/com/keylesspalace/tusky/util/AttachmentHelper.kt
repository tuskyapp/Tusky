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

fun List<Attachment>.aspectRatios(minAspect: Double, maxAspect: Double): List<Double> {
    return map { attachment ->
        // clamp ratio between min & max, defaulting to 16:9 if there is no metadata
        val size = (attachment.meta?.small ?: attachment.meta?.original) ?: return@map 1.7778
        val aspect = if (size.aspect > 0) size.aspect else size.width.toDouble() / size.height
        aspect.coerceIn(minAspect, maxAspect)
    }
}
