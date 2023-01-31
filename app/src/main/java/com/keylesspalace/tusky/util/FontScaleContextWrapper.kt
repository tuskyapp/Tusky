package com.keylesspalace.tusky.util

import android.content.Context
import android.content.ContextWrapper

class FontScaleContextWrapper(base: Context?) : ContextWrapper(base) {
    companion object {
        const val TAG = "FontScaleContextWrapper"

        /**
         * Wraps `context`, setting the correct `fontScale` from `fontScaleRatio`.
         */
        fun wrap(context: Context, fontScaleRatio: Float): FontScaleContextWrapper {
            val configuration = context.resources.configuration

            // Can't simply adjust the `fontScale` in `context`, because that will contain the
            // result of previous adjustments. E.g., going from 100% to 80% to 100% does not return
            // you to the original 100%, it leaves it at 80%.
            //
            // The application context is unaffected by changes to the base context, so always use
            // the `fontScale` from that as the initial value to multiply. This will also contain
            // any changes to the font scale from "Settings > Display > Font size" in the device
            // settings.
            val appConfiguration = context.applicationContext.resources.configuration

            // This only adjusts the fonts, anything measured in `dp` is unaffected by this.
            // You can try to adjust `densityDpi` as shown in the commented out code below. This
            // works, to a point. However, dialogs do not react well to this. Beyond a certain
            // scale (~ 120%) the right hand edge of the dialog will clip off the right of the
            // screen.
            //
            // So for now, just adjust the font scale
            //
            // val displayMetrics = appContext.resources.displayMetrics
            // configuration.densityDpi = ((displayMetrics.densityDpi * uiScaleRatio).toInt())

            configuration.fontScale = appConfiguration.fontScale * fontScaleRatio

            val newContext = context.createConfigurationContext(configuration)
            return FontScaleContextWrapper(newContext)
        }
    }
}
