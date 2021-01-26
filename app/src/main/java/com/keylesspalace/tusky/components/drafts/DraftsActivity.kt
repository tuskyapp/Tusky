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
import android.view.Menu
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.SavedTootActivity
import com.keylesspalace.tusky.components.compose.ComposeActivity
import com.keylesspalace.tusky.databinding.ActivityDraftsBinding
import com.keylesspalace.tusky.db.DraftEntity
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.uber.autodispose.android.lifecycle.autoDispose
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import retrofit2.HttpException
import javax.inject.Inject

class DraftsActivity : BaseActivity(), DraftActionListener {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: DraftsViewModel by viewModels { viewModelFactory }

    private lateinit var binding: ActivityDraftsBinding
    private lateinit var bottomSheet: BottomSheetBehavior<LinearLayout>

    private var oldDraftsButton: MenuItem? = null

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

        binding.draftsErrorMessageView.setup(R.drawable.elephant_friend_empty, R.string.no_saved_status)

        val adapter = DraftsAdapter(this)

        binding.draftsRecyclerView.adapter = adapter
        binding.draftsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.draftsRecyclerView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        bottomSheet = BottomSheetBehavior.from(binding.bottomSheet.root)

        viewModel.drafts.observe(this) { draftList ->
            if (draftList.isEmpty()) {
                binding.draftsRecyclerView.hide()
                binding.draftsErrorMessageView.show()
            } else {
                binding.draftsRecyclerView.show()
                binding.draftsErrorMessageView.hide()
                adapter.submitList(draftList)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.drafts, menu)
        oldDraftsButton = menu.findItem(R.id.action_old_drafts)
        viewModel.showOldDraftsButton()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .autoDispose(this, Lifecycle.Event.ON_DESTROY)
                .subscribe { showOldDraftsButton ->
                    oldDraftsButton?.isVisible = showOldDraftsButton
                }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
            R.id.action_old_drafts -> {
                val intent = Intent(this, SavedTootActivity::class.java)
                startActivityWithSlideInAnimation(intent)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onOpenDraft(draft: DraftEntity) {

        if (draft.inReplyToId != null) {
            bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
            viewModel.getToot(draft.inReplyToId)
                    .observeOn(AndroidSchedulers.mainThread())
                    .autoDispose(this)
                    .subscribe({ status ->
                        val composeOptions = ComposeActivity.ComposeOptions(
                                draftId = draft.id,
                                tootText = draft.content,
                                contentWarning = draft.contentWarning,
                                inReplyToId = draft.inReplyToId,
                                replyingStatusContent = status.content.toString(),
                                replyingStatusAuthor = status.account.localUsername,
                                draftAttachments = draft.attachments,
                                poll = draft.poll,
                                sensitive = draft.sensitive,
                                visibility = draft.visibility
                        )

                        bottomSheet.state = BottomSheetBehavior.STATE_HIDDEN

                        startActivity(ComposeActivity.startIntent(this, composeOptions))

                    }, { throwable ->

                        bottomSheet.state = BottomSheetBehavior.STATE_HIDDEN

                        Log.w(TAG, "failed loading reply information", throwable)

                        if (throwable is HttpException && throwable.code() == 404) {
                            // the original status to which a reply was drafted has been deleted
                            // let's open the ComposeActivity without reply information
                            Toast.makeText(this, getString(R.string.drafts_toot_reply_removed), Toast.LENGTH_LONG).show()
                            openDraftWithoutReply(draft)
                        } else {
                            Snackbar.make(binding.root, getString(R.string.drafts_failed_loading_reply), Snackbar.LENGTH_SHORT)
                                    .show()
                        }
                    })
        } else {
            openDraftWithoutReply(draft)
        }
    }

    private fun openDraftWithoutReply(draft: DraftEntity) {
        val composeOptions = ComposeActivity.ComposeOptions(
                draftId = draft.id,
                tootText = draft.content,
                contentWarning = draft.contentWarning,
                draftAttachments = draft.attachments,
                poll = draft.poll,
                sensitive = draft.sensitive,
                visibility = draft.visibility
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
