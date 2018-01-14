package com.keylesspalace.tusky.util;

import android.content.Context;
import android.support.annotation.AnyRes;

/**
 * Created by remi on 1/14/18.
 */

public class ResourcesUtils {
    public static @AnyRes int getResourceIdentifier(Context context, String defType, String name) {
        return context.getResources().getIdentifier(name, defType, context.getPackageName());
    }
}
