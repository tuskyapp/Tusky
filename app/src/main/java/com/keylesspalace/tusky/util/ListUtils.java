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

package com.keylesspalace.tusky.util;

import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class ListUtils {
    /** @return true if list is null or else return list.isEmpty() */
    public static boolean isEmpty(@Nullable List list) {
        return list == null || list.isEmpty();
    }

    /** @return 0 if list is null, or else return list.size() */
    public static int getSize(@Nullable List list) {
        if (list == null) {
            return 0;
        } else {
            return list.size();
        }
    }

    /** @return a new ArrayList containing the elements without duplicates in the same order */
    public static <T> ArrayList<T> removeDuplicates(List<T> list) {
        LinkedHashSet<T> set = new LinkedHashSet<>();
        set.addAll(list);
        return new ArrayList<>(set);
    }
}
