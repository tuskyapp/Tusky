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
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.core.view.GestureDetectorCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.ui.PlayerControlView
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.BuildConfig
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.ViewMediaActivity
import com.keylesspalace.tusky.databinding.FragmentViewVideoBinding
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.util.getErrorString
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.util.visible
import okhttp3.OkHttpClient
import javax.inject.Inject
import kotlin.math.abs

@UnstableApi
class ViewVideoFragment : ViewMediaFragment(), Injectable {
    interface VideoActionsListener {
        fun onDismiss()
    }

    @Inject
    lateinit var okHttpClient: OkHttpClient

    private val binding by viewBinding(FragmentViewVideoBinding::bind)

    private lateinit var videoActionsListener: VideoActionsListener
    private lateinit var toolbar: View
    private val handler = Handler(Looper.getMainLooper())
    private val hideToolbar = Runnable {
        // Hoist toolbar hiding to activity so it can track state across different fragments
        // This is explicitly stored as runnable so that we pass it to the handler later for cancellation
        mediaActivity.onPhotoTap()
    }
    private lateinit var mediaActivity: ViewMediaActivity
    private lateinit var mediaPlayerListener: Player.Listener
    private var isAudio = false

    private lateinit var mediaAttachment: Attachment

    private var player: ExoPlayer? = null

    /** The saved seek position, if the fragment is being resumed */
    private var savedSeekPosition: Long = 0

    private lateinit var mediaSourceFactory: DefaultMediaSourceFactory

    companion object {
        private const val TAG = "ViewVideoFragment"
        private const val TOOLBAR_HIDE_DELAY_MS = PlayerControlView.DEFAULT_SHOW_TIMEOUT_MS
        private const val SEEK_POSITION = "seekPosition"
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(DefaultDataSource.Factory(context, OkHttpDataSource.Factory(okHttpClient)))

        videoActionsListener = context as VideoActionsListener

        val tapDetector = GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent) = true

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    mediaActivity.onPhotoTap()
                    return false
                }
            }
        )

        mediaPlayerListener = object : Player.Listener {
            @SuppressLint("ClickableViewAccessibility")
            @OptIn(UnstableApi::class)
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        // Wait until the media is loaded before accepting taps as we don't want toolbar to
                        // be hidden until then.
                        binding.videoView.setOnTouchListener { _, e: MotionEvent ->
                            tapDetector.onTouchEvent(e)
                            false
                        }

                        binding.progressBar.hide()
                        binding.videoView.useController = true
                        binding.videoView.showController()
                    }
                    else -> { /* do nothing */ }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isAudio) return
                if (isPlaying) {
                    hideToolbarAfterDelay(TOOLBAR_HIDE_DELAY_MS)
                } else {
                    handler.removeCallbacks(hideToolbar)
                }
            }

            /*
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
            */
            override fun onPlayerError(error: PlaybackException) {
                val message = getString(R.string.error_media_playback, error.getErrorString(requireContext()))
                Snackbar.make(binding.root, message, Snackbar.LENGTH_INDEFINITE)
                    .setTextMaxLines(5)
                    .setAction(R.string.action_retry) { player?.prepare() }
                    .show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT > 23) {
            initializePlayer()
            binding.videoView.onResume()
        }
    }

    override fun onResume() {
        super.onResume()

        if (Build.VERSION.SDK_INT <= 23 || player == null) {
            initializePlayer()
            if (mediaActivity.isToolbarVisible && !isAudio) {
                hideToolbarAfterDelay(TOOLBAR_HIDE_DELAY_MS)
            }
            binding.videoView.onResume()
        }
    }

    private fun releasePlayer() {
        player?.let {
            savedSeekPosition = it.currentPosition
            it.release()
            player = null
            binding.videoView.player = null
        }
    }

    override fun onPause() {
        super.onPause()

        // If <= API 23 then multi-window mode is not available, so this is a good time to
        // pause everything
        if (Build.VERSION.SDK_INT <= 23) {
            binding.videoView.onPause()
            releasePlayer()
            handler.removeCallbacks(hideToolbar)
        }
    }

    override fun onStop() {
        super.onStop()

        // If > API 23 then this might be multi-window, and definitely wasn't paused in onPause,
        // so pause everything now.
        if (Build.VERSION.SDK_INT > 23) {
            binding.videoView.onPause()
            releasePlayer()
            handler.removeCallbacks(hideToolbar)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(SEEK_POSITION, savedSeekPosition)
    }

    private fun initializePlayer() {
        if (mediaAttachment.url.isEmpty()) return // TODO: Is this necessary?

        Log.d(TAG, "initializePlayer()")
        ExoPlayer.Builder(requireContext())
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                if (BuildConfig.DEBUG) addAnalyticsListener(EventLogger("$TAG:ExoPlayer"))
                setMediaItem(MediaItem.fromUri(mediaAttachment.url))
                addListener(mediaPlayerListener)
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = true
                seekTo(savedSeekPosition)
                prepare()
                player = this
            }

        binding.videoView.player = player
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun setupMediaView(
        url: String,
        previewUrl: String?,
        description: String?,
        showingDescription: Boolean
    ) {
        binding.mediaDescription.text = description
        binding.mediaDescription.visible(showingDescription)
        binding.mediaDescription.movementMethod = ScrollingMovementMethod()

        // Ensure the description is visible over the video
        binding.mediaDescription.elevation = binding.videoView.elevation + 1

        binding.videoView.transitionName = url

        binding.videoView.requestFocus()

        if (requireArguments().getBoolean(ARG_START_POSTPONED_TRANSITION)) {
            mediaActivity.onBringUp()
        }
    }

    private fun hideToolbarAfterDelay(delayMilliseconds: Int) {
        handler.postDelayed(hideToolbar, delayMilliseconds.toLong())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        mediaActivity = activity as ViewMediaActivity
        toolbar = mediaActivity.toolbar
        return inflater.inflate(R.layout.fragment_view_video, container, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val attachment = arguments?.getParcelable<Attachment>(ARG_ATTACHMENT)
            ?: throw IllegalArgumentException("attachment has to be set")

        val url = attachment.url
        isAudio = attachment.type == Attachment.Type.AUDIO

        val gestureDetector = GestureDetectorCompat(
            requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent): Boolean {
                    return true
                }

                override fun onFling(
                    e1: MotionEvent,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (abs(velocityY) > abs(velocityX)) {
                        videoActionsListener.onDismiss()
                        return true
                    }
                    return false
                }
            }
        )

        var lastY = 0f
        binding.root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                lastY = event.rawY
            } else if (event.pointerCount == 1 && event.action == MotionEvent.ACTION_MOVE) {
                val diff = event.rawY - lastY
                if (binding.videoView.translationY != 0f || abs(diff) > 40) {
                    binding.videoView.translationY += diff
                    val scale = (-abs(binding.videoView.translationY) / 720 + 1).coerceAtLeast(0.5f)
                    binding.videoView.scaleY = scale
                    binding.videoView.scaleX = scale
                    lastY = event.rawY
                    return@setOnTouchListener true
                }
            } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                if (abs(binding.videoView.translationY) > 180) {
                    videoActionsListener.onDismiss()
                } else {
                    binding.videoView.animate().translationY(0f).scaleX(1f).scaleY(1f).start()
                }
            }

            gestureDetector.onTouchEvent(event)
        }

        savedSeekPosition = savedInstanceState?.getLong(SEEK_POSITION) ?: 0

        mediaAttachment = attachment

        finalizeViewSetup(url, attachment.previewUrl, attachment.description)
    }

    override fun onToolbarVisibilityChange(visible: Boolean) {
        if (!userVisibleHint) {
            return
        }

        isDescriptionVisible = showingDescription && visible
        val alpha = if (isDescriptionVisible) 1.0f else 0.0f
        if (isDescriptionVisible) {
            // If to be visible, need to make visible immediately and animate alpha
            binding.mediaDescription.alpha = 0.0f
            binding.mediaDescription.visible(isDescriptionVisible)
        }

        binding.mediaDescription.animate().alpha(alpha)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.mediaDescription.visible(isDescriptionVisible)
                    animation.removeListener(this)
                }
            })
            .start()

        if (visible && (binding.videoView.player?.isPlaying == true) && !isAudio) {
            hideToolbarAfterDelay(TOOLBAR_HIDE_DELAY_MS)
        } else {
            handler.removeCallbacks(hideToolbar)
        }
    }

    override fun onTransitionEnd() {
    }
}
