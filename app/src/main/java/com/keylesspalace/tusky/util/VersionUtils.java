/* Copyright 2019 kyori19
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionUtils {

    private int major;
    private int minor;
    private int patch;

    public VersionUtils(String versionString) {
        String regex = "([0-9]+)\\.([0-9]+)\\.([0-9]+).*";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(versionString);
        if (matcher.find()) {
            major = Integer.parseInt(matcher.group(1));
            minor = Integer.parseInt(matcher.group(2));
            patch = Integer.parseInt(matcher.group(3));
        }
    }

    public boolean supportsScheduledToots() {
        return (major == 2) ? ( (minor == 7) ? (patch >= 0) : (minor > 7) ) : (major > 2);
    }

}
