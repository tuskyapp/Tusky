/*
 * Copyright 2023 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky.components.timeline

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** A timeline's type. Hold's data necessary to display that timeline. */
@Parcelize
sealed class TimelineKind : Parcelable {
    object Home : TimelineKind()
    object PublicFederated : TimelineKind()
    object PublicLocal : TimelineKind()
    data class Tag(val tags: List<String>) : TimelineKind()

    /** Any timeline showing statuses from a single user */
    @Parcelize
    sealed class User(open val id: String) : TimelineKind() {
        /** Timeline showing just the user's statuses (no replies) */
        data class Posts(override val id: String) : User(id)

        /** Timeline showing the user's pinned statuses */
        data class Pinned(override val id: String) : User(id)

        /** Timeline showing the user's top-level statuses and replies they have made */
        data class Replies(override val id: String) : User(id)
    }
    object Favourites : TimelineKind()
    object Bookmarks : TimelineKind()
    data class UserList(val id: String, val title: String) : TimelineKind()
}
