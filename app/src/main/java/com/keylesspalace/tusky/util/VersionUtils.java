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

import androidx.annotation.NonNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionUtils {
    private int vanillaMajor;
    private int vanillaMinor;
    private int vanillaPatch;

    private String backend = "Mastodon";
    private int backendMajor;
    private int backendMinor;
    private int backendPatch;

    public VersionUtils(@NonNull String versionString) {
        // Match Mastodon and its forks
        String regex = "^([0-9]+)\\.([0-9]+)\\.([0-9]+)\\+([\\S]+).*";
        Pattern pattern = Pattern.compile(versionRegex);

        Matcher matcher = pattern.matcher(versionString);
        if (! matcher.find()) return;

        vanillaMajor = Integer.parseInt(matcher.group(1));
        vanillaMinor = Integer.parseInt(matcher.group(2));
        vanillaPatch = Integer.parseInt(matcher.group(3));

        String forkName = matcher.group(4);
        if (forkName != null) backend = forkName;

        // Try Pleroma-like version string
        String pleromaRegex = "^([\\w\\.]*)(?: \\(compatible; ([\\w]*) (.*)\\))?$";
        Pattern pleromaPattern = Pattern.compile(pleromaRegex);

        Matcher pleromaMatcher = pleromaPattern.matcher(versionString);
        if (! pleromaMatcher.find()) return;
        if (matcher.group(2) != null) backend = matcher.group(2);

        String backendVersion = matcher.group(3);
        if (backendVersion == null) return;

        Matcher backendVersionMatcher = pattern.matcher(backendVersion);
        if (! backendVersionMatcher.find()) return;

        backendMajor = Integer.parseInt(backendVersionMatcher.group(1));
        backendMinor = Integer.parseInt(backendVersionMatcher.group(2));
        backendPatch = Integer.parseInt(backendVersionMatcher.group(3));
    }

    public boolean supportsScheduledToots() {
        return (vanillaMajor == 2) ? ( (vanillaMinor == 7) ? (vanillaPatch >= 0) : (vanillaMinor > 7) ) : (vanillaMajor > 2);
    }

    public boolean supportsRichTextToots() {
        return (backend == "glitch") || (backend == "Pleroma");
    }
}
