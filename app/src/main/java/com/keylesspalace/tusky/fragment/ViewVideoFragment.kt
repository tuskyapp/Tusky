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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.view.ViewCompat
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView

import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.ViewMediaActivity
import com.keylesspalace.tusky.entity.Attachment

import java.util.Objects

class ViewVideoFragment : ViewMediaFragment() {
    private lateinit var rootView: View
    private var videoView: VideoView? = null
    private lateinit var progressBar: View
    private lateinit var toolbar: View
    private lateinit var descriptionView: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val hideToolbar = Runnable {
        // Hoist toolbar hiding to activity so it can track state across different fragments
        // This is explicitly stored as runnable so that we pass it to the handler later for cancellation
        mediaActivity.onPhotoTap()
    }
    private lateinit var mediaActivity: ViewMediaActivity
    private val TOOLBAR_HIDE_DELAY_MS = 3000L

    private var showingDescription = false
    private var isDescriptionVisible = false

    companion object {
        private const val TAG = "ViewVideoFragment"
    }

    private fun setupViews(inflater: LayoutInflater, container: ViewGroup?) {
        rootView = inflater.inflate(R.layout.fragment_view_video, container, false)
        videoView = rootView.findViewById(R.id.video_player)
        progressBar = rootView.findViewById<View>(R.id.view_media_progress)
        toolbar = activity!!.findViewById<View>(R.id.toolbar)
        descriptionView = rootView.findViewById(R.id.tv_media_description)
        mediaActivity = activity as ViewMediaActivity
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        // Start/pause/resume video playback as fragment is shown/hidden
        super.setUserVisibleHint(isVisibleToUser)
        if (videoView == null) {
            return
        }

        if (isVisibleToUser) {
            if (mediaActivity.isToolbarVisible()) {
                handler.postDelayed(hideToolbar, TOOLBAR_HIDE_DELAY_MS)
            }
            videoView?.start()
        } else {
            handler.removeCallbacks(hideToolbar)
            videoView?.pause()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun setupMediaView(url: String) {
        val videoView = videoView as VideoView
        videoView.setVideoPath(url)
        val controller = MediaController(mediaActivity)
        controller.setMediaPlayer(videoView)
        videoView.setMediaController(controller)
        videoView.requestFocus()
        videoView.setOnTouchListener { _, _ ->
            mediaActivity.onPhotoTap()
            false
        }
        videoView.setOnPreparedListener { mp ->
            progressBar.visibility = View.GONE
            mp.isLooping = true
            if (arguments!!.getBoolean(ViewMediaFragment.ARG_START_POSTPONED_TRANSITION)) {
                hideToolbarAfterDelay(TOOLBAR_HIDE_DELAY_MS)
                videoView.start()
            }
        }

        if (arguments!!.getBoolean(ViewMediaFragment.ARG_START_POSTPONED_TRANSITION)) {
            mediaActivity.onBringUp()
        }
    }

    private fun hideToolbarAfterDelay(delayMilliseconds: Long) {
        handler.postDelayed(hideToolbar , delayMilliseconds)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val arguments = Objects.requireNonNull(this.arguments, "Empty arguments")
        val attachment = arguments!!.getParcelable<Attachment>(ViewMediaFragment.ARG_ATTACHMENT)
        val url: String

        setupViews(inflater, container)

        if(attachment == null) {
            throw IllegalArgumentException("attachment has to be set")
        }
        url = attachment.url
        val description = attachment.description
        descriptionView.text = description
        showingDescription = !TextUtils.isEmpty(description)
        isDescriptionVisible = showingDescription

        // Setting visibility without animations so it looks nice when you scroll media
        //noinspection ConstantConditions
        descriptionView.visibility = if (showingDescription && mediaActivity.isToolbarVisible()) {
            View.VISIBLE
        } else {
            View.GONE
        }

        ViewCompat.setTransitionName(videoView!!, url)

        setupMediaView(url)

        setupToolbarVisibilityListener()

        return rootView
    }

    override fun onToolbarVisibilityChange(visible: Boolean) {
        if (videoView == null || !userVisibleHint) {
            return
        }

        isDescriptionVisible = showingDescription && visible
        val visibility = if(isDescriptionVisible){ View.VISIBLE } else { View.INVISIBLE }
        val alpha = if(isDescriptionVisible){ 1.0f } else { 0.0f }
        descriptionView.animate().alpha(alpha)
                .setListener(object: AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        descriptionView.visibility = visibility
                        animation.removeListener(this)
                    }
                })
                .start()

        if (visible) {
            hideToolbarAfterDelay(TOOLBAR_HIDE_DELAY_MS)
        } else {
            handler.removeCallbacks(hideToolbar)
        }
    }
}
