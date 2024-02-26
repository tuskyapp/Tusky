package com.keylesspalace.tusky.util

import android.graphics.drawable.Drawable
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.IOException

/**
 * Allows waiting for a Glide request to complete without blocking a background thread.
 */
suspend fun <R> RequestBuilder<R>.submitAsync(
    width: Int = Int.MIN_VALUE,
    height: Int = Int.MIN_VALUE
): R {
    return suspendCancellableCoroutine { continuation ->
        val target = object : CustomTarget<R>(width, height), RequestListener<R> {
            override fun onResourceReady(resource: R & Any, transition: Transition<in R>?) {
                // Do nothing, we use the RequestListener version instead
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                // Do nothing, we don't retain a reference to the resource
            }

            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<R>,
                isFirstResource: Boolean
            ): Boolean {
                continuation.resumeWithException(e ?: IOException("Image loading failed"))
                return false
            }

            override fun onResourceReady(
                resource: R & Any,
                model: Any,
                target: Target<R>?,
                dataSource: DataSource,
                isFirstResource: Boolean
            ): Boolean {
                continuation.resume(resource)
                return false
            }
        }
        into(target)
        continuation.invokeOnCancellation { target.request?.clear() }
    }
}
