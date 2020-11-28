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
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
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
import javax.inject.Inject

class DraftsActivity: BaseActivity(), DraftActionListener {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: DraftsViewModel by viewModels { viewModelFactory }

    private lateinit var binding: ActivityDraftsBinding

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

        val composeOptions = ComposeActivity.ComposeOptions(
                savedTootUid = draft.id,
                tootText = draft.content,
                contentWarning = draft.contentWarning,
                inReplyToId = draft.inReplyToId
        )

        startActivity(ComposeActivity.startIntent(this, composeOptions))
    }

    override fun onDeleteDraft(draft: DraftEntity) {
        viewModel.deleteDraft(draft)
    }

    companion object {
        fun newIntent(context: Context) = Intent(context, DraftsActivity::class.java)
    }

}