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
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.OptIn
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.BuildConfig
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.ViewMediaActivity
import com.keylesspalace.tusky.databinding.FragmentViewVideoBinding
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.util.getParcelableCompat
import com.keylesspalace.tusky.util.unsafeLazy
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.util.visible
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.abs

@AndroidEntryPoint
@OptIn(UnstableApi::class)
class ViewVideoFragment : ViewMediaFragment() {
    interface VideoActionsListener {
        fun onDismiss()
    }

    @Inject
    lateinit var playerProvider: Provider<ExoPlayer>

    private val binding by viewBinding(FragmentViewVideoBinding::bind)

    private val videoActionsListener: VideoActionsListener
        get() = requireActivity() as VideoActionsListener
    private val handler = Handler(Looper.getMainLooper())
    private val hideToolbar = Runnable {
        // Hoist toolbar hiding to activity so it can track state across different fragments
        // This is explicitly stored as runnable so that we pass it to the handler later for cancellation
        mediaActivity.onPhotoTap()
    }
    private val mediaActivity: ViewMediaActivity
        get() = requireActivity() as ViewMediaActivity
    private val isAudio
        get() = mediaAttachment.type == Attachment.Type.AUDIO

    private val mediaAttachment: Attachment by unsafeLazy {
        arguments?.getParcelableCompat<Attachment>(ARG_ATTACHMENT)
            ?: throw IllegalArgumentException("attachment has to be set")
    }

    private var player: ExoPlayer? = null

    /** The saved seek position, if the fragment is being resumed */
    private var savedSeekPosition: Long = 0

    /** Have we received at least one "READY" event? */
    private var haveStarted = false

    /** Is there a pending autohide? (We can't rely on Android's tracking because that clears on suspend.) */
    private var pendingHideToolbar = false

    /** Prevent the next play start from queueing a toolbar hide. */
    private var suppressNextHideToolbar = false

    @SuppressLint("PrivateResource", "MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = inflater.inflate(R.layout.fragment_view_video, container, false)

        // Move the controls to the bottom of the screen, with enough bottom margin to clear the seekbar
        val controls = rootView.findViewById<LinearLayout>(
            androidx.media3.ui.R.id.exo_center_controls
        )
        val layoutParams = controls.layoutParams as FrameLayout.LayoutParams
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        layoutParams.bottomMargin = rootView.context.resources.getDimension(androidx.media3.ui.R.dimen.exo_styled_bottom_bar_height)
            .toInt()
        controls.layoutParams = layoutParams

        return rootView
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.mediaDescriptionScrollView) { captionSheet, insets ->
            val systemBarInsets = insets.getInsets(systemBars())
            captionSheet.updatePadding(bottom = systemBarInsets.bottom)
            binding.videoView.updateLayoutParams<ConstraintLayout.LayoutParams> {
                bottomMargin = systemBarInsets.bottom
            }

            insets.inset(0, 0, 0, systemBarInsets.bottom)
        }

        /**
         * Handle single taps, flings, and dragging
         */
        val touchListener = object : View.OnTouchListener {
            var lastY = 0f

            /** The view that contains the playing content */
            // binding.videoView is fullscreen, and includes the controls, so don't use that
            // when scaling in response to the user dragging on the screen
            val contentFrame = binding.videoView.findViewById<AspectRatioFrameLayout>(
                androidx.media3.ui.R.id.exo_content_frame
            )

            /** Handle taps and flings */
            val simpleGestureDetector = GestureDetector(
                requireContext(),
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent) = true

                    /** A single tap should show/hide the media description */
                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        mediaActivity.onPhotoTap()
                        return true // Do not pass gestures through to media3
                    }

                    /** A fling up/down should dismiss the fragment */
                    override fun onFling(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        velocityX: Float,
                        velocityY: Float
                    ): Boolean {
                        if (abs(velocityY) > abs(velocityX)) {
                            videoActionsListener.onDismiss()
                            return true
                        }
                        return true // Do not pass gestures through to media3
                    }
                }
            )

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                // Track movement, and scale / translate the video display accordingly
                if (event.action == MotionEvent.ACTION_DOWN) {
                    lastY = event.rawY
                } else if (event.pointerCount == 1 && event.action == MotionEvent.ACTION_MOVE) {
                    val diff = event.rawY - lastY
                    if (contentFrame.translationY != 0f || abs(diff) > 40) {
                        contentFrame.translationY += diff
                        val scale = (-abs(contentFrame.translationY) / 720 + 1).coerceAtLeast(0.5f)
                        contentFrame.scaleY = scale
                        contentFrame.scaleX = scale
                        lastY = event.rawY
                    }
                } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                    if (abs(contentFrame.translationY) > 180) {
                        videoActionsListener.onDismiss()
                    } else {
                        contentFrame.animate().translationY(0f).scaleX(1f).scaleY(1f).start()
                    }
                }

                simpleGestureDetector.onTouchEvent(event)

                // Do not pass gestures through to media3
                // We have to do this because otherwise taps to hide will be double-handled and media3 will re-show itself
                // media3 has a property to disable "hide on tap" but "show on tap" is unconditional
                return true
            }
        }

        val mediaPlayerListener = object : Player.Listener {
            @SuppressLint("ClickableViewAccessibility", "SyntheticAccessor")
            @OptIn(UnstableApi::class)
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        if (!haveStarted) {
                            // Wait until the media is loaded before accepting taps as we don't want toolbar to
                            // be hidden until then.
                            binding.videoView.setOnTouchListener(touchListener)

                            binding.progressBar.hide()
                            binding.videoView.useController = true
                            binding.videoView.showController()
                            haveStarted = true
                        } else {
                            // This isn't a real "done loading"; this is a resume event after backgrounding.
                            if (mediaActivity.isToolbarVisible) {
                                // Before suspend, the toolbar/description were visible, so description is visible already.
                                // But media3 will have automatically hidden the video controls on suspend, so we need to match the description state.
                                binding.videoView.showController()
                                if (!pendingHideToolbar) {
                                    suppressNextHideToolbar = true // The user most recently asked us to show the toolbar, so don't hide it when play starts.
                                }
                            } else {
                                mediaActivity.onPhotoTap()
                            }
                        }
                    }
                    else -> { /* do nothing */ }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isAudio) return
                if (isPlaying) {
                    if (suppressNextHideToolbar) {
                        suppressNextHideToolbar = false
                    } else {
                        hideToolbarAfterDelay(TOOLBAR_HIDE_DELAY_MS)
                    }
                } else {
                    handler.removeCallbacks(hideToolbar)
                }
            }

            @SuppressLint("SyntheticAccessor")
            override fun onPlayerError(error: PlaybackException) {
                binding.progressBar.hide()
                val message = getString(
                    R.string.error_media_playback,
                    error.cause?.message ?: error.message
                )
                Snackbar.make(binding.root, message, Snackbar.LENGTH_INDEFINITE)
                    .setTextMaxLines(10)
                    .setAction(R.string.action_retry) { player?.prepare() }
                    .show()
            }
        }

        savedSeekPosition = savedInstanceState?.getLong(SEEK_POSITION) ?: 0

        val attachment = mediaAttachment
        finalizeViewSetup(attachment.url, attachment.previewUrl, attachment.description)

        // Lifecycle callbacks
        viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                initializePlayer(mediaPlayerListener)
                binding.videoView.onResume()
            }

            override fun onStop(owner: LifecycleOwner) {
                // This might be multi-window, so pause everything now.
                binding.videoView.onPause()
                releasePlayer()
                handler.removeCallbacks(hideToolbar)
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(SEEK_POSITION, savedSeekPosition)
    }

    private fun initializePlayer(mediaPlayerListener: Player.Listener) {
        player = playerProvider.get().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(if (isAudio) C.AUDIO_CONTENT_TYPE_UNKNOWN else C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            if (BuildConfig.DEBUG) addAnalyticsListener(EventLogger("$TAG:ExoPlayer"))
            setMediaItem(MediaItem.fromUri(mediaAttachment.url))
            addListener(mediaPlayerListener)
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            seekTo(savedSeekPosition)
            prepare()
        }

        binding.videoView.player = player

        // Audio-only files might have a preview image. If they do, set it as the artwork
        if (isAudio) {
            mediaAttachment.previewUrl?.let { url ->
                Glide.with(this)
                    .load(url)
                    .into(
                        object : CustomViewTarget<PlayerView, Drawable>(binding.videoView) {
                            override fun onLoadFailed(errorDrawable: Drawable?) {
                                // Don't do anything
                            }

                            override fun onResourceCleared(placeholder: Drawable?) {
                                view.defaultArtwork = null
                            }

                            override fun onResourceReady(
                                resource: Drawable,
                                transition: Transition<in Drawable>?
                            ) {
                                view.defaultArtwork = resource
                            }
                        }.clearOnDetach()
                    )
            }
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

    @SuppressLint("ClickableViewAccessibility")
    override fun setupMediaView(
        url: String,
        previewUrl: String?,
        description: String?,
        showingDescription: Boolean
    ) {
        binding.mediaDescriptionTextView.text = description
        binding.mediaDescriptionScrollView.visible(showingDescription)

        // Ensure the description is visible over the video
        binding.mediaDescriptionScrollView.elevation = binding.videoView.elevation + 1

        binding.videoView.transitionName = url

        binding.videoView.requestFocus()

        if (requireArguments().getBoolean(ARG_START_POSTPONED_TRANSITION)) {
            mediaActivity.onBringUp()
        }
    }

    private fun hideToolbarAfterDelay(delayMilliseconds: Int) {
        pendingHideToolbar = true
        handler.postDelayed(hideToolbar, delayMilliseconds.toLong())
    }

    override fun onToolbarVisibilityChange(visible: Boolean) {
        if (view == null) {
            return
        }

        isDescriptionVisible = showingDescription && visible
        val alpha = if (isDescriptionVisible) 1.0f else 0.0f
        if (isDescriptionVisible) {
            // If to be visible, need to make visible immediately and animate alpha
            binding.mediaDescriptionScrollView.alpha = 0.0f
            binding.mediaDescriptionScrollView.visible(isDescriptionVisible)
        }

        binding.mediaDescriptionScrollView.animate().alpha(alpha)
            .setListener(object : AnimatorListenerAdapter() {
                @SuppressLint("SyntheticAccessor")
                override fun onAnimationEnd(animation: Animator) {
                    view ?: return
                    binding.mediaDescriptionScrollView.visible(isDescriptionVisible)
                    animation.removeListener(this)
                }
            })
            .start()

        // media3 controls bar
        if (visible) {
            binding.videoView.showController()
        } else {
            binding.videoView.hideController()
        }

        // Either the user just requested toolbar display, or we just hid it.
        // Either way, any pending hides are no longer appropriate.
        pendingHideToolbar = false
        handler.removeCallbacks(hideToolbar)
    }

    override fun onTransitionEnd() { }

    companion object {
        private const val TAG = "ViewVideoFragment"
        private const val TOOLBAR_HIDE_DELAY_MS = 4_000
        private const val SEEK_POSITION = "seekPosition"
    }
}
