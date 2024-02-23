@file:JvmName("ActivityExtensions")

package com.keylesspalace.tusky.util

import android.app.Activity
import android.content.Intent
import android.os.Build
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R

fun Activity.startActivityWithSlideInAnimation(intent: Intent) {
    // the new transition api needs to be called by the activity that is the result of the transition,
    // so we pass a flag that BaseActivity will respect.
    intent.putExtra(BaseActivity.OPEN_WITH_SLIDE_IN, true)
    startActivity(intent)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        // the old api needs to be called by the activity that starts the transition
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left)
    }
}
