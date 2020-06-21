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
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.ViewMediaActivity
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.visible
import com.keylesspalace.tusky.view.ExposedPlayPauseVideoView
import kotlinx.android.synthetic.main.activity_view_media.*
import kotlinx.android.synthetic.main.fragment_view_video.*

class ViewVideoFragment : ViewMediaFragment() {
    private lateinit var toolbar: View
    private val handler = Handler(Looper.getMainLooper())
    private val hideToolbar = Runnable {
        // Hoist toolbar hiding to activity so it can track state across different fragments
        // This is explicitly stored as runnable so that we pass it to the handler later for cancellation
        mediaActivity.onPhotoTap()
        mediaController.hide()
    }
    private lateinit var mediaActivity: ViewMediaActivity
    private val TOOLBAR_HIDE_DELAY_MS = 3000L
    private lateinit var mediaController : MediaController
    private var isAudio = false

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        // Start/pause/resume video playback as fragment is shown/hidden
        super.setUserVisibleHint(isVisibleToUser)
        if (videoView == null) {
            return
        }

        if (isVisibleToUser) {
            if (mediaActivity.isToolbarVisible) {
                handler.postDelayed(hideToolbar, TOOLBAR_HIDE_DELAY_MS)
            }
            videoView.start()
        } else {
            handler.removeCallbacks(hideToolbar)
            videoView.pause()
            mediaController.hide()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun setupMediaView(
            url: String,
            previewUrl: String?,
            description: String?,
            showingDescription: Boolean
    ) {
        mediaDescription.text = description
        mediaDescription.visible(showingDescription)

        videoView.transitionName = url
        videoView.setVideoPath(url)
        mediaController = object : MediaController(mediaActivity) {
            override fun show(timeout: Int) {
                // We're doing manual auto-close management.
                // Also, take focus back from the pause button so we can use the back button.
                super.show(0)
                mediaController.requestFocus()
            }

            override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
                if (event?.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) {
                        hide()
                        activity?.supportFinishAfterTransition()
                    }
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }

        mediaController.setMediaPlayer(videoView)
        videoView.setMediaController(mediaController)
        videoView.requestFocus()
        videoView.setPlayPauseListener(object: ExposedPlayPauseVideoView.PlayPauseListener {
            override fun onPause() {
                handler.removeCallbacks(hideToolbar)
            }
            override fun onPlay() {
                // Audio doesn't cause the controller to show automatically,
                // and we only want to hide the toolbar if it's a video.
                if (isAudio) {
                    mediaController.show()
                } else {
                    hideToolbarAfterDelay(TOOLBAR_HIDE_DELAY_MS)
                }
            }
        })
        videoView.setOnPreparedListener { mp ->
            val containerWidth = videoContainer.measuredWidth.toFloat()
            val containerHeight = videoContainer.measuredHeight.toFloat()
            val videoWidth = mp.videoWidth.toFloat()
            val videoHeight = mp.videoHeight.toFloat()

            if(containerWidth/containerHeight > videoWidth/videoHeight) {
                videoView.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                videoView.layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
            } else {
                videoView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                videoView.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            }

            // Wait until the media is loaded before accepting taps as we don't want toolbar to
            // be hidden until then.
            videoView.setOnTouchListener { _, _ ->
                mediaActivity.onPhotoTap()
                false
            }

            progressBar.hide()
            mp.isLooping = true
            if (requireArguments().getBoolean(ARG_START_POSTPONED_TRANSITION)) {
                videoView.start()
            }
        }

        if (requireArguments().getBoolean(ARG_START_POSTPONED_TRANSITION)) {
            mediaActivity.onBringUp()
        }
    }

    private fun hideToolbarAfterDelay(delayMilliseconds: Long) {
        handler.postDelayed(hideToolbar, delayMilliseconds)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        toolbar = requireActivity().toolbar
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
        isAudio = attachment.type == Attachment.Type.AUDIO
        finalizeViewSetup(url, attachment.previewUrl, attachment.description)
    }

    override fun onToolbarVisibilityChange(visible: Boolean) {
        if (videoView == null || !userVisibleHint) {
            return
        }

        isDescriptionVisible = showingDescription && visible
        val alpha = if (isDescriptionVisible) 1.0f else 0.0f
        if (isDescriptionVisible) {
            // If to be visible, need to make visible immediately and animate alpha
            mediaDescription.alpha = 0.0f
            mediaDescription.visible(isDescriptionVisible)
        }

        mediaDescription.animate().alpha(alpha)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        mediaDescription.visible(isDescriptionVisible)
                        animation.removeListener(this)
                    }
                })
                .start()

        if (visible && videoView.isPlaying && !isAudio) {
            hideToolbarAfterDelay(TOOLBAR_HIDE_DELAY_MS)
        } else {
            handler.removeCallbacks(hideToolbar)
        }
    }

    override fun onTransitionEnd() {
    }
}
