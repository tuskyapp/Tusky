/* Copyright 2019 Tusky Contributors
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
 * see <https://www.gnu.org/licenses>. */

package com.keylesspalace.tusky

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.keylesspalace.tusky.components.timeline.TimelineFragment
import com.keylesspalace.tusky.components.timeline.viewmodel.TimelineViewModel.Kind
import com.keylesspalace.tusky.databinding.ActivityStatuslistBinding
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import javax.inject.Inject

class StatusListActivity : BottomSheetActivity(), HasAndroidInjector {

    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Any>

    lateinit var kind: Kind
    var hashtag: String? = null
    var followTagItem: MenuItem? = null
    var unfollowTagItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityStatuslistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)

        kind = Kind.valueOf(intent.getStringExtra(EXTRA_KIND)!!)
        val listId = intent.getStringExtra(EXTRA_LIST_ID)
        hashtag = intent.getStringExtra(EXTRA_HASHTAG)

        val title = when (kind) {
            Kind.FAVOURITES -> getString(R.string.title_favourites)
            Kind.BOOKMARKS -> getString(R.string.title_bookmarks)
            Kind.TAG -> getString(R.string.title_tag).format(hashtag)
            else -> intent.getStringExtra(EXTRA_LIST_TITLE)
        }

        supportActionBar?.run {
            setTitle(title)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
            supportFragmentManager.commit {
                val fragment = if (kind == Kind.TAG) {
                    TimelineFragment.newHashtagInstance(listOf(hashtag!!))
                } else {
                    TimelineFragment.newInstance(kind, listId)
                }
                replace(R.id.fragmentContainer, fragment)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val tag = hashtag
        if (kind == Kind.TAG && tag != null) {
            lifecycleScope.launch {
                try {
                    val tagEntity = mastodonApi.tag(tag).await()
                    menuInflater.inflate(R.menu.view_hashtag_toolbar, menu)
                    followTagItem = menu.findItem(R.id.action_follow_hashtag)
                    unfollowTagItem = menu.findItem(R.id.action_unfollow_hashtag)
                    followTagItem?.isVisible = tagEntity.following == false
                    unfollowTagItem?.isVisible = tagEntity.following == true
                    followTagItem?.setOnMenuItemClickListener { followTag() }
                    unfollowTagItem?.setOnMenuItemClickListener { unfollowTag() }
                } catch (exception: Exception) {
                    Log.w("Failed to query tag #$tag", exception)
                }
            }
        }

        return super.onCreateOptionsMenu(menu)
    }

    private fun followTag(): Boolean {
        val tag = hashtag
        if (tag != null) {
            val context = this
            lifecycleScope.launch {
                try {
                    mastodonApi.followTag(tag).await()
                    followTagItem?.isVisible = false
                    unfollowTagItem?.isVisible = true
                } catch (_: Exception) {
                    Toast.makeText(context, "Error following #$tag", Toast.LENGTH_SHORT).show()
                }
            }
        }

        return true
    }

    private fun unfollowTag(): Boolean {
        val tag = hashtag
        if (tag != null) {
            val context = this
            lifecycleScope.launch {
                try {
                    mastodonApi.unfollowTag(tag).await()
                    followTagItem?.isVisible = true
                    unfollowTagItem?.isVisible = false
                } catch (_: Exception) {
                    Toast.makeText(context, "Error unfollowing #$tag", Toast.LENGTH_SHORT).show()
                }
            }
        }

        return true
    }

    override fun androidInjector() = dispatchingAndroidInjector

    companion object {

        private const val EXTRA_KIND = "kind"
        private const val EXTRA_LIST_ID = "id"
        private const val EXTRA_LIST_TITLE = "title"
        private const val EXTRA_HASHTAG = "tag"

        fun newFavouritesIntent(context: Context) =
            Intent(context, StatusListActivity::class.java).apply {
                putExtra(EXTRA_KIND, Kind.FAVOURITES.name)
            }

        fun newBookmarksIntent(context: Context) =
            Intent(context, StatusListActivity::class.java).apply {
                putExtra(EXTRA_KIND, Kind.BOOKMARKS.name)
            }

        fun newListIntent(context: Context, listId: String, listTitle: String) =
            Intent(context, StatusListActivity::class.java).apply {
                putExtra(EXTRA_KIND, Kind.LIST.name)
                putExtra(EXTRA_LIST_ID, listId)
                putExtra(EXTRA_LIST_TITLE, listTitle)
            }

        @JvmStatic
        fun newHashtagIntent(context: Context, hashtag: String) =
            Intent(context, StatusListActivity::class.java).apply {
                putExtra(EXTRA_KIND, Kind.TAG.name)
                putExtra(EXTRA_HASHTAG, hashtag)
            }
    }
}
