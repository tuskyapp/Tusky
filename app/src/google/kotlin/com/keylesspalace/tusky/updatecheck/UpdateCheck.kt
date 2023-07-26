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

package com.keylesspalace.tusky.updatecheck

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.keylesspalace.tusky.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject

class UpdateCheck @Inject constructor(
    val context: Context
) : UpdateCheckBase() {
    private var appUpdateManager = AppUpdateManagerFactory.create(context)

    override val updateIntent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse(
            "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}")
        setPackage("com.android.vending")
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override suspend fun remoteFetchLatestVersionCode(): Int? {
        return suspendCancellableCoroutine { cont ->
            appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
                cont.resume(info.availableVersionCode()) {}
            }
        }
    }
}
