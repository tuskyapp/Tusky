/* Copyright 2021 Tusky Contributors
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
package com.keylesspalace.tusky.adapter

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.interfaces.StatusActionListener

/**
 * Placeholder for different timelines.
 *
 * Displays a "Load more" button for a particular status ID, or a
 * circular progress wheel if the status' page is being loaded.
 *
 * The user can only have one "Load more" operation in progress at
 * a time (determined by the adapter), so the contents of the view
 * and the enabled state is driven by that.
 */
class PlaceholderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val loadMoreButton: MaterialButton = itemView.findViewById(R.id.button_load_more)
    private val drawable = IndeterminateDrawable.createCircularDrawable(
        itemView.context,
        CircularProgressIndicatorSpec(itemView.context, null)
    )

    fun setup(listener: StatusActionListener, loading: Boolean) {
        itemView.isEnabled = !loading
        loadMoreButton.isEnabled = !loading

        if (loading) {
            loadMoreButton.text = ""
            loadMoreButton.icon = drawable
            return
        }

        loadMoreButton.text = itemView.context.getString(R.string.load_more_placeholder_text)
        loadMoreButton.icon = null

        // To allow the user to click anywhere in the layout to load more content set the click
        // listener on the parent layout instead of loadMoreButton.
        //
        // See the comments in item_status_placeholder.xml for more details.
        itemView.setOnClickListener {
            itemView.isEnabled = false
            loadMoreButton.isEnabled = false
            loadMoreButton.icon = drawable
            loadMoreButton.text = ""
            listener.onLoadMore(bindingAdapterPosition)
        }
    }
}
