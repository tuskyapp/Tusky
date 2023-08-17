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

package com.keylesspalace.tusky.components.filters

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.util.await

internal suspend fun Activity.showDeleteFilterDialog(filterTitle: String) = AlertDialog.Builder(this)
    .setMessage(getString(R.string.dialog_delete_filter_text, filterTitle))
    .setCancelable(true)
    .create()
    .await(R.string.dialog_delete_filter_positive_action, android.R.string.cancel)
