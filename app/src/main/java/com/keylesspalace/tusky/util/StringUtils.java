package com.keylesspalace.tusky.util;

import java.util.Random;

public class StringUtils {

    public final static String carriageReturn = System.getProperty("line.separator");
    final static String QUOTE = "\"";

    public static String randomAlphanumericString(int count) {
        char[] chars = new char[count];
        Random random = new Random();
        final String POSSIBLE_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        for (int i = 0; i < count; i++) {
            chars[i] = POSSIBLE_CHARS.charAt(random.nextInt(POSSIBLE_CHARS.length()));
        }
        return new String(chars);
    }
}
