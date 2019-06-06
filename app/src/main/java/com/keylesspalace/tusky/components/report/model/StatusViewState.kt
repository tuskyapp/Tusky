/* Copyright 2019 Joel Pyska
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

package com.keylesspalace.tusky.components.report.model

class StatusViewState {
    private val mediaShownState = HashMap<String, Boolean>()
    private val contentShownState = HashMap<String, Boolean>()
    private val longContentCollapsedState = HashMap<String, Boolean>()

    fun isMediaShow(id: String, isSensitive: Boolean): Boolean = isStateEnabled(mediaShownState, id, !isSensitive)
    fun setMediaShow(id: String, isShow: Boolean) = setStateEnabled(mediaShownState, id, isShow)

    fun isContentShow(id: String, isSensitive: Boolean): Boolean = isStateEnabled(contentShownState, id, !isSensitive)
    fun setContentShow(id: String, isShow: Boolean) = setStateEnabled(contentShownState, id, isShow)

    fun isCollapsed(id: String, isCollapsed: Boolean): Boolean = isStateEnabled(longContentCollapsedState, id, isCollapsed)
    fun setCollapsed(id: String, isCollapsed: Boolean) = setStateEnabled(longContentCollapsedState, id, isCollapsed)

    private fun isStateEnabled(map: Map<String, Boolean>, id: String, def: Boolean): Boolean = map[id]
            ?: def

    private fun setStateEnabled(map: MutableMap<String, Boolean>, id: String, state: Boolean) = map.put(id, state)
}