/* Copyright 2020 Tusky Contributors
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

package com.keylesspalace.tusky.components.drafts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import at.connyduck.calladapter.networkresult.fold
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.components.compose.ComposeActivity
import com.keylesspalace.tusky.databinding.ActivityDraftsBinding
import com.keylesspalace.tusky.db.DraftEntity
import com.keylesspalace.tusky.db.DraftsAlert
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.util.parseAsMastodonHtml
import com.keylesspalace.tusky.util.visible
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

class DraftsActivity : BaseActivity(), DraftActionListener {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var draftsAlert: DraftsAlert

    private val viewModel: DraftsViewModel by viewModels { viewModelFactory }

    private lateinit var binding: ActivityDraftsBinding
    private lateinit var bottomSheet: BottomSheetBehavior<LinearLayout>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDraftsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.title_drafts)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        binding.draftsErrorMessageView.setup(R.drawable.elephant_friend_empty, R.string.no_drafts)

        val adapter = DraftsAdapter(this)

        binding.draftsRecyclerView.adapter = adapter
        binding.draftsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.draftsRecyclerView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        bottomSheet = BottomSheetBehavior.from(binding.bottomSheet.root)

        lifecycleScope.launch {
            viewModel.drafts.collectLatest { draftData ->
                adapter.submitData(draftData)
            }
        }

        adapter.addLoadStateListener {
            binding.draftsErrorMessageView.visible(adapter.itemCount == 0)
        }

        // If a failed post is saved to drafts while this activity is up, do nothing; the user is already in the drafts view.
        draftsAlert.observeInContext(this, false)
    }

    override fun onOpenDraft(draft: DraftEntity) {
        if (draft.inReplyToId == null) {
            openDraftWithoutReply(draft)
            return
        }

        val context = this as Context

        lifecycleScope.launch {
            bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
            viewModel.getStatus(draft.inReplyToId)
                .fold(
                    { status ->
                        val composeOptions = ComposeActivity.ComposeOptions(
                            draftId = draft.id,
                            content = draft.content,
                            contentWarning = draft.contentWarning,
                            inReplyToId = draft.inReplyToId,
                            replyingStatusContent = status.content.parseAsMastodonHtml().toString(),
                            replyingStatusAuthor = status.account.localUsername,
                            draftAttachments = draft.attachments,
                            poll = draft.poll,
                            sensitive = draft.sensitive,
                            visibility = draft.visibility,
                            scheduledAt = draft.scheduledAt,
                            language = draft.language,
                            statusId = draft.statusId,
                            kind = ComposeActivity.ComposeKind.EDIT_DRAFT
                        )

                        bottomSheet.state = BottomSheetBehavior.STATE_HIDDEN

                        startActivity(ComposeActivity.startIntent(context, composeOptions))
                    },
                    { throwable ->
                        bottomSheet.state = BottomSheetBehavior.STATE_HIDDEN

                        Log.w(TAG, "failed loading reply information", throwable)

                        if (throwable is HttpException && throwable.code() == 404) {
                            // the original status to which a reply was drafted has been deleted
                            // let's open the ComposeActivity without reply information
                            Toast.makeText(context, getString(R.string.drafts_post_reply_removed), Toast.LENGTH_LONG).show()
                            openDraftWithoutReply(draft)
                        } else {
                            Snackbar.make(binding.root, getString(R.string.drafts_failed_loading_reply), Snackbar.LENGTH_SHORT)
                                .show()
                        }
                    }
                )
        }
    }

    private fun openDraftWithoutReply(draft: DraftEntity) {
        val composeOptions = ComposeActivity.ComposeOptions(
            draftId = draft.id,
            content = draft.content,
            contentWarning = draft.contentWarning,
            draftAttachments = draft.attachments,
            poll = draft.poll,
            sensitive = draft.sensitive,
            visibility = draft.visibility,
            scheduledAt = draft.scheduledAt,
            language = draft.language,
            statusId = draft.statusId,
            kind = ComposeActivity.ComposeKind.EDIT_DRAFT
        )

        startActivity(ComposeActivity.startIntent(this, composeOptions))
    }

    override fun onDeleteDraft(draft: DraftEntity) {
        viewModel.deleteDraft(draft)
        Snackbar.make(binding.root, getString(R.string.draft_deleted), Snackbar.LENGTH_LONG)
            .setAction(R.string.action_undo) {
                viewModel.restoreDraft(draft)
            }
            .show()
    }

    companion object {
        const val TAG = "DraftsActivity"

        fun newIntent(context: Context) = Intent(context, DraftsActivity::class.java)
    }
}
