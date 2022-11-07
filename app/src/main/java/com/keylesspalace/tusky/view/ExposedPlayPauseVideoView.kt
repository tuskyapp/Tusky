package com.keylesspalace.tusky.view

import android.content.Context
import android.util.AttributeSet
import android.widget.VideoView

class ExposedPlayPauseVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) :
    VideoView(context, attrs, defStyleAttr) {

    private var listener: PlayPauseListener? = null
    private var playing = false

    fun setPlayPauseListener(listener: PlayPauseListener) {
        this.listener = listener
    }

    override fun start() {
        super.start()
        if (!playing) {
            playing = true
            listener?.onPlay()
        }
    }

    override fun pause() {
        super.pause()
        if (playing) {
            playing = false
            listener?.onPause()
        }
    }

    interface PlayPauseListener {
        fun onPlay()
        fun onPause()
    }
}
