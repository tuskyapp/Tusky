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

package com.keylesspalace.tusky.db

import androidx.room.Entity

enum class RemoteKeyKind {
    /** Key to load the next (chronologically oldest) page of data for this timeline */
    NEXT,

    /** Key to load the previous (chronologically newer) page of data for this timeline */
    PREV
}

/**
 * The next and previous keys for the given timeline.
 */
@Entity(
    primaryKeys = ["accountId", "timelineId", "kind"]
)
data class RemoteKeyEntity(
    /** User account these keys relate to. */
    val accountId: Long,
    /**
     * Identifier for the timeline these keys relate to.
     *
     * At the moment there is only one valid value here, "home", as that
     * is the only timeline that is cached. As more timelines become cacheable
     * this will need to be expanded.
     *
     * This also needs to be extensible in the future to cover the case where
     * the user might have multiple timelines from the same base timeline, but
     * with different configurations. E.g., two home timelines, one with boosts
     * and replies turned off, and one with boosts and replies turned on.
     */
    val timelineId: String,
    val kind: RemoteKeyKind,
    val key: String? = null,
)

