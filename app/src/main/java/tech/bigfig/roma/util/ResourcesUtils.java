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

package tech.bigfig.roma.util;

import android.content.Context;
import androidx.annotation.AnyRes;

/**
 * Created by remi on 1/14/18.
 */

public class ResourcesUtils {
    public static @AnyRes int getResourceIdentifier(Context context, String defType, String name) {
        return context.getResources().getIdentifier(name, defType, context.getPackageName());
    }
}
