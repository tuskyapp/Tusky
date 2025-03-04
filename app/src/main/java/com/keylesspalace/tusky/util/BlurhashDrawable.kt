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
    override fun equals(other: Any?): Boolean {
        return (other as? BlurhashDrawable)?.blurhash == blurhash
    }

    override fun hashCode(): Int {
        return blurhash.hashCode()
    }
}
