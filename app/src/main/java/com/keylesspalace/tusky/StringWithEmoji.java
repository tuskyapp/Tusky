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

package com.keylesspalace.tusky;

/**
 * This is just a wrapper class for a String.
 *
 * It was designed to get around the limitation of a Json deserializer which only allows custom
 * deserializing based on types, when special handling for a specific field was what was actually
 * desired (in this case, display names). So, it was most expedient to just make up a type.
 */
public class StringWithEmoji {
    public String value;

    public StringWithEmoji(String value) {
        this.value = value;
    }
}
