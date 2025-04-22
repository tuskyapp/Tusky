package com.keylesspalace.tusky.components.followedtags

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import at.connyduck.calladapter.networkresult.fold
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.StatusListActivity
import com.keylesspalace.tusky.databinding.ActivityFollowedTagsBinding
import com.keylesspalace.tusky.interfaces.HashtagActionListener
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.copyToClipboard
import com.keylesspalace.tusky.util.ensureBottomMargin
import com.keylesspalace.tusky.util.ensureBottomPadding
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.util.visible
import com.keylesspalace.tusky.view.showHashtagPickerDialog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FollowedTagsActivity :
    BaseActivity(),
    HashtagActionListener {

    @Inject
    lateinit var api: MastodonApi

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    private val binding by viewBinding(ActivityFollowedTagsBinding::inflate)
    private val viewModel: FollowedTagsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.run {
            setTitle(R.string.title_followed_hashtags)
            // Back button
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        binding.fab.ensureBottomMargin()
        binding.followedTagsView.ensureBottomPadding(fab = true)

        binding.fab.setOnClickListener {
            showDialog()
        }

        setupAdapter().let { adapter ->
            setupRecyclerView(adapter)

            lifecycleScope.launch {
                viewModel.pager.collectLatest { pagingData ->
                    adapter.submitData(pagingData)
                }
            }
        }
    }

    private fun setupRecyclerView(adapter: FollowedTagsAdapter) {
        binding.followedTagsView.adapter = adapter
        binding.followedTagsView.setHasFixedSize(true)
        binding.followedTagsView.layoutManager = LinearLayoutManager(this)
        binding.followedTagsView.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )
        (binding.followedTagsView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
    }

    private fun setupAdapter(): FollowedTagsAdapter = FollowedTagsAdapter(this, viewModel).apply {
        addLoadStateListener { loadState ->
            binding.followedTagsProgressBar.visible(
                loadState.refresh == LoadState.Loading && itemCount == 0
            )

            if (loadState.refresh is LoadState.Error) {
                binding.followedTagsView.hide()
                binding.followedTagsMessageView.show()
                val errorState = loadState.refresh as LoadState.Error
                binding.followedTagsMessageView.setup(errorState.error) { retry() }
                Log.w(TAG, "error loading followed hashtags", errorState.error)
            } else {
                binding.followedTagsView.show()
                binding.followedTagsMessageView.hide()
            }
        }
    }

    private fun follow(tagName: String, position: Int = -1) {
        lifecycleScope.launch {
            val snackbarText = api.followTag(tagName).fold(
                {
                    if (position == -1) {
                        viewModel.tags.add(it)
                    } else {
                        viewModel.tags.add(position, it)
                    }
                    viewModel.currentSource?.invalidate()
                    getString(R.string.follow_hashtag_success, tagName)
                },
                { t ->
                    Log.w(TAG, "failed to follow hashtag $tagName", t)
                    getString(R.string.error_following_hashtag_format, tagName)
                }
            )
            Snackbar.make(
                this@FollowedTagsActivity,
                binding.followedTagsView,
                snackbarText,
                Snackbar.LENGTH_SHORT
            )
                .show()
        }
    }

    override fun unfollow(tagName: String, position: Int) {
        lifecycleScope.launch {
            api.unfollowTag(tagName).fold(
                {
                    viewModel.tags.removeIf { tag -> tag.name == tagName }
                    Snackbar.make(
                        this@FollowedTagsActivity,
                        binding.followedTagsView,
                        getString(R.string.confirmation_hashtag_unfollowed, tagName),
                        Snackbar.LENGTH_LONG
                    )
                        .setAction(R.string.action_undo) {
                            follow(tagName, position)
                        }
                        .show()
                    viewModel.currentSource?.invalidate()
                },
                {
                    Snackbar.make(
                        this@FollowedTagsActivity,
                        binding.followedTagsView,
                        getString(
                            R.string.error_unfollowing_hashtag_format,
                            tagName
                        ),
                        Snackbar.LENGTH_SHORT
                    )
                        .show()
                }
            )
        }
    }

    override fun viewTag(tagName: String) {
        startActivity(StatusListActivity.newHashtagIntent(this, tagName))
    }

    override fun copyTagName(tagName: String) {
        copyToClipboard(
            "#$tagName",
            getString(R.string.confirmation_hashtag_copied, tagName),
        )
    }

    private fun showDialog() {
        showHashtagPickerDialog(api, R.string.dialog_follow_hashtag_title) { hashtag ->
            follow(hashtag)
        }
    }

    companion object {
        const val TAG = "FollowedTagsActivity"
    }
}
