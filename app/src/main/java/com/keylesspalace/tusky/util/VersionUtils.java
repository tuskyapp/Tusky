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
