/* Copyright 2017 Andrew Dawson
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

package com.keylesspalace.tusky.fragment

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.github.chrisbanes.photoview.PhotoViewAttacher
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.visible
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.activity_view_media.*
import kotlinx.android.synthetic.main.fragment_view_image.*
import kotlin.math.abs

class ViewImageFragment : ViewMediaFragment() {
    interface PhotoActionsListener {
        fun onBringUp()
        fun onDismiss()
        fun onPhotoTap()
    }

    private lateinit var attacher: PhotoViewAttacher
    private lateinit var photoActionsListener: PhotoActionsListener
    private lateinit var toolbar: View
    private var transition = BehaviorSubject.create<Unit>()
    private var shouldStartTransition = false
    // Volatile: Image requests happen on background thread and we want to see updates to it
    // immediately on another thread. Atomic is an overkill for such thing.
    @Volatile
    private var startedTransition = false

    override lateinit var descriptionView: TextView
    override fun onAttach(context: Context) {
        super.onAttach(context)
        photoActionsListener = context as PhotoActionsListener
    }

    override fun setupMediaView(url: String, previewUrl: String?) {
        descriptionView = mediaDescription
        photoView.transitionName = url
        attacher = PhotoViewAttacher(photoView).apply {
            // Clicking outside the photo closes the viewer.
            setOnOutsidePhotoTapListener { photoActionsListener.onDismiss() }
            setOnClickListener { onMediaTap() }

            /* A vertical swipe motion also closes the viewer. This is especially useful when the photo
             * mostly fills the screen so clicking outside is difficult. */
            setOnSingleFlingListener { _, _, velocityX, velocityY ->
                var result = false
                if (abs(velocityY) > abs(velocityX)) {
                    photoActionsListener.onDismiss()
                    result = true
                }
                result
            }
        }

        startedTransition = false
        loadImageFromNetwork(url, previewUrl, photoView)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        toolbar = requireActivity().toolbar
        this.transition = BehaviorSubject.create()
        return inflater.inflate(R.layout.fragment_view_image, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val arguments = this.requireArguments()
        val attachment = arguments.getParcelable<Attachment>(ARG_ATTACHMENT)
        this.shouldStartTransition = arguments.getBoolean(ARG_START_POSTPONED_TRANSITION)
        val url: String?
        var description: String? = null

        if (attachment != null) {
            url = attachment.url
            description = attachment.description
        } else {
            url = arguments.getString(ARG_AVATAR_URL)
            if (url == null) {
                throw IllegalArgumentException("attachment or avatar url has to be set")
            }
        }

        finalizeViewSetup(url, attachment?.previewUrl, description)
    }

    private fun onMediaTap() {
        photoActionsListener.onPhotoTap()
    }

    override fun onToolbarVisibilityChange(visible: Boolean) {
        if (photoView == null || !userVisibleHint) {
            return
        }
        isDescriptionVisible = showingDescription && visible
        val alpha = if (isDescriptionVisible) 1.0f else 0.0f
        descriptionView.animate().alpha(alpha)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        descriptionView.visible(isDescriptionVisible)
                        animation.removeListener(this)
                    }
                })
                .start()
    }

    override fun onDestroyView() {
        Glide.with(this).clear(photoView)
        transition.onComplete()
        super.onDestroyView()
    }

    private fun loadImageFromNetwork(url: String, previewUrl: String?, photoView: ImageView) {
        val glide = Glide.with(this)
        // Request image from the any cache
        glide
                .load(url)
                .dontAnimate()
                .onlyRetrieveFromCache(true)
                .let {
                    if (previewUrl != null)
                        it.thumbnail(glide
                                .load(previewUrl)
                                .dontAnimate()
                                .onlyRetrieveFromCache(true)
                                .centerInside()
                                .addListener(ImageRequestListener(true, isThumnailRequest = true)))
                    else it
                }
                //Request image from the network on fail load image from cache
                .error(glide.load(url)
                        .centerInside()
                        .addListener(ImageRequestListener(false, isThumnailRequest = false))
                )
                .centerInside()
                .addListener(ImageRequestListener(true, isThumnailRequest = false))
                .into(photoView)
    }

    /**
     * We start transition as soon as we think reasonable but we must take care about couple of
     * things>
     *  - Do not change image in the middle of transition. It messes up the view.
     *  - Do not transition for the views which don't require it. Starting transition from
     *      multiple fragments does weird things
     *  - Do not wait to transition until the image loads from network
     *
     * Preview, cached image, network image, x - failed, o - succeeded
     * P C N - start transition after...
     * x x x - the cache fails
     * x x o - the cache fails
     * x o o - the cache succeeds
     * o x o - the preview succeeds. Do not start on cache.
     * o o o - the preview succeeds. Do not start on cache.
     *
     * So start transition after the first success or after anything with the cache
     *
     * @param isCacheRequest - is this listener for request image from cache or from the network
     */
    private inner class ImageRequestListener(
            private val isCacheRequest: Boolean,
            private val isThumnailRequest: Boolean) : RequestListener<Drawable> {

        override fun onLoadFailed(e: GlideException?, model: Any, target: Target<Drawable>,
                                  isFirstResource: Boolean): Boolean {
            // If cache for full image failed complete transition
            if (isCacheRequest && !isThumnailRequest && shouldStartTransition
                    && !startedTransition) {
                photoActionsListener.onBringUp()
            }
            // Hide progress bar only on fail request from internet
            if (!isCacheRequest) progressBar?.hide()
            // We don't want to overwrite preview with null when main image fails to load
            return !isCacheRequest
        }

        @SuppressLint("CheckResult")
        override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>,
                                     dataSource: DataSource, isFirstResource: Boolean): Boolean {
            progressBar?.hide() // Always hide the progress bar on success

            if (!startedTransition || !shouldStartTransition) {
                // Set this right away so that we don't have to concurrent post() requests
                startedTransition = true
                // post() because load() replaces image with null. Sometimes after we set
                // the thumbnail.
                photoView.post {
                    target.onResourceReady(resource, null)
                    if (shouldStartTransition) photoActionsListener.onBringUp()
                }
            } else {
                // This wait for transition. If there's no transition then we should hit
                // another branch. take() will unsubscribe after we have it to not leak menmory
                transition
                        .take(1)
                        .subscribe {
                            target.onResourceReady(resource, null)
                            // It's needed. Don't ask why, I don't know, setImageDrawable() should
                            // do it by itself but somehow it doesn't work automatically.
                            // Just do it. If you don't, image will jump around when touched.
                            attacher.update()
                        }
            }
            return true
        }
    }

    override fun onTransitionEnd() {
        this.transition.onNext(Unit)
    }
}
