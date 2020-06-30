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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
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


class ViewImageFragment : ViewMediaFragment(), ImageLoader.Callback, View.OnTouchListener {
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

    override lateinit var descriptionView: TextView
    override fun onAttach(context: Context) {
        super.onAttach(context)
        photoActionsListener = context as PhotoActionsListener
    }

    override fun setupMediaView(url: String, previewUrl: String?) {
        descriptionView = mediaDescription
        photoView.transitionName = url
        startedTransition = false
        loadImageFromNetwork(url, previewUrl)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        toolbar = requireActivity().toolbar
        return inflater.inflate(R.layout.fragment_view_image, container, false)
    }

    private var lastY = 0.0f
    private var swipeStartedWithOneFinger = false
    private lateinit var gestureDetector : GestureDetector

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        // This part is for scaling/translating on vertical move.
        // We use raw coordinates to get the correct ones during scaling
        gestureDetector.onTouchEvent(event)

        if(event.pointerCount != 1) {
            swipeStartedWithOneFinger = false
            return false
        }

        var result = false

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
                if(swipeStartedWithOneFinger && photoView.ssiv.scale <= photoView.ssiv.minScale) {
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
                    result = true
                }
            }
        }

        return result
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

        // photoView.setOnTouchListener(this)
        photoView.setImageLoaderCallback(this)
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

    private fun loadImageFromNetwork(url: String, previewUrl: String?) {
        photoView.showImage(Uri.parse(previewUrl), Uri.parse(url))
    }

    override fun onSuccess(image: File?) {
        progressBar?.hide() // Always hide the progress bar on success
        photoActionsListener.onBringUp()
        photoView.ssiv?.setOnTouchListener(this)
    }

    override fun onFail(error: Exception?) {
        progressBar?.hide()
        photoActionsListener.onBringUp()
    }

    override fun onCacheHit(imageType: Int, image: File?) {
    }

    override fun onCacheMiss(imageType: Int, image: File?) {
    }

    override fun onFinish() {
    }

    override fun onProgress(progress: Int) {
    }

    override fun onTransitionEnd() {
    }
}
