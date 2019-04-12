/* Copyright 2019 Joel Pyska
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

package tech.bigfig.roma.util

import tech.bigfig.roma.entity.Notification
import org.json.JSONArray

/**
 * Serialize to string array and deserialize notifications type
 */

fun serialize(data: Set<Notification.Type>?): String {
    val array = JSONArray()
    data?.forEach {
        array.put(it.presentation)
    }
    return array.toString()
}

fun deserialize(data: String?): Set<Notification.Type> {
    val ret = HashSet<Notification.Type>()
    data?.let {
        val array = JSONArray(data)
        for (i in 0..(array.length() - 1)) {
            val item = array.getString(i)
            val type = Notification.Type.byString(item)
            if (type != Notification.Type.UNKNOWN)
                ret.add(type)
        }
    }
    return ret
}