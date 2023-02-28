@file:JvmName("AttachmentHelper")

package com.keylesspalace.tusky.util

import android.content.Context
import android.content.res.Resources
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.Attachment
import kotlin.math.roundToInt

fun Attachment.getFormattedDescription(context: Context): CharSequence {
    var duration = ""
    if (meta?.duration != null && meta.duration > 0) {
        duration = formatDuration(meta.duration.toDouble()) + " "
    }
    return if (description.isNullOrEmpty()) {
        duration + context.getString(R.string.description_post_media_no_description_placeholder)
    } else {
        duration + description
    }
}

private fun formatDuration(durationInSeconds: Double): String {
    val seconds = durationInSeconds.roundToInt() % 60
    val minutes = durationInSeconds.toInt() % 3600 / 60
    val hours = durationInSeconds.toInt() / 3600
    return "%d:%02d:%02d".format(hours, minutes, seconds)
}

fun List<Attachment>.aspectRatios(minAspect: Double, maxAspect: Double): List<Double> {
    return map { attachment ->
        // clamp ratio between min & max, defaulting to 16:9 if there is no metadata
        val size = (attachment.meta?.small ?: attachment.meta?.original) ?: return@map 1.7778
        val aspect = if (size.aspect > 0) size.aspect else size.width.toDouble() / size.height
        aspect.coerceIn(minAspect, maxAspect)
    }
}
