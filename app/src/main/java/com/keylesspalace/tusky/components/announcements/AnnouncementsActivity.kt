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

package com.keylesspalace.tusky.components.announcements

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.PopupWindow
import androidx.activity.viewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.keylesspalace.tusky.*
import com.keylesspalace.tusky.adapter.EmojiAdapter
import com.keylesspalace.tusky.adapter.OnEmojiSelectedListener
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.di.ViewModelFactory
import com.keylesspalace.tusky.util.*
import com.keylesspalace.tusky.view.EmojiPicker
import kotlinx.android.synthetic.main.activity_announcements.*
import kotlinx.android.synthetic.main.toolbar_basic.*
import javax.inject.Inject

class AnnouncementsActivity : BottomSheetActivity(), AnnouncementActionListener, OnEmojiSelectedListener, Injectable {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: AnnouncementsViewModel by viewModels { viewModelFactory }

    private val adapter = AnnouncementAdapter(emptyList(), this)

    private val picker by lazy { EmojiPicker(this) }
    private val pickerDialog by lazy {
        PopupWindow(this)
                .apply {
                    contentView = picker
                    isFocusable = true
                    setOnDismissListener {
                        currentAnnouncementId = null
                    }
                }
    }
    private var currentAnnouncementId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_announcements)

        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = getString(R.string.title_announcements)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        swipeRefreshLayout.setOnRefreshListener(this::refreshAnnouncements)
        swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue)

        announcementsList.setHasFixedSize(true)
        announcementsList.layoutManager = LinearLayoutManager(this)
        val divider = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        announcementsList.addItemDecoration(divider)
        announcementsList.adapter = adapter

        viewModel.announcements.observe(this) {
            when (it) {
                is Success -> {
                    progressBar.hide()
                    swipeRefreshLayout.isRefreshing = false
                    if (it.data.isNullOrEmpty()) {
                        errorMessageView.setup(R.drawable.elephant_friend_empty, R.string.no_announcements)
                        errorMessageView.show()
                    } else {
                        errorMessageView.hide()
                    }
                    adapter.updateList(it.data ?: listOf())
                }
                is Loading -> {
                    errorMessageView.hide()
                }
                is Error -> {
                    progressBar.hide()
                    swipeRefreshLayout.isRefreshing = false
                    errorMessageView.setup(R.drawable.elephant_error, R.string.error_generic) {
                        refreshAnnouncements()
                    }
                    errorMessageView.show()
                }
            }
        }

        viewModel.emojis.observe(this) {
            picker.adapter = EmojiAdapter(it, this)
        }

        viewModel.load()
        progressBar.show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun refreshAnnouncements() {
        viewModel.load()
        swipeRefreshLayout.isRefreshing = true
    }

    override fun openReactionPicker(announcementId: String, target: View) {
        currentAnnouncementId = announcementId
        pickerDialog.showAsDropDown(target)
    }

    override fun onEmojiSelected(shortcode: String) {
        viewModel.addReaction(currentAnnouncementId!!, shortcode)
        pickerDialog.dismiss()
    }

    override fun addReaction(announcementId: String, name: String) {
        viewModel.addReaction(announcementId, name)
    }

    override fun removeReaction(announcementId: String, name: String) {
        viewModel.removeReaction(announcementId, name)
    }

    override fun onViewTag(tag: String?) {
        val intent = Intent(this, ViewTagActivity::class.java)
        intent.putExtra("hashtag", tag)
        startActivityWithSlideInAnimation(intent)
    }

    override fun onViewAccount(id: String?) {
        if (id != null) {
            viewAccount(id)
        }
    }

    override fun onViewUrl(url: String?) {
        if (url != null) {
            viewUrl(url)
        }
    }

    companion object {
        fun newIntent(context: Context) = Intent(context, AnnouncementsActivity::class.java)
    }
}
