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

package com.keylesspalace.tusky.util

import android.content.DialogInterface
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Wait for the alert dialog buttons to be clicked, return the ID of the clicked button
 *
 * @param positiveText Text to show on the positive button
 * @param negativeText Optional text to show on the negative button
 * @param neutralText Optional text to show on the neutral button
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun AlertDialog.await(
    positiveText: String,
    negativeText: String? = null,
    neutralText: String? = null
) = suspendCancellableCoroutine<Int> { cont ->
    val listener = DialogInterface.OnClickListener { _, which ->
        cont.resume(which) { dismiss() }
    }

    setButton(AlertDialog.BUTTON_POSITIVE, positiveText, listener)
    negativeText?.let { setButton(AlertDialog.BUTTON_NEGATIVE, it, listener) }
    neutralText?.let { setButton(AlertDialog.BUTTON_NEUTRAL, it, listener) }

    setOnCancelListener { cont.cancel() }
    cont.invokeOnCancellation { dismiss() }
    show()
}

/**
 * @see [AlertDialog.await]
 */
suspend fun AlertDialog.await(
    @StringRes positiveTextResource: Int,
    @StringRes negativeTextResource: Int? = null,
    @StringRes neutralTextResource: Int? = null
) = await(
    context.getString(positiveTextResource),
    negativeTextResource?.let { context.getString(it) },
    neutralTextResource?.let { context.getString(it) }
)
