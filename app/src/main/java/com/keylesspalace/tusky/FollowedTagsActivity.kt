package com.keylesspalace.tusky

import android.app.AlertDialog
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import at.connyduck.calladapter.networkresult.fold
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.databinding.ActivityFollowedTagsBinding
import com.keylesspalace.tusky.entity.HashTag
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.util.hide
import com.keylesspalace.tusky.util.show
import com.keylesspalace.tusky.util.viewBinding
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.net.HttpURLConnection
import javax.inject.Inject

class FollowedTagsActivity : BaseActivity() {
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

    private suspend fun refreshDisplay() {
        binding.followedTagsView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            tags.map { it.name }
        )
        binding.followedTagsView.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                val tagName = tags[position].name
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.action_unfollow_hashtag_format, tagName))
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        lifecycleScope.launch {
                            api.unfollowTag(tagName).fold(
                                {
                                    tags.removeAt(position)
                                    refreshDisplay()
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
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
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
