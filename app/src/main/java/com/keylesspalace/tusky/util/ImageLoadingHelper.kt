/* Copyright 2025 Tusky Contributors
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

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
