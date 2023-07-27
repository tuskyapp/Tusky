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
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import at.connyduck.calladapter.networkresult.fold
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.components.filters.FiltersActivity
import com.keylesspalace.tusky.components.timeline.TimelineFragment
import com.keylesspalace.tusky.components.timeline.viewmodel.TimelineViewModel.Kind
import com.keylesspalace.tusky.databinding.ActivityStatuslistBinding
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.FilterV1
import com.keylesspalace.tusky.util.viewBinding
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

class StatusListActivity : BottomSheetActivity(), HasAndroidInjector {

    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Any>

    @Inject
    lateinit var eventHub: EventHub

    private val binding: ActivityStatuslistBinding by viewBinding(ActivityStatuslistBinding::inflate)
    private lateinit var kind: Kind
    private var hashtag: String? = null
    private var followTagItem: MenuItem? = null
    private var unfollowTagItem: MenuItem? = null
    private var muteTagItem: MenuItem? = null
    private var unmuteTagItem: MenuItem? = null

    /** The filter muting hashtag, null if unknown or hashtag is not filtered */
    private var mutedFilterV1: FilterV1? = null
    private var mutedFilter: Filter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("StatusListActivity", "onCreate")
        super.onCreate(savedInstanceState)
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
                mastodonApi.tag(tag).fold(
                    { tagEntity ->
                        menuInflater.inflate(R.menu.view_hashtag_toolbar, menu)
                        followTagItem = menu.findItem(R.id.action_follow_hashtag)
                        unfollowTagItem = menu.findItem(R.id.action_unfollow_hashtag)
                        muteTagItem = menu.findItem(R.id.action_mute_hashtag)
                        unmuteTagItem = menu.findItem(R.id.action_unmute_hashtag)
                        followTagItem?.isVisible = tagEntity.following == false
                        unfollowTagItem?.isVisible = tagEntity.following == true
                        followTagItem?.setOnMenuItemClickListener { followTag() }
                        unfollowTagItem?.setOnMenuItemClickListener { unfollowTag() }
                        muteTagItem?.setOnMenuItemClickListener { muteTag() }
                        unmuteTagItem?.setOnMenuItemClickListener { unmuteTag() }
                        updateMuteTagMenuItems()
                    },
                    {
                        Log.w(TAG, "Failed to query tag #$tag", it)
                    }
                )
            }
        }

        return super.onCreateOptionsMenu(menu)
    }

    private fun followTag(): Boolean {
        val tag = hashtag
        if (tag != null) {
            lifecycleScope.launch {
                mastodonApi.followTag(tag).fold(
                    {
                        followTagItem?.isVisible = false
                        unfollowTagItem?.isVisible = true

                        Snackbar.make(binding.root, getString(R.string.following_hashtag_success_format, tag), Snackbar.LENGTH_LONG).show()
                    },
                    {
                        Snackbar.make(binding.root, getString(R.string.error_following_hashtag_format, tag), Snackbar.LENGTH_SHORT).show()
                        Log.e(TAG, "Failed to follow #$tag", it)
                    }
                )
            }
        }

        return true
    }

    private fun unfollowTag(): Boolean {
        val tag = hashtag
        if (tag != null) {
            lifecycleScope.launch {
                mastodonApi.unfollowTag(tag).fold(
                    {
                        followTagItem?.isVisible = true
                        unfollowTagItem?.isVisible = false

                        Snackbar.make(binding.root, getString(R.string.unfollowing_hashtag_success_format, tag), Snackbar.LENGTH_SHORT).show()
                    },
                    {
                        Snackbar.make(binding.root, getString(R.string.error_unfollowing_hashtag_format, tag), Snackbar.LENGTH_SHORT).show()
                        Log.e(TAG, "Failed to unfollow #$tag", it)
                    }
                )
            }
        }

        return true
    }

    /**
     * Determine if the current hashtag is muted, and update the UI state accordingly.
     */
    private fun updateMuteTagMenuItems() {
        val tag = hashtag ?: return

        muteTagItem?.isVisible = true
        muteTagItem?.isEnabled = false
        unmuteTagItem?.isVisible = false

        lifecycleScope.launch {
            mastodonApi.getFilters().fold(
                { filters ->
                    mutedFilter = filters.firstOrNull { filter ->
                        filter.context.contains(Filter.Kind.HOME.kind) && filter.keywords.any {
                            it.keyword == tag
                        }
                    }
                    updateTagMuteState(mutedFilter != null)
                },
                { throwable ->
                    if (throwable is HttpException && throwable.code() == 404) {
                        mastodonApi.getFiltersV1().fold(
                            { filters ->
                                mutedFilterV1 = filters.firstOrNull { filter ->
                                    tag == filter.phrase && filter.context.contains(FilterV1.HOME)
                                }
                                updateTagMuteState(mutedFilterV1 != null)
                            },
                            { throwable ->
                                Log.e(TAG, "Error getting filters: $throwable")
                            }
                        )
                    } else {
                        Log.e(TAG, "Error getting filters: $throwable")
                    }
                }
            )
        }
    }

    private fun updateTagMuteState(muted: Boolean) {
        if (muted) {
            muteTagItem?.isVisible = false
            muteTagItem?.isEnabled = false
            unmuteTagItem?.isVisible = true
        } else {
            unmuteTagItem?.isVisible = false
            muteTagItem?.isEnabled = true
            muteTagItem?.isVisible = true
        }
    }

    private fun muteTag(): Boolean {
        val tag = hashtag ?: return true
        val hashedTag = if (tag.startsWith('#')) tag else "#" + tag

        lifecycleScope.launch {
            var filterCreateSuccess = false

            mastodonApi.createFilter(
                title = "#$tag",
                context = listOf(FilterV1.HOME),
                filterAction = Filter.Action.WARN.action,
                expiresInSeconds = null
            ).fold(
                { filter ->
                    if (mastodonApi.addFilterKeyword(filterId = filter.id, keyword = tag, wholeWord = true).isSuccess) {
                        mutedFilter = filter
                        // TODO the preference key here ("home") is not meaningful; should probably be another event if any
                        eventHub.dispatch(PreferenceChangedEvent(filter.context[0]))
                        filterCreateSuccess = true
                    } else {
                        Snackbar.make(binding.root, getString(R.string.error_muting_hashtag_format, tag), Snackbar.LENGTH_SHORT).show()
                        Log.e(TAG, "Failed to mute #$tag")
                    }
                },
                { throwable ->
                    if (throwable is HttpException && throwable.code() == 404) {
                        mastodonApi.createFilterV1(
                            tag,
                            listOf(FilterV1.HOME),
                            irreversible = false,
                            wholeWord = true,
                            expiresInSeconds = null
                        ).fold(
                            { filter ->
                                mutedFilterV1 = filter
                                eventHub.dispatch(PreferenceChangedEvent(filter.context[0]))
                                filterCreateSuccess = true
                            },
                            { throwable ->
                                Snackbar.make(binding.root, getString(R.string.error_muting_hashtag_format, tag), Snackbar.LENGTH_SHORT).show()
                                Log.e(TAG, "Failed to mute #$tag", throwable)
                            }
                        )
                    } else {
                        Snackbar.make(binding.root, getString(R.string.error_muting_hashtag_format, tag), Snackbar.LENGTH_SHORT).show()
                        Log.e(TAG, "Failed to mute #$tag", throwable)
                    }
                }
            )

            if (filterCreateSuccess) {
                updateTagMuteState(true)
                Snackbar.make(binding.root, getString(R.string.muting_hashtag_success_format, tag), Snackbar.LENGTH_LONG).apply {
                    setAction(R.string.action_view_filters) {
                        startActivityWithSlideInAnimation(Intent(this@StatusListActivity, FiltersActivity::class.java))
                    }
                    show()
                }
            }
        }

        return true
    }

    private fun unmuteTag(): Boolean {
        lifecycleScope.launch {
            val tag = hashtag
            val result = if (mutedFilter != null) {
                val filter = mutedFilter!!
                if (filter.context.size > 1) {
                    // This filter exists in multiple contexts, just remove the home context
                    mastodonApi.updateFilter(
                        id = filter.id,
                        context = filter.context.filter { it != Filter.Kind.HOME.kind }
                    )
                } else {
                    mastodonApi.deleteFilter(filter.id)
                }

            } else if (mutedFilterV1 != null) {
                mutedFilterV1?.let { filter ->
                    if (filter.context.size > 1) {
                        // This filter exists in multiple contexts, just remove the home context
                        mastodonApi.updateFilterV1(
                            id = filter.id,
                            phrase = filter.phrase,
                            context = filter.context.filter { it != FilterV1.HOME },
                            irreversible = null,
                            wholeWord = null,
                            expiresInSeconds = null
                        )
                    } else {
                        mastodonApi.deleteFilterV1(filter.id)
                    }
                }
            } else {
                null
            }

            result?.fold(
                {
                    updateTagMuteState(false)
                    eventHub.dispatch(PreferenceChangedEvent(Filter.Kind.HOME.kind))
                    mutedFilterV1 = null
                    mutedFilter = null

                    Snackbar.make(binding.root, getString(R.string.unmuting_hashtag_success_format, tag), Snackbar.LENGTH_SHORT).show()
                },
                { throwable ->
                    Snackbar.make(binding.root, getString(R.string.error_unmuting_hashtag_format, tag), Snackbar.LENGTH_SHORT).show()
                    Log.e(TAG, "Failed to unmute #$tag", throwable)
                }
            )
        }

        return true
    }

    override fun androidInjector() = dispatchingAndroidInjector

    companion object {

        private const val EXTRA_KIND = "kind"
        private const val EXTRA_LIST_ID = "id"
        private const val EXTRA_LIST_TITLE = "title"
        private const val EXTRA_HASHTAG = "tag"
        const val TAG = "StatusListActivity"

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
