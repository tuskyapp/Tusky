/* Copyright 2017 Andrew Dawson
 *
 * This file is a part of Roma.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Roma is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Roma; if not,
 * see <http://www.gnu.org/licenses>. */

package tech.bigfig.roma.fragment

import android.os.Bundle
import android.text.TextUtils
import android.widget.TextView

import tech.bigfig.roma.ViewMediaActivity
import tech.bigfig.roma.entity.Attachment
import tech.bigfig.roma.util.visible

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

        @JvmStatic
        fun newInstance(attachment: Attachment, shouldStartPostponedTransition: Boolean): ViewMediaFragment {
            val arguments = Bundle(2)
            arguments.putParcelable(ARG_ATTACHMENT, attachment)
            arguments.putBoolean(ARG_START_POSTPONED_TRANSITION, shouldStartPostponedTransition)

            val fragment = when (attachment.type) {
                Attachment.Type.IMAGE -> ViewImageFragment()
                Attachment.Type.VIDEO,
                Attachment.Type.GIFV -> ViewVideoFragment()
                else -> ViewImageFragment()   // it probably won't show anything, but its better than crashing
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

        descriptionView.visible(showingDescription && mediaActivity.isToolbarVisible())

        toolbarVisibiltyDisposable = (activity as ViewMediaActivity).addToolbarVisibilityListener(object: ViewMediaActivity.ToolbarVisibilityListener {
            override fun onToolbarVisiblityChanged(isVisible: Boolean) {
                onToolbarVisibilityChange(isVisible)
            }
        })
    }

    override fun onDestroyView() {
        toolbarVisibiltyDisposable?.invoke()
        super.onDestroyView()
    }
}
