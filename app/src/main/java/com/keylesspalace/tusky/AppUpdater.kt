/*
 * Copyright 2023 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>.
 */

/*
 * Copyright 2023 Krzysztof Nawrot
 *
 * This file is a lightly altered copy of
 * https://github.com/Parseus/codecinfo/blob/master/app/src/nonFreeMobile/java/com/parseus/codecinfo/utils/ThirdPartyUtils.kt
 * available under the Apache 2.0 license,
 * https://github.com/Parseus/codecinfo/blob/4c8e2a5bc7db1ca7a5435e4ee1e72c3c327bf175/LICENSE
 */

package com.keylesspalace.tusky

import android.app.Activity
import android.widget.ProgressBar
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.view.isVisible
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

private const val MIN_IMMEDIATE_UPDATE_PRIORITY = 4
private const val MAX_FLEXIBLE_UPDATE_PRIORITY = 3

private lateinit var appUpdateManager: AppUpdateManager
private lateinit var updateListener: InstallStateUpdatedListener
private lateinit var inAppUpdateResultLauncher: ActivityResultLauncher<IntentSenderRequest>

enum class UpdateType {
    Flexible, Immediate, Unknown
}

private var appUpdateType = UpdateType.Unknown

fun checkForUpdate(activity: Activity, progressBar: ProgressBar) {
    appUpdateManager = AppUpdateManagerFactory.create(activity)
    appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
        if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
            if (info.updatePriority() >= MIN_IMMEDIATE_UPDATE_PRIORITY
                && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                appUpdateType = UpdateType.Immediate
                appUpdateManager.startUpdateFlowForResult(info, inAppUpdateResultLauncher,
                    AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE))
            } else if (info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                && info.updatePriority() <= MAX_FLEXIBLE_UPDATE_PRIORITY) {
                appUpdateType = UpdateType.Flexible
                updateListener = InstallStateUpdatedListener { state ->
                    when {
                        state.installStatus() == InstallStatus.DOWNLOADING -> {
                            progressBar!!.isVisible = true
                            val bytesDownloaded = state.bytesDownloaded()
                            val totalBytesToDownload = state.totalBytesToDownload()
                            val currentProgress = (bytesDownloaded / totalBytesToDownload).toInt() * 100
//                            progressBar.setProgressCompat(currentProgress, true)
                            progressBar.progress = currentProgress
                            progressBar.contentDescription = activity.getString(R.string.update_flexible_progress_description, currentProgress)
                        }
                        state.installStatus() in InstallStatus.FAILED..InstallStatus.CANCELED -> {
                            progressBar!!.isVisible = false
                        }
                        state.installStatus() == InstallStatus.DOWNLOADED -> {
                            showSnackbarForDownloadedUpdate(activity)
                        }
                    }
                }
                appUpdateManager.registerListener(updateListener)
                appUpdateManager.startUpdateFlowForResult(info, inAppUpdateResultLauncher,
                    AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE))
            }
        }
    }
}

fun handleAppUpdateOnActivityResult(activity: Activity, resultCode: Int) {
    if (resultCode == Activity.RESULT_CANCELED) {
        appUpdateManager.unregisterListener(updateListener)
    } else if (resultCode == ActivityResult.RESULT_IN_APP_UPDATE_FAILED) {
        Snackbar.make(activity.findViewById(android.R.id.content),
            R.string.update_failed, Snackbar.LENGTH_LONG).show()
    }
}

fun handleAppUpdateOnResume(activity: Activity) {
    if (appUpdateType == UpdateType.Flexible) {
        handleFlexibleUpdateOnResume(activity)
    } else if (appUpdateType == UpdateType.Immediate) {
        handleImmediateUpdateOnResume(activity)
    }
}

private fun handleFlexibleUpdateOnResume(activity: Activity) {
    appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
        if (info.installStatus() == InstallStatus.DOWNLOADED) {
            appUpdateManager.unregisterListener(updateListener)
            showSnackbarForDownloadedUpdate(activity)
        }
    }
}

private fun handleImmediateUpdateOnResume(activity: Activity) {
    appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
        if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
            appUpdateManager.startUpdateFlow(info, activity, AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE))
        }
    }
}

private fun showSnackbarForDownloadedUpdate(activity: Activity) {
    Snackbar.make(activity.findViewById(android.R.id.content),
        R.string.update_flexible_complete, Snackbar.LENGTH_INDEFINITE).apply {
        setAction(R.string.update_flexible_restart) { appUpdateManager.completeUpdate() }
        show()
    }
}
