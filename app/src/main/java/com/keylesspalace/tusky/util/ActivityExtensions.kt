@file:JvmName("ActivityExtensions")

package com.keylesspalace.tusky.util

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.annotation.AnimRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.keylesspalace.tusky.BaseActivity

fun Activity.startActivityWithSlideInAnimation(intent: Intent) {
    startActivity(intent.withSlideInAnimation())
}

fun Intent.withSlideInAnimation(): Intent {
    // the new transition api needs to be called by the activity that is the result of the transition,
    // so we pass a flag that BaseActivity will respect.
    return putExtra(BaseActivity.OPEN_WITH_SLIDE_IN, true)
}

/**
 * Call this method in Activity.onCreate() to configure the open or close transitions.
 */
@Suppress("DEPRECATION")
fun ComponentActivity.overrideActivityTransitionCompat(
    overrideType: Int,
    @AnimRes enterAnim: Int,
    @AnimRes exitAnim: Int
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(overrideType, enterAnim, exitAnim)
    } else {
        if (overrideType == ActivityConstants.OVERRIDE_TRANSITION_OPEN) {
            overridePendingTransition(enterAnim, exitAnim)
        } else {
            lifecycle.addObserver(
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_PAUSE && isFinishing) {
                        overridePendingTransition(enterAnim, exitAnim)
                    }
                }
            )
        }
    }
}

object ActivityConstants {
    const val OVERRIDE_TRANSITION_OPEN = 0
    const val OVERRIDE_TRANSITION_CLOSE = 1
}
