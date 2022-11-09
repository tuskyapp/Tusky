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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.github.chrisbanes.photoview.PhotoViewAttacher
import com.keylesspalace.tusky.ViewMediaActivity
import com.keylesspalace.tusky.databinding.FragmentViewImageBinding
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.visible
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlin.math.abs

class ViewImageFragment : ViewMediaFragment() {
    interface PhotoActionsListener {
        fun onBringUp()
        fun onDismiss()
        fun onPhotoTap()
    }

    private var _binding: FragmentViewImageBinding? = null
    private val binding get() = _binding!!

    private lateinit var attacher: PhotoViewAttacher
    private lateinit var photoActionsListener: PhotoActionsListener
    private lateinit var toolbar: View
    private var transition = BehaviorSubject.create<Unit>()
    private var shouldStartTransition = false

    // Volatile: Image requests happen on background thread and we want to see updates to it
    // immediately on another thread. Atomic is an overkill for such thing.
    @Volatile
    private var startedTransition = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        photoActionsListener = context as PhotoActionsListener
    }

    override fun setupMediaView(
        url: String,
        previewUrl: String?,
        description: String?,
        showingDescription: Boolean
    ) {
        binding.photoView.transitionName = url
        binding.mediaDescription.text = description
        binding.captionSheet.visible(showingDescription)

        startedTransition = false
        loadImageFromNetwork(url, previewUrl, binding.photoView)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        toolbar = (requireActivity() as ViewMediaActivity).toolbar
        this.transition = BehaviorSubject.create()
        _binding = FragmentViewImageBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
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
            url = arguments.getString(ARG_SINGLE_IMAGE_URL)
            if (url == null) {
                throw IllegalArgumentException("attachment or image url has to be set")
            }
        }

        attacher = PhotoViewAttacher(binding.photoView).apply {
            // This prevents conflicts with ViewPager
            setAllowParentInterceptOnEdge(true)

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

        var lastY = 0f

        binding.photoView.setOnTouchListener { v, event ->
            // This part is for scaling/translating on vertical move.
            // We use raw coordinates to get the correct ones during scaling

            if (event.action == MotionEvent.ACTION_DOWN) {
                lastY = event.rawY
            } else if (event.pointerCount == 1 &&
                attacher.scale == 1f &&
                event.action == MotionEvent.ACTION_MOVE
            ) {
                val diff = event.rawY - lastY
                // This code is to prevent transformations during page scrolling
                // If we are already translating or we reached the threshold, then transform.
                if (binding.photoView.translationY != 0f || abs(diff) > 40) {
                    binding.photoView.translationY += (diff)
                    val scale = (-abs(binding.photoView.translationY) / 720 + 1).coerceAtLeast(0.5f)
                    binding.photoView.scaleY = scale
                    binding.photoView.scaleX = scale
                    lastY = event.rawY
                    return@setOnTouchListener true
                }
            } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                onGestureEnd()
            }
            attacher.onTouch(v, event)
        }

        finalizeViewSetup(url, attachment?.previewUrl, description)
    }

    private fun onGestureEnd() {
        if (_binding == null) {
            return
        }
        if (abs(binding.photoView.translationY) > 180) {
            photoActionsListener.onDismiss()
        } else {
            binding.photoView.animate().translationY(0f).scaleX(1f).scaleY(1f).start()
        }
    }

    private fun onMediaTap() {
        photoActionsListener.onPhotoTap()
    }

    override fun onToolbarVisibilityChange(visible: Boolean) {
        if (_binding == null || !userVisibleHint) {
            return
        }
        isDescriptionVisible = showingDescription && visible
        val alpha = if (isDescriptionVisible) 1.0f else 0.0f
        binding.captionSheet.animate().alpha(alpha)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (_binding != null) {
                        binding.captionSheet.visible(isDescriptionVisible)
                    }
                    animation.removeListener(this)
                }
            })
            .start()
    }

    override fun onDestroyView() {
        Glide.with(this).clear(binding.photoView)
        transition.onComplete()
        _binding = null
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
                    it.thumbnail(
                        glide
                            .load(previewUrl)
                            .dontAnimate()
                            .onlyRetrieveFromCache(true)
                            .centerInside()
                            .addListener(ImageRequestListener(true, isThumbnailRequest = true))
                    )
                else it
            }
            // Request image from the network on fail load image from cache
            .error(
                glide.load(url)
                    .centerInside()
                    .addListener(ImageRequestListener(false, isThumbnailRequest = false))
            )
            .centerInside()
            .addListener(ImageRequestListener(true, isThumbnailRequest = false))
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
        private val isThumbnailRequest: Boolean
    ) : RequestListener<Drawable> {

        override fun onLoadFailed(
            e: GlideException?,
            model: Any,
            target: Target<Drawable>,
            isFirstResource: Boolean
        ): Boolean {
            // If cache for full image failed complete transition
            if (isCacheRequest && !isThumbnailRequest && shouldStartTransition &&
                !startedTransition
            ) {
                photoActionsListener.onBringUp()
            }
            // Hide progress bar only on fail request from internet
            if (!isCacheRequest && _binding != null) binding.progressBar.hide()
            // We don't want to overwrite preview with null when main image fails to load
            return !isCacheRequest
        }

        @SuppressLint("CheckResult")
        override fun onResourceReady(
            resource: Drawable,
            model: Any,
            target: Target<Drawable>,
            dataSource: DataSource,
            isFirstResource: Boolean
        ): Boolean {
            if (_binding != null) {
                binding.progressBar.hide() // Always hide the progress bar on success
            }

            if (!startedTransition || !shouldStartTransition) {
                // Set this right away so that we don't have to concurrent post() requests
                startedTransition = true
                // post() because load() replaces image with null. Sometimes after we set
                // the thumbnail.
                binding.photoView.post {
                    target.onResourceReady(resource, null)
                    if (shouldStartTransition) photoActionsListener.onBringUp()
                }
            } else {
                // This wait for transition. If there's no transition then we should hit
                // another branch. take() will unsubscribe after we have it to not leak memory
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
