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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.TextView

import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.ViewMediaActivity
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.visible
import kotlinx.android.synthetic.main.activity_view_media.*
import kotlinx.android.synthetic.main.fragment_view_video.*

class ViewVideoFragment : ViewMediaFragment() {
    private lateinit var toolbar: View
    private val handler = Handler(Looper.getMainLooper())
    private val hideToolbar = Runnable {
        // Hoist toolbar hiding to activity so it can track state across different fragments
        // This is explicitly stored as runnable so that we pass it to the handler later for cancellation
        mediaActivity.onPhotoTap()
    }
    private lateinit var mediaActivity: ViewMediaActivity
    private val TOOLBAR_HIDE_DELAY_MS = 3000L
    override lateinit var descriptionView : TextView
    private lateinit var mediaController : MediaController

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        // Start/pause/resume video playback as fragment is shown/hidden
        super.setUserVisibleHint(isVisibleToUser)
        if (videoPlayer == null) {
            return
        }

        if (isVisibleToUser) {
            if (mediaActivity.isToolbarVisible()) {
                handler.postDelayed(hideToolbar, TOOLBAR_HIDE_DELAY_MS)
            }
            videoPlayer.start()
        } else {
            handler.removeCallbacks(hideToolbar)
            videoPlayer.pause()
            mediaController.hide()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun setupMediaView(url: String) {
        descriptionView = mediaDescription
        val videoView = videoPlayer
        videoView.transitionName = url
        videoView.setVideoPath(url)
        mediaController = MediaController(mediaActivity)
        mediaController.setMediaPlayer(videoPlayer)
        videoPlayer.setMediaController(mediaController)
        videoView.requestFocus()
        videoView.setOnTouchListener { _, _ ->
            mediaActivity.onPhotoTap()
            false
        }
        videoView.setOnPreparedListener { mp ->
            progressBar.hide()
            mp.isLooping = true
            if (arguments!!.getBoolean(ARG_START_POSTPONED_TRANSITION)) {
                hideToolbarAfterDelay(TOOLBAR_HIDE_DELAY_MS)
                videoView.start()
            }
        }

        if (arguments!!.getBoolean(ARG_START_POSTPONED_TRANSITION)) {
            mediaActivity.onBringUp()
        }
    }

    private fun hideToolbarAfterDelay(delayMilliseconds: Long) {
        handler.postDelayed(hideToolbar, delayMilliseconds)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        toolbar = activity!!.toolbar
        mediaActivity = activity as ViewMediaActivity
        return inflater.inflate(R.layout.fragment_view_video, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val attachment = arguments?.getParcelable<Attachment>(ARG_ATTACHMENT)
        val url: String

        if (attachment == null) {
            throw IllegalArgumentException("attachment has to be set")
        }
        url = attachment.url
        finalizeViewSetup(url, attachment.description)
    }

    override fun onToolbarVisibilityChange(visible: Boolean) {
        if (videoPlayer == null || !userVisibleHint) {
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

        if (visible) {
            hideToolbarAfterDelay(TOOLBAR_HIDE_DELAY_MS)
        } else {
            handler.removeCallbacks(hideToolbar)
        }
    }
}
