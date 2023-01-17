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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.drafts.DraftsActivity
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This class manages an alert popup when a post has failed and been saved to drafts.
 * It must be separately registered in each lifetime in which it is to appear,
 * and it only appears if the post failure belongs to the current user.
 */

private const val TAG = "DraftsAlert"

@Singleton
class DraftsAlert @Inject constructor(db: AppDatabase, accountManager: AccountManager) {
    // For tracking when a media upload fails in the service
    private val draftDao: DraftDao = db.draftDao()

    // Mapped to current user failure count in draftDao
    private var draftsNeedUserAlertCurrent: LiveData<Int>? = null

    // User id corresponding to draftsNeedUserAlertCurrent
    private var userIdCurrent: Long? = null

    // Durable "forwarding address" for current draftsNeedUserAlertCurrent object
    public val draftsNeedUserAlert = MediatorLiveData<Pair<Long, Int>>()

    fun updateActiveAccountId(dbId: Long) {
        if (dbId == userIdCurrent) {
            return; // Nothing to do
        }

        userIdCurrent = dbId

        // Remove old observer if we just switched accounts
        draftsNeedUserAlertCurrent?.let {
            draftsNeedUserAlert.removeSource(it)
        }

        // Add new observer
        val draftsNeedUserAlertCurrent = draftDao.draftsNeedUserAlert(dbId)
        this.draftsNeedUserAlertCurrent = draftsNeedUserAlertCurrent
        draftsNeedUserAlert.addSource(draftsNeedUserAlertCurrent, { count ->
            Log.d(TAG, "draftsNeedUserAlert: account id " + dbId + " has " + count + " notification-worthy drafts") // REMOVE BEFORE COMMIT
            // !! here is safe because this is called only after userIdCurrent first goes non-null, and it never goes null again
            draftsNeedUserAlert.value = Pair(userIdCurrent!!, count)
        })
    }

    init {
        accountManager.draftsAlert = this
        accountManager.activeAccount?.let { updateActiveAccountId(it.id) }
    }

    public fun <T> observeInContext(context: T, showAlert: Boolean) where T : Context, T : LifecycleOwner {
        // observe ensures that this gets called at the most appropriate moment wrt the context lifecycle—
        // at init, at next onResume, or immediately if the context is resumed already.
        if (showAlert) {
            draftsNeedUserAlert.observe(context) { (dbId, count) ->
                Log.d(TAG, "draftsNeedUserAlert 2: Notification-worthy draft count " + count)
                if (count > 0) {
                    AlertDialog.Builder(context)
                        .setTitle(R.string.action_post_failed)
                        .setMessage(
                            context.getResources().getQuantityString(R.plurals.action_post_failed_detail, count)
                        )
                        .setPositiveButton(R.string.action_post_failed_show_drafts) { _: DialogInterface?, _: Int ->
                            clearDraftsAlert(dbId) // User looked at drafts

                            val intent = DraftsActivity.newIntent(context)
                            context.startActivity(intent)
                        }
                        .setNegativeButton(R.string.action_post_failed_do_nothing) { _: DialogInterface?, _: Int ->
                            clearDraftsAlert(dbId) // User doesn't care
                        }
                        .show()
                }
            }
        } else {
            draftsNeedUserAlert.observe(context) { (dbId, _) ->
                Log.d(TAG, "draftsNeedUserAlert 3: Clean out")
                clearDraftsAlert(dbId)
            }
        }
    }

    /**
     * Clear drafts alert for current user
     * Caller's responsibility to ensure there is a current user
     */
    fun clearDraftsAlert(id: Long) {
        GlobalScope.launch {
            async {
                draftDao.draftsClearNeedUserAlert(id)
            }
        }
    }
}
