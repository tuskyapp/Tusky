package com.keylesspalace.tusky.components.followedtags

import android.app.Dialog
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.AutoCompleteTextView
import androidx.activity.viewModels
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import at.connyduck.calladapter.networkresult.fold
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.StatusListActivity
import com.keylesspalace.tusky.components.compose.ComposeAutoCompleteAdapter
import com.keylesspalace.tusky.databinding.ActivityFollowedTagsBinding
import com.keylesspalace.tusky.interfaces.HashtagActionListener
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.copyToClipboard
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import com.keylesspalace.tusky.util.visible
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FollowedTagsActivity :
    BaseActivity(),
    HashtagActionListener,
    ComposeAutoCompleteAdapter.AutocompletionProvider {
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

        binding.fab.setOnClickListener {
            val dialog: DialogFragment = FollowTagDialog.newInstance()
            dialog.show(supportFragmentManager, "dialog")
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

    private fun setupAdapter(): FollowedTagsAdapter {
        return FollowedTagsAdapter(this, viewModel).apply {
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
    }

    private fun follow(tagName: String, position: Int = -1) {
        lifecycleScope.launch {
            api.followTag(tagName).fold(
                {
                    if (position == -1) {
                        viewModel.tags.add(it)
                    } else {
                        viewModel.tags.add(position, it)
                    }
                    viewModel.currentSource?.invalidate()
                },
                {
                    Snackbar.make(
                        this@FollowedTagsActivity,
                        binding.followedTagsView,
                        getString(R.string.error_following_hashtag_format, tagName),
                        Snackbar.LENGTH_SHORT
                    )
                        .show()
                }
            )
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

    override fun search(token: String): List<ComposeAutoCompleteAdapter.AutocompleteResult> {
        return viewModel.searchAutocompleteSuggestions(token)
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

    companion object {
        const val TAG = "FollowedTagsActivity"
    }

    class FollowTagDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val layout = layoutInflater.inflate(R.layout.dialog_follow_hashtag, null)
            val autoCompleteTextView = layout.findViewById<AutoCompleteTextView>(R.id.hashtag)!!
            autoCompleteTextView.setAdapter(
                ComposeAutoCompleteAdapter(
                    requireActivity() as FollowedTagsActivity,
                    animateAvatar = false,
                    animateEmojis = false,
                    showBotBadge = false
                )
            )
            autoCompleteTextView.requestFocus()
            autoCompleteTextView.setSelection(autoCompleteTextView.length())

            return MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.dialog_follow_hashtag_title)
                .setView(layout)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    (requireActivity() as FollowedTagsActivity).follow(
                        autoCompleteTextView.text.toString().removePrefix("#")
                    )
                }
                .setNegativeButton(android.R.string.cancel) { _: DialogInterface, _: Int -> }
                .create()
        }

        override fun onStart() {
            super.onStart()
            dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }

        companion object {
            fun newInstance(): FollowTagDialog = FollowTagDialog()
        }
    }
}
