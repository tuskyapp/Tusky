package com.keylesspalace.tusky

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import at.connyduck.calladapter.networkresult.fold
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.adapter.FollowedTagsAdapter
import com.keylesspalace.tusky.databinding.ActivityFollowedTagsBinding
import com.keylesspalace.tusky.entity.HashTag
import com.keylesspalace.tusky.interfaces.HashtagActionListener
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.net.HttpURLConnection
import javax.inject.Inject

class FollowedTagsActivity : BaseActivity(), HashtagActionListener {
    @Inject
    lateinit var api: MastodonApi

    private val binding by viewBinding(ActivityFollowedTagsBinding::inflate)

    private lateinit var tags: MutableList<HashTag>

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

        loadTags()
    }

    fun follow(tagName: String, position: Int) {
        lifecycleScope.launch {
            api.followTag(tagName).fold(
                {
                    tags.add(position, it)
                    refreshDisplay()
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
                    tags.removeAt(position)
                    refreshDisplay()
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

    private suspend fun refreshDisplay() {
        binding.followedTagsView.adapter = FollowedTagsAdapter(this, this, tags.map { it.name })
    }

    private fun loadTags() {
        binding.followedTagsMessageView.hide()
        binding.followedTagsView.hide()
        binding.followedTagsProgressBar.show()

        lifecycleScope.launch {
            api.followedTags().fold(
                {
                    tags = it.toMutableList()
                    refreshDisplay()
                    binding.followedTagsView.show()
                    binding.followedTagsProgressBar.hide()
                },
                {
                    binding.followedTagsProgressBar.hide()
                    binding.followedTagsMessageView.show()
                    if (it is IOException) {
                        binding.followedTagsMessageView.setup(
                            R.drawable.elephant_offline,
                            R.string.error_network
                        ) { loadTags() }
                    } else if (it is HttpException && it.code() == HttpURLConnection.HTTP_NOT_FOUND) {
                        binding.followedTagsMessageView.setup(
                            R.drawable.elephant_error,
                            R.string.error_following_hashtags_unsupported,
                            null
                        )
                    } else {
                        binding.followedTagsMessageView.setup(
                            R.drawable.elephant_error,
                            R.string.error_generic
                        ) { loadTags() }
                    }
                }
            )
        }
    }
}
