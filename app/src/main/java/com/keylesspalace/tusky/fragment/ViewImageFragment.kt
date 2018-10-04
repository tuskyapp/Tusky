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
import android.content.Context
import android.os.Bundle
import android.support.v4.view.ViewCompat
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView

import com.github.chrisbanes.photoview.PhotoViewAttacher
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.ViewMediaActivity
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.squareup.picasso.Callback
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_view_media.*
import kotlinx.android.synthetic.main.fragment_view_image.*

class ViewImageFragment : ViewMediaFragment() {
    interface PhotoActionsListener {
        fun onBringUp()
        fun onDismiss()
        fun onPhotoTap()
    }

    private lateinit var attacher: PhotoViewAttacher
    private lateinit var photoActionsListener: PhotoActionsListener
    private lateinit var toolbar: View

    private var showingDescription = false
    private var isDescriptionVisible = false

    companion object {
        private const val TAG = "ViewImageFragment"
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        photoActionsListener = context as PhotoActionsListener
    }

    override fun setupMediaView(url: String) {
        attacher = PhotoViewAttacher(photoView)

        // Clicking outside the photo closes the viewer.
        attacher.setOnOutsidePhotoTapListener { _ -> photoActionsListener.onDismiss() }

        attacher.setOnClickListener { _ -> onMediaTap() }

        /* A vertical swipe motion also closes the viewer. This is especially useful when the photo
         * mostly fills the screen so clicking outside is difficult. */
        attacher.setOnSingleFlingListener { _, _, velocityX, velocityY ->
            var result = false
            if (Math.abs(velocityY) > Math.abs(velocityX)) {
                photoActionsListener.onDismiss()
                result = true
            }
            result
        }

        // If we are the view to be shown initially...
        if (arguments!!.getBoolean(ViewMediaFragment.ARG_START_POSTPONED_TRANSITION)) {
            // Try to load image from disk.
            Picasso.with(context)
                    .load(url)
                    .noFade()
                    .networkPolicy(NetworkPolicy.OFFLINE)
                    .into(photoView, object : Callback {
                        override fun onSuccess() {
                            // if we loaded image from disk, we should check that view is attached.
                            if (ViewCompat.isAttachedToWindow(photoView)) {
                                finishLoadingSuccessfully()
                            } else {
                                // if view is not attached yet, wait for an attachment and
                                // start transition when it's finally ready.
                                photoView.addOnAttachStateChangeListener(
                                        object : View.OnAttachStateChangeListener {
                                            override fun onViewAttachedToWindow(v: View?) {
                                                finishLoadingSuccessfully()
                                                photoView.removeOnAttachStateChangeListener(this)
                                            }

                                            override fun onViewDetachedFromWindow(v: View?) {}
                                        })
                            }
                        }

                        override fun onError() {
                            // if there's no image in cache, load from network and start transition
                            // immediately.
                            photoActionsListener.onBringUp()
                            loadImageFromNetwork(url, photoView)
                        }
                    })
        } else {
            // if we're not initial page, don't bother.
            loadImageFromNetwork(url, photoView)
        }

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        toolbar = activity!!.toolbar
        return inflater.inflate(R.layout.fragment_view_image, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val arguments = this.arguments!!
        val attachment = arguments.getParcelable<Attachment>(ARG_ATTACHMENT)
        val url: String?

        if (attachment != null) {
            url = attachment.url

            val description = attachment.description

            descriptionView.text = description
            showingDescription = !TextUtils.isEmpty(description)
            isDescriptionVisible = showingDescription
        } else {
            url = arguments.getString(ARG_AVATAR_URL)
            if (url == null) {
                throw IllegalArgumentException("attachment or avatar url has to be set")
            }

            showingDescription = false
            isDescriptionVisible = false
        }

        // Setting visibility without animations so it looks nice when you scroll images
        if (showingDescription && (activity as ViewMediaActivity).isToolbarVisible()) {
            descriptionView.show()
        } else {
            descriptionView.hide()
        }

        setupMediaView(url)

        setupToolbarVisibilityListener()
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
                        if (isDescriptionVisible) {
                            descriptionView.show()
                        } else {
                            descriptionView.hide()
                        }
                        animation.removeListener(this)
                    }
                })
                .start()
    }

    override fun onDetach() {
        super.onDetach()
        Picasso.with(context).cancelRequest(photoView)
    }

    private fun loadImageFromNetwork(url: String, photoView: ImageView) {
        Picasso.with(context)
                .load(url)
                .noPlaceholder()
                .networkPolicy(NetworkPolicy.NO_STORE)
                .into(photoView, object : Callback {
                    override fun onSuccess() {
                        finishLoadingSuccessfully()
                    }

                    override fun onError() {
                        progressBar.hide()
                    }
                })
    }

    private fun finishLoadingSuccessfully() {
        progressBar.hide()
        attacher.update()
        photoActionsListener.onBringUp()
    }
}
