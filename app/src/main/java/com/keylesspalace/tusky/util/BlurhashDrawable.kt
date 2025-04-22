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

package com.keylesspalace.tusky.util

import android.content.Context
import android.graphics.drawable.BitmapDrawable

/**
 * Drawable to display blurhashes with custom equals and hashCode implementation.
 * This is so Glide does not flicker unnecessarily when it is used with blurhashes as placeholder.
 */
class BlurhashDrawable(
    context: Context,
    val blurhash: String
) : BitmapDrawable(
    context.resources,
    BlurHashDecoder.decode(blurhash, 32, 32, 1f)
) {
    override fun equals(other: Any?): Boolean = (other as? BlurhashDrawable)?.blurhash == blurhash

    override fun hashCode(): Int = blurhash.hashCode()
}
