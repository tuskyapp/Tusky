@file:JvmName("ImageLoadingHelper")

package com.keylesspalace.tusky.util

import android.graphics.Bitmap
import android.widget.ImageView
import androidx.annotation.Px
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.keylesspalace.tusky.R

private val centerCropTransformation = CenterCrop()

fun loadAvatar(
    url: String?,
    imageView: ImageView,
    @Px radius: Int,
    animate: Boolean,
    transforms: List<Transformation<Bitmap>>? = null
) {
    if (url.isNullOrBlank()) {
        Glide.with(imageView)
            .load(R.drawable.avatar_default)
            .into(imageView)
    } else {
        val multiTransformation = MultiTransformation(
            buildList {
                transforms?.let { this.addAll(it) }
                add(centerCropTransformation)
                add(RoundedCorners(radius))
            }
        )

        if (animate) {
            Glide.with(imageView)
                .load(url)
                .transform(multiTransformation)
                .placeholder(R.drawable.avatar_default)
                .into(imageView)
        } else {
            Glide.with(imageView)
                .asBitmap()
                .load(url)
                .transform(multiTransformation)
                .placeholder(R.drawable.avatar_default)
                .into(imageView)
        }
    }
}
