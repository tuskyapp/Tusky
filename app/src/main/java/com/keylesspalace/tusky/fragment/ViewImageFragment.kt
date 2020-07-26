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
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.TextView
import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.github.piasy.biv.BigImageViewer
import com.github.piasy.biv.loader.ImageLoader
import com.github.piasy.biv.view.GlideImageViewFactory
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.visible
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.activity_view_media.*
import kotlinx.android.synthetic.main.fragment_view_image.*
import java.io.File
import java.lang.Exception
import kotlin.math.abs
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView


class ViewImageFragment : ViewMediaFragment() {
    interface PhotoActionsListener {
        fun onBringUp()
        fun onDismiss()
        fun onPhotoTap()
    }

    private lateinit var photoActionsListener: PhotoActionsListener
    private lateinit var toolbar: View
    private var shouldStartTransition = false

    // Volatile: Image requests happen on background thread and we want to see updates to it
    // immediately on another thread. Atomic is an overkill for such thing.
    @Volatile
    private var startedTransition = false

    private var uri = Uri.EMPTY
    private var previewUri = Uri.EMPTY
    private var showingPreview = false

    override lateinit var descriptionView: TextView
    override fun onAttach(context: Context) {
        super.onAttach(context)
        photoActionsListener = context as PhotoActionsListener
    }

    override fun setupMediaView(url: String, previewUrl: String?) {
        descriptionView = mediaDescription
        photoView.transitionName = url
        startedTransition = false
        uri = Uri.parse(url)
        if(previewUrl != null && !previewUrl.equals(url)) {
            previewUri = Uri.parse(previewUrl)
        }
        loadImageFromNetwork()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        toolbar = requireActivity().toolbar
        return inflater.inflate(R.layout.fragment_view_image, container, false)
    }

    private lateinit var gestureDetector : GestureDetector

    private val imageOnTouchListener = object : View.OnTouchListener {
        private var lastY = 0.0f
        private var swipeStartedWithOneFinger = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            // This part is for scaling/translating on vertical move.
            // We use raw coordinates to get the correct ones during scaling
            gestureDetector.onTouchEvent(event)

            if(event.pointerCount != 1) {
                onGestureEnd()
                swipeStartedWithOneFinger = false
                return false
            }

            when(event.action) {
                MotionEvent.ACTION_DOWN -> {
                    swipeStartedWithOneFinger = true
                    lastY = event.rawY
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    onGestureEnd()
                    swipeStartedWithOneFinger = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if(swipeStartedWithOneFinger &&
                            (photoView.ssiv == null || photoView.ssiv.scale <= photoView.ssiv.minScale)) {
                        val diff = event.rawY - lastY
                        // This code is to prevent transformations during page scrolling
                        // If we are already translating or we reached the threshold, then transform.
                        if (photoView.translationY != 0f || abs(diff) > 40) {
                            photoView.translationY += (diff)
                            val scale = (-abs(photoView.translationY) / 720 + 1).coerceAtLeast(0.5f)
                            photoView.scaleY = scale
                            photoView.scaleX = scale
                            lastY = event.rawY
                        }
                    }
                }
            }

            return false
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
                onMediaTap()
                return true
            }
        })

        photoView.setImageLoaderCallback(imageLoaderCallback)
        photoView.setImageViewFactory(GlideImageViewFactory())

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

    private fun onGestureEnd() {
        if (abs(photoView.translationY) > 180) {
            photoActionsListener.onDismiss()
        } else {
            photoView.animate().translationY(0f).scaleX(1f).scaleY(1f).start()
        }
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
        super.onDestroyView()
        photoView.ssiv?.recycle()
    }

    private inner class DummyCacheTarget(val ctx: Context, val requestPreview : Boolean) : CustomTarget<File>() {
        override fun onLoadCleared(placeholder: Drawable?) {}
        override fun onLoadFailed(errorDrawable: Drawable?) {
            if(requestPreview) {
                // no preview, no full image in cache, load full image
                // forget about fancy transition
                showingPreview = false
                photoView.showImage(uri)
            } else {
                // let's start downloading full image that we supposedly don't have
                BigImageViewer.prefetch(uri)

                // meanwhile poke cache about preview image
                Glide.with(ctx).asFile()
                        .load(previewUri)
                        .dontAnimate()
                        .onlyRetrieveFromCache(true)
                        .into(DummyCacheTarget(ctx, true))
            }
        }

        override fun onResourceReady(resource: File, transition: Transition<in File>?) {
            showingPreview = requestPreview
            if(requestPreview) {
                // have preview cached but not full image
                photoView.showImage(previewUri, uri, true)
            } else {
                photoView.showImage(uri)
            }
        }
    }

    private fun loadImageFromNetwork() {
        if(previewUri != Uri.EMPTY) {
            // check if we have full image in the cache, if yes, use it
            // if not, look for preview in cache and use it if available
            // if not, load full image anyway
            Glide.with(this).asFile()
                    .load(uri)
                    .onlyRetrieveFromCache(true)
                    .dontAnimate()
                    .into(DummyCacheTarget(context!!, false))
        } else {
            // no need in cache lookup, just load full image
            showingPreview = false
            photoView.showImage(uri)
        }
    }

    override fun onTransitionEnd() {
        // if we had preview, load full image, as transition has ended
        if (showingPreview) {
            showingPreview = false
            photoView.loadMainImageNow()
        }
    }

    private val imageLoaderCallback = object : ImageLoader.Callback {
        override fun onSuccess(image: File?) {
            if(!showingPreview) {
                progressBar?.hide()
                photoView.ssiv?.orientation = SubsamplingScaleImageView.ORIENTATION_USE_EXIF
                photoView.mainView?.setOnTouchListener(imageOnTouchListener)
            }
        }

        override fun onFail(error: Exception?) {
            progressBar?.hide()
        }

        override fun onCacheHit(imageType: Int, image: File?) {
            // image is here, bring up the activity!
            photoActionsListener.onBringUp()
        }

        override fun onStart() {
            // cache miss but image is downloading, bring up the activity
            photoActionsListener.onBringUp()
        }

        override fun onCacheMiss(imageType: Int, image: File?) {
            // this callback is useless because it's called after
            // image is downloaded or pulled from cache
            // so in case of cache miss, onStart is used
        }

        override fun onFinish() {}
        override fun onProgress(progress: Int) {
            // TODO: make use of it :)
        }
    }
}
