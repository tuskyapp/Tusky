package com.keylesspalace.tusky.view

import android.content.Context
import android.util.AttributeSet
import android.widget.VideoView

class ExposedPlayPauseVideoView : VideoView {
    private var mListener: PlayPauseListener? = null

    constructor(ctx: Context) : super(ctx)
    constructor(ctx: Context, attrs: AttributeSet) : super(ctx, attrs)
    constructor(ctx: Context, attrs: AttributeSet, defStyleAttr: Int) : super(ctx, attrs, defStyleAttr)
    constructor(ctx: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(ctx, attrs, defStyleAttr, defStyleRes)

    fun setPlayPauseListener(listener: PlayPauseListener) {
        mListener = listener
    }

    override fun start() {
        super.start()
        mListener?.onPlay()
    }

    override fun pause() {
        super.pause()
        mListener?.onPause()
    }

    interface PlayPauseListener {
        fun onPlay()
        fun onPause()
    }
}