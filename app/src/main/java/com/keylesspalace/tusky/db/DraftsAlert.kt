/* Copyright 2023 Andi McClure
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
 * see <http://www.gnu.org/licenses>. */

package com.keylesspalace.tusky.db

import android.content.Context
import android.content.DialogInterface
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.drafts.DraftsActivity
import com.keylesspalace.tusky.db.dao.DraftDao
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * This class manages an alert popup when a post has failed and been saved to drafts.
 * It must be separately registered in each lifetime in which it is to appear,
 * and it only appears if the post failure belongs to the current user.
 */

private const val TAG = "DraftsAlert"

class DraftsAlert @Inject constructor(
    db: AppDatabase,
    private val accountManager: AccountManager
) {
    // For tracking when a media upload fails in the service
    private val draftDao: DraftDao = db.draftDao()

    private var dialog: AlertDialog? = null

    fun <T> observeInContext(context: T, showAlert: Boolean) where T : Context, T : LifecycleOwner {
        accountManager.activeAccount?.let { activeAccount ->
            val coroutineScope = context.lifecycleScope

            // One activity never sees more then one user id in its lifetime.
            val activeAccountId = activeAccount.id

            // observe ensures that this gets called at the most appropriate moment wrt the context lifecycle—
            // at init, at next onResume, or immediately if the context is resumed already.
            coroutineScope.launch {
                context.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    val draftsNeedUserAlert = draftDao.draftsNeedUserAlert(activeAccountId)

                    if (showAlert) {
                        draftDao.draftsNeedUserAlert(activeAccountId).collect { count ->
                            Log.d(TAG, "User id $activeAccountId changed: Notification-worthy draft count $count")
                            if (count > 0) {
                                dialog?.cancel()
                                dialog = MaterialAlertDialogBuilder(context)
                                    .setTitle(R.string.action_post_failed)
                                    .setMessage(
                                        context.resources.getQuantityString(R.plurals.action_post_failed_detail, count)
                                    )
                                    .setPositiveButton(R.string.action_post_failed_show_drafts) { _: DialogInterface?, _: Int ->
                                        clearDraftsAlert(coroutineScope, activeAccountId) // User looked at drafts

                                        val intent = DraftsActivity.newIntent(context)
                                        context.startActivity(intent)
                                    }
                                    .setNegativeButton(R.string.action_post_failed_do_nothing) { _: DialogInterface?, _: Int ->
                                        clearDraftsAlert(coroutineScope, activeAccountId) // User doesn't care
                                    }
                                    .show()
                            }
                        }
                    } else {
                        draftsNeedUserAlert.collect {
                            Log.d(TAG, "User id $activeAccountId: Clean out notification-worthy drafts")
                            clearDraftsAlert(coroutineScope, activeAccountId)
                        }
                    }
                }
            }
        } ?: run {
            Log.w(TAG, "Attempted to observe drafts, but there is no active account")
        }
    }

    /**
     * Clear drafts alert for specified user
     */
    private fun clearDraftsAlert(coroutineScope: LifecycleCoroutineScope, id: Long) {
        coroutineScope.launch {
            draftDao.draftsClearNeedUserAlert(id)
        }
    }
}
