package com.keylesspalace.tusky.util

import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Allows waiting for a Glide request to complete without blocking a background thread.
 */
suspend fun <R> RequestBuilder<R>.submitAsync(
    width: Int = Target.SIZE_ORIGINAL,
    height: Int = Target.SIZE_ORIGINAL
): R {
    return suspendCancellableCoroutine { continuation ->
        val target = addListener(
            object : RequestListener<R> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<R>,
                    isFirstResource: Boolean
                ): Boolean {
                    continuation.resumeWithException(e ?: GlideException("Image loading failed"))
                    return false
                }

                override fun onResourceReady(
                    resource: R & Any,
                    model: Any,
                    target: Target<R>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    if (target?.request?.isComplete == true) {
                        continuation.resume(resource)
                    }
                    return false
                }
            }
        ).submit(width, height)
        continuation.invokeOnCancellation { target.cancel(true) }
    }
}
