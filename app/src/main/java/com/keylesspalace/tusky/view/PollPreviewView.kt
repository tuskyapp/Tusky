/* Copyright 2019 Tusky Contributors
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

package com.keylesspalace.tusky.view

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.PreviewPollOptionsAdapter
import com.keylesspalace.tusky.entity.NewPoll
import kotlinx.android.synthetic.main.view_poll_preview.view.*

class PollPreviewView @JvmOverloads constructor(
        context: Context?,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0)
    : LinearLayout(context, attrs, defStyleAttr) {

    val adapter = PreviewPollOptionsAdapter()

    init {
        inflate(context, R.layout.view_poll_preview, this)

        orientation = VERTICAL

        setBackgroundResource(R.drawable.card_frame)

        val padding = resources.getDimensionPixelSize(R.dimen.poll_preview_padding)

        setPadding(padding, padding, padding, padding)

        pollPreviewOptions.adapter = adapter

    }

    fun setPoll(poll: NewPoll){
        adapter.update(poll.options, poll.multiple)
    }

    override fun setOnClickListener(l: OnClickListener?) {
        super.setOnClickListener(l)
        adapter.setOnClickListener(l)
    }

}