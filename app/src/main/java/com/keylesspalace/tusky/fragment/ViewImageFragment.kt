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
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

import com.github.chrisbanes.photoview.PhotoViewAttacher
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.visible
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
    override lateinit var descriptionView: TextView

    override fun onAttach(context: Context) {
        super.onAttach(context)
        photoActionsListener = context as PhotoActionsListener
    }

    override fun setupMediaView(url: String) {
        descriptionView = mediaDescription
        photoView.transitionName = url
        attacher = PhotoViewAttacher(photoView)

        // Clicking outside the photo closes the viewer.
        attacher.setOnOutsidePhotoTapListener { photoActionsListener.onDismiss() }

        attacher.setOnClickListener { onMediaTap() }

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
            Glide.with(this)
                    .load(url)
                    .dontAnimate()
                    .onlyRetrieveFromCache(true)
                    .centerInside()
                    .addListener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                            // if there's no image in cache, load from network and start transition
                            // immediately.
                            if (isAdded) {
                                photoActionsListener.onBringUp()
                                loadImageFromNetwork(url, photoView)
                            }
                            return false
                        }

                        override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                            if (photoView?.isAttachedToWindow == true) {
                                finishLoadingSuccessfully()
                            } else {
                                // if view is not attached yet, wait for an attachment and
                                // start transition when it's finally ready.
                                photoView?.addOnAttachStateChangeListener(
                                        object : View.OnAttachStateChangeListener {
                                            override fun onViewAttachedToWindow(v: View?) {
                                                finishLoadingSuccessfully()
                                                photoView.removeOnAttachStateChangeListener(this)
                                            }

                                            override fun onViewDetachedFromWindow(v: View?) {}
                                        })
                            }
                            return false
                        }

                    })
                    .into(photoView)
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

        finalizeViewSetup(url, description)
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
        super.onDestroyView()
    }

    private fun loadImageFromNetwork(url: String, photoView: ImageView) {
        Handler().post {
            Glide.with(this)
                    .load(url)
                    .dontAnimate()
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .centerInside()
                    .addListener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                            progressBar?.hide()
                            return false
                        }

                        override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                            resource?.let {
                                target?.onResourceReady(resource, null)
                                finishLoadingSuccessfully()
                                return true
                            }
                            progressBar?.hide()
                            return false
                        }
                    })
                    .into(photoView)

        }
    }

    private fun finishLoadingSuccessfully() {
        progressBar?.hide()
        attacher.update()
        photoActionsListener.onBringUp()
    }
}
