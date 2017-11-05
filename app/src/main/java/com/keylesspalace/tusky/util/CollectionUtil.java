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

import android.arch.core.util.Function;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by charlag on 05/11/17.
 */

public final class CollectionUtil {
    private CollectionUtil() {
        throw new AssertionError();
    }

    public static <E, R> List<R> map(List<E> list, Function<E, R> mapper) {
        final List<R> newList = new ArrayList<>(list.size());
        for (E el : list) {
            newList.add(mapper.apply(el));
        }
        return newList;
    }
}
