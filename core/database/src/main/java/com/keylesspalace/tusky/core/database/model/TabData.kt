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

package com.keylesspalace.tusky.core.database.model

/**
 * A tab's kind.
 *
 * @param repr String representation of the tab in the database
 */
enum class TabKind(val repr: String) {
    HOME("Home"),
    NOTIFICATIONS("Notifications"),
    LOCAL("Local"),
    FEDERATED("Federated"),
    DIRECT("Direct"),
    TRENDING("Trending"),
    HASHTAG("Hashtag"),
    LIST("List")
}

/** this would be a good case for a sealed class, but that does not work nice with Room */

data class TabData(val kind: TabKind, val arguments: List<String> = emptyList()) {
    companion object {
        fun from(kind: TabKind, arguments: List<String> = emptyList()) =
            TabData(kind, arguments)

        fun from(kind: String, arguments: List<String> = emptyList()) =
            TabData(TabKind.valueOf(kind.uppercase()), arguments)
    }
}

fun defaultTabs() = listOf(
    TabData.from(TabKind.HOME),
    TabData.from(TabKind.NOTIFICATIONS),
    TabData.from(TabKind.LOCAL),
    TabData.from(TabKind.DIRECT)
)

fun List<TabData>.hasTab(kind: TabKind): Boolean = this.find { it.kind == kind } != null
