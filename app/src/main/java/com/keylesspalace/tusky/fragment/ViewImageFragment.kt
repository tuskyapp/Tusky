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
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.databinding.FragmentViewImageBinding
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.util.getParcelableCompat
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.util.visible
import com.ortiz.touchview.OnTouchCoordinatesListener
import com.ortiz.touchview.TouchImageView
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

class ViewImageFragment : ViewMediaFragment() {
    interface PhotoActionsListener {
        fun onBringUp()
        fun onDismiss()
        fun onPhotoTap()
    }

    private val binding by viewBinding(FragmentViewImageBinding::bind)

    private val photoActionsListener: PhotoActionsListener
        get() = requireActivity() as PhotoActionsListener
    private var transition: CompletableDeferred<Unit>? = null
    private var shouldStartTransition = false

    // Volatile: Image requests happen on background thread and we want to see updates to it
    // immediately on another thread. Atomic is an overkill for such thing.
    @Volatile
    private var startedTransition = false

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        this.transition = CompletableDeferred()
        return inflater.inflate(R.layout.fragment_view_image, container, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val arguments = requireArguments()
        val attachment = arguments.getParcelableCompat<Attachment>(ARG_ATTACHMENT)
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

        val descriptionBottomSheet = BottomSheetBehavior.from(binding.captionSheet)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bottomInsets = insets.getInsets(systemBars()).bottom
            val mediaDescriptionBottomPadding = requireContext().resources.getDimensionPixelSize(R.dimen.media_description_sheet_bottom_padding)
            val mediaDescriptionPeekHeight = requireContext().resources.getDimensionPixelSize(R.dimen.media_description_sheet_peek_height)
            val imageViewBottomMargin = requireContext().resources.getDimensionPixelSize(R.dimen.media_image_view_bottom_margin)
            binding.mediaDescription.updatePadding(bottom = mediaDescriptionBottomPadding + bottomInsets)
            descriptionBottomSheet.setPeekHeight(mediaDescriptionPeekHeight + bottomInsets, false)
            binding.photoView.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = imageViewBottomMargin + bottomInsets }
            insets.inset(0, 0, 0, bottomInsets)
        }

        val singleTapDetector = GestureDetector(
            requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent) = true
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (isAdded) {
                        photoActionsListener.onPhotoTap()
                    }
                    return false
                }
            }
        )

        binding.photoView.setOnTouchCoordinatesListener(object : OnTouchCoordinatesListener {
            /** Y coordinate of the last single-finger drag */
            var lastDragY: Float? = null

            override fun onTouchCoordinate(view: View, event: MotionEvent, bitmapPoint: PointF) {
                singleTapDetector.onTouchEvent(event)

                // Two fingers have gone down after a single finger drag. Finish the drag
                if (event.pointerCount == 2 && lastDragY != null) {
                    onGestureEnd(view)
                    lastDragY = null
                }

                // Stop the parent view from handling touches if either (a) the user has 2+
                // fingers on the screen, or (b) the image has been zoomed in, and can be scrolled
                // horizontally in both directions.
                //
                // This stops things like ViewPager2 from trying to intercept a left/right swipe
                // and ensures that the image does not appear to "stick" to the screen as different
                // views fight over who should be handling the swipe.
                //
                // If the view can be scrolled in one direction it's OK to let the parent intercept,
                // which allows the user to swipe between images even if one or more of them have
                // been zoomed in.
                if (event.pointerCount >= 2 || view.canScrollHorizontally(1) && view.canScrollHorizontally(-1)) {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            view.parent.requestDisallowInterceptTouchEvent(true)
                        }

                        MotionEvent.ACTION_UP -> {
                            view.parent.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    return
                }

                // The user is dragging the image around
                if (event.pointerCount == 1) {
                    // If the image is zoomed then the swipe-to-dismiss functionality is disabled
                    if ((view as TouchImageView).isZoomed) return

                    // The user's finger just went down, start recording where they are dragging from
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        lastDragY = event.rawY
                        return
                    }

                    // The user is dragging the un-zoomed image to possibly fling it up or down
                    // to dismiss.
                    if (event.action == MotionEvent.ACTION_MOVE) {
                        // lastDragY may be null; e.g., the user was performing a two-finger drag,
                        // and has lifted one finger. In this case do nothing
                        lastDragY ?: return

                        // Compute the Y offset of the drag, and scale/translate the photoview
                        // accordingly.
                        val diff = event.rawY - lastDragY!!
                        if (view.translationY != 0f || abs(diff) > 40) {
                            // Drag has definitely started, stop the parent from interfering
                            view.parent.requestDisallowInterceptTouchEvent(true)
                            view.translationY += diff
                            val scale = (-abs(view.translationY) / 720 + 1).coerceAtLeast(0.5f)
                            view.scaleY = scale
                            view.scaleX = scale
                            lastDragY = event.rawY
                        }
                        return
                    }

                    // The user has finished dragging. Allow the parent to handle touch events if
                    // appropriate, and end the gesture.
                    if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                        view.parent.requestDisallowInterceptTouchEvent(false)
                        if (lastDragY != null) onGestureEnd(view)
                        lastDragY = null
                        return
                    }
                }
            }

            /**
             * Handle the end of the user's gesture.
             *
             * If the user was previously dragging, and the image has been dragged a sufficient
             * distance then we are done. Otherwise, animate the image back to its starting position.
             */
            private fun onGestureEnd(view: View) {
                if (abs(view.translationY) > 180) {
                    photoActionsListener.onDismiss()
                } else {
                    view.animate().translationY(0f).scaleX(1f).scaleY(1f).start()
                }
            }
        })

        finalizeViewSetup(url, attachment?.previewUrl, description)
    }

    override fun onToolbarVisibilityChange(visible: Boolean) {
        if (view == null) return

        isDescriptionVisible = showingDescription && visible
        val alpha = if (isDescriptionVisible) 1.0f else 0.0f
        binding.captionSheet.animate().alpha(alpha)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view ?: return
                    binding.captionSheet.visible(isDescriptionVisible)
                    animation.removeListener(this)
                }
            })
            .start()
    }

    override fun onDestroyView() {
        transition = null
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
                if (previewUrl != null) {
                    it.thumbnail(
                        glide
                            .load(previewUrl)
                            .dontAnimate()
                            .onlyRetrieveFromCache(true)
                            .centerInside()
                            .addListener(ImageRequestListener(true, isThumbnailRequest = true))
                    )
                } else {
                    it
                }
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
            model: Any?,
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
            if (!isCacheRequest) binding.progressBar.hide()
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
            binding.progressBar.hide() // Always hide the progress bar on success

            if (!startedTransition || !shouldStartTransition) {
                // Set this right away so that we don't have to concurrent post() requests
                startedTransition = true
                // post() because load() replaces image with null. Sometimes after we set
                // the thumbnail.
                binding.photoView.post {
                    if (isAdded) {
                        target.onResourceReady(resource, null)
                        if (shouldStartTransition) photoActionsListener.onBringUp()
                    }
                }
            } else {
                // This waits for transition. If there's no transition then we should hit
                // another branch. When the view is destroyed the coroutine is automatically canceled.
                transition?.let {
                    viewLifecycleOwner.lifecycleScope.launch {
                        it.await()
                        target.onResourceReady(resource, null)
                    }
                }
            }
            return true
        }
    }

    override fun onTransitionEnd() {
        this.transition?.complete(Unit)
    }
}
