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

import android.os.Bundle
import android.text.TextUtils
import android.widget.TextView

import com.keylesspalace.tusky.ViewMediaActivity
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show

abstract class ViewMediaFragment : BaseFragment() {
    private var toolbarVisibiltyDisposable: Function0<Boolean>? = null

    abstract fun setupMediaView(url: String)
    abstract fun onToolbarVisibilityChange(visible: Boolean)
    abstract val descriptionView : TextView

    protected var showingDescription = false
    protected var isDescriptionVisible = false

    companion object {
        @JvmStatic protected val ARG_START_POSTPONED_TRANSITION = "startPostponedTransition"
        @JvmStatic protected val ARG_ATTACHMENT = "attach"
        @JvmStatic protected val ARG_AVATAR_URL = "avatarUrl"
        private const val TAG = "ViewMediaFragment"

        @JvmStatic
        fun newInstance(attachment: Attachment, shouldStartPostponedTransition: Boolean): ViewMediaFragment {
            val arguments = Bundle(2)
            arguments.putParcelable(ARG_ATTACHMENT, attachment)
            arguments.putBoolean(ARG_START_POSTPONED_TRANSITION, shouldStartPostponedTransition)

            val fragment = when (attachment.type) {
                Attachment.Type.IMAGE -> ViewImageFragment()
                Attachment.Type.VIDEO,
                Attachment.Type.GIFV -> ViewVideoFragment()
                else -> throw Exception("Unknown media type: $attachment")
            }
            fragment.arguments = arguments
            return fragment
        }

        @JvmStatic
        fun newAvatarInstance(avatarUrl: String): ViewMediaFragment {
            val arguments = Bundle(2)
            val fragment = ViewImageFragment()
            arguments.putString(ARG_AVATAR_URL, avatarUrl)
            arguments.putBoolean(ARG_START_POSTPONED_TRANSITION, true)

            fragment.arguments = arguments
            return fragment
        }
    }

    protected fun finalizeViewSetup(url: String, description: String?) {
        val mediaActivity = activity as ViewMediaActivity
        setupMediaView(url)

        descriptionView.text = description ?: ""
        showingDescription = !TextUtils.isEmpty(description)
        isDescriptionVisible = showingDescription

        updateDescriptionVisibility(showingDescription && mediaActivity.isToolbarVisible())

        toolbarVisibiltyDisposable = (activity as ViewMediaActivity).addToolbarVisibilityListener(object: ViewMediaActivity.ToolbarVisibilityListener {
            override fun onToolbarVisiblityChanged(isVisible: Boolean) {
                onToolbarVisibilityChange(isVisible)
            }
        })
    }

    protected fun updateDescriptionVisibility(visible: Boolean) {
        // Setting visibility without animations so it looks nice when you scroll media
        if (visible) {
            descriptionView.show()
        } else {
            descriptionView.hide()
        }
    }

    override fun onDestroyView() {
        toolbarVisibiltyDisposable?.invoke()
        super.onDestroyView()
    }
}
