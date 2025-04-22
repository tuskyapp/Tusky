/* Copyright 2021 Tusky Contributors
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

package com.keylesspalace.tusky.components.search.fragments

import android.Manifest
import android.app.DownloadManager
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import at.connyduck.calladapter.networkresult.fold
import at.connyduck.calladapter.networkresult.onFailure
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.ViewMediaActivity
import com.keylesspalace.tusky.components.compose.ComposeActivity
import com.keylesspalace.tusky.components.compose.ComposeActivity.ComposeOptions
import com.keylesspalace.tusky.components.report.ReportActivity
import com.keylesspalace.tusky.components.search.adapter.SearchStatusesAdapter
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.entity.AccountEntity
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.entity.Status.Mention
import com.keylesspalace.tusky.interfaces.AccountSelectionListener
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.settings.PrefKeys
import com.keylesspalace.tusky.util.CardViewMode
import com.keylesspalace.tusky.util.ListStatusAccessibilityDelegate
import com.keylesspalace.tusky.util.StatusDisplayOptions
import com.keylesspalace.tusky.util.copyToClipboard
import com.keylesspalace.tusky.util.openLink
import com.keylesspalace.tusky.util.startActivityWithSlideInAnimation
import com.keylesspalace.tusky.util.updateRelativeTimePeriodically
import com.keylesspalace.tusky.view.showMuteAccountDialog
import com.keylesspalace.tusky.viewdata.AttachmentViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchStatusesFragment :
    SearchFragment<StatusViewData.Concrete>(),
    StatusActionListener {
    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var preferences: SharedPreferences

    override val data: Flow<PagingData<StatusViewData.Concrete>>
        get() = viewModel.statusesFlow

    private var pendingMediaDownloads: List<String>? = null

    private val downloadAllMediaPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                pendingMediaDownloads?.let { downloadAllMedia(it) }
            } else {
                Toast.makeText(
                    context,
                    R.string.error_media_download_permission,
                    Toast.LENGTH_SHORT
                ).show()
            }
            pendingMediaDownloads = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingMediaDownloads = savedInstanceState?.getStringArrayList(PENDING_MEDIA_DOWNLOADS_STATE_KEY)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter?.let {
            updateRelativeTimePeriodically(preferences, it)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingMediaDownloads?.let {
            outState.putStringArrayList(PENDING_MEDIA_DOWNLOADS_STATE_KEY, ArrayList(it))
        }
    }

    override fun createAdapter(): PagingDataAdapter<StatusViewData.Concrete, *> {
        val statusDisplayOptions = StatusDisplayOptions(
            animateAvatars = preferences.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false),
            mediaPreviewEnabled = viewModel.mediaPreviewEnabled,
            useAbsoluteTime = preferences.getBoolean(PrefKeys.ABSOLUTE_TIME_VIEW, false),
            showBotOverlay = preferences.getBoolean(PrefKeys.SHOW_BOT_OVERLAY, true),
            useBlurhash = preferences.getBoolean(PrefKeys.USE_BLURHASH, true),
            cardViewMode = CardViewMode.NONE,
            confirmReblogs = preferences.getBoolean(PrefKeys.CONFIRM_REBLOGS, true),
            confirmFavourites = preferences.getBoolean(PrefKeys.CONFIRM_FAVOURITES, false),
            hideStats = preferences.getBoolean(PrefKeys.WELLBEING_HIDE_STATS_POSTS, false),
            animateEmojis = preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false),
            showStatsInline = preferences.getBoolean(PrefKeys.SHOW_STATS_INLINE, false),
            showSensitiveMedia = accountManager.activeAccount!!.alwaysShowSensitiveMedia,
            openSpoiler = accountManager.activeAccount!!.alwaysOpenSpoiler
        )
        val adapter = SearchStatusesAdapter(statusDisplayOptions, this)

        binding.searchRecyclerView.setAccessibilityDelegateCompat(
            ListStatusAccessibilityDelegate(binding.searchRecyclerView, this) { pos ->
                if (pos in 0 until adapter.itemCount) {
                    adapter.peek(pos)
                } else {
                    null
                }
            }
        )

        binding.searchRecyclerView.addItemDecoration(
            DividerItemDecoration(
                binding.searchRecyclerView.context,
                DividerItemDecoration.VERTICAL
            )
        )
        binding.searchRecyclerView.layoutManager =
            LinearLayoutManager(binding.searchRecyclerView.context)
        return adapter
    }

    override fun onRefresh() {
        viewModel.clearStatusCache()
        super.onRefresh()
    }

    override fun onContentHiddenChange(isShowing: Boolean, position: Int) {
        adapter?.peek(position)?.let {
            viewModel.contentHiddenChange(it, isShowing)
        }
    }

    override fun onReply(position: Int) {
        adapter?.peek(position)?.let { status ->
            reply(status)
        }
    }

    override fun onFavourite(favourite: Boolean, position: Int) {
        adapter?.peek(position)?.let { status ->
            viewModel.favorite(status, favourite)
        }
    }

    override fun onBookmark(bookmark: Boolean, position: Int) {
        adapter?.peek(position)?.let { status ->
            viewModel.bookmark(status, bookmark)
        }
    }

    override fun onMore(view: View, position: Int) {
        adapter?.peek(position)?.let {
            more(it, view, position)
        }
    }

    override fun onViewMedia(position: Int, attachmentIndex: Int, view: View?) {
        adapter?.peek(position)?.let { status ->
            when (status.attachments[attachmentIndex].type) {
                Attachment.Type.GIFV, Attachment.Type.VIDEO, Attachment.Type.IMAGE, Attachment.Type.AUDIO -> {
                    val attachments = AttachmentViewData.list(status)
                    val intent = ViewMediaActivity.newIntent(
                        context,
                        attachments,
                        attachmentIndex
                    )
                    if (view != null) {
                        val url = status.attachments[attachmentIndex].url
                        ViewCompat.setTransitionName(view, url)
                        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                            requireActivity(),
                            view,
                            url
                        )
                        startActivity(intent, options.toBundle())
                    } else {
                        startActivity(intent)
                    }
                }

                Attachment.Type.UNKNOWN -> {
                    context?.openLink(status.attachments[attachmentIndex].unknownUrl)
                }
            }
        }
    }

    override fun onViewThread(position: Int) {
        adapter?.peek(position)?.status?.let { status ->
            val actionableStatus = status.actionableStatus
            bottomSheetActivity?.viewThread(actionableStatus.id, actionableStatus.url)
        }
    }

    override fun onOpenReblog(position: Int) {
        adapter?.peek(position)?.status?.let { status ->
            bottomSheetActivity?.viewAccount(status.account.id)
        }
    }

    override fun onExpandedChange(expanded: Boolean, position: Int) {
        adapter?.peek(position)?.let {
            viewModel.expandedChange(it, expanded)
        }
    }

    override fun onLoadMore(position: Int) {
        // Not possible here
    }

    override fun onContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        adapter?.peek(position)?.let {
            viewModel.collapsedChange(it, isCollapsed)
        }
    }

    override fun onVoteInPoll(position: Int, choices: List<Int>) {
        adapter?.peek(position)?.let {
            viewModel.voteInPoll(it, choices)
        }
    }

    override fun onShowPollResults(position: Int) {
        adapter?.peek(position)?.asStatusOrNull()?.let { status ->
            viewModel.showPollResults(status)
        }
    }

    override fun clearWarningAction(position: Int) {}

    private fun removeItem(position: Int) {
        adapter?.peek(position)?.let {
            viewModel.removeItem(it)
        }
    }

    override fun onReblog(reblog: Boolean, position: Int, visibility: Status.Visibility) {
        adapter?.peek(position)?.let { status ->
            viewModel.reblog(status, reblog, visibility)
        }
    }

    override fun onUntranslate(position: Int) {
        adapter?.peek(position)?.let {
            viewModel.untranslate(it)
        }
    }

    private fun reply(status: StatusViewData.Concrete) {
        val actionableStatus = status.actionable
        val mentionedUsernames = actionableStatus.mentions.map { it.username }
            .toMutableSet()
            .apply {
                add(actionableStatus.account.username)
                remove(viewModel.activeAccount?.username)
            }

        val intent = ComposeActivity.startIntent(
            requireContext(),
            ComposeOptions(
                inReplyToId = status.actionableId,
                replyVisibility = actionableStatus.visibility,
                contentWarning = actionableStatus.spoilerText,
                mentionedUsernames = mentionedUsernames,
                replyingStatusAuthor = actionableStatus.account.localUsername,
                replyingStatusContent = status.content.toString(),
                language = actionableStatus.language,
                kind = ComposeActivity.ComposeKind.NEW
            )
        )
        bottomSheetActivity?.startActivityWithSlideInAnimation(intent)
    }

    private fun more(statusViewData: StatusViewData.Concrete, view: View, position: Int) {
        val status = statusViewData.status
        val id = status.actionableId
        val accountId = status.actionableStatus.account.id
        val accountUsername = status.actionableStatus.account.username
        val statusUrl = status.actionableStatus.url
        val loggedInAccountId = viewModel.activeAccount?.accountId

        val popup = PopupMenu(view.context, view)
        val statusIsByCurrentUser = loggedInAccountId?.equals(accountId) == true
        // Give a different menu depending on whether this is the user's own toot or not.
        if (statusIsByCurrentUser) {
            popup.inflate(R.menu.status_more_for_user)
            val menu = popup.menu
            menu.findItem(R.id.status_open_as).isVisible = !statusUrl.isNullOrBlank()
            when (status.visibility) {
                Status.Visibility.PUBLIC, Status.Visibility.UNLISTED -> {
                    val textId =
                        getString(
                            if (status.pinned) R.string.unpin_action else R.string.pin_action
                        )
                    menu.add(0, R.id.pin, 1, textId)
                }

                Status.Visibility.PRIVATE -> {
                    var reblogged = status.reblogged
                    if (status.reblog != null) reblogged = status.reblog.reblogged
                    menu.findItem(R.id.status_reblog_private).isVisible = !reblogged
                    menu.findItem(R.id.status_unreblog_private).isVisible = reblogged
                }

                Status.Visibility.UNKNOWN, Status.Visibility.DIRECT -> {
                } // Ignore
            }
        } else {
            popup.inflate(R.menu.status_more)
            val menu = popup.menu
            menu.findItem(R.id.status_download_media).isVisible = status.attachments.isNotEmpty()
        }

        val openAsItem = popup.menu.findItem(R.id.status_open_as)
        val openAsText = bottomSheetActivity?.openAsText
        if (openAsText == null) {
            openAsItem.isVisible = false
        } else {
            openAsItem.title = openAsText
        }

        val mutable =
            statusIsByCurrentUser || accountIsInMentions(viewModel.activeAccount, status.mentions)
        val muteConversationItem = popup.menu.findItem(R.id.status_mute_conversation).apply {
            isVisible = mutable
        }
        if (mutable) {
            muteConversationItem.setTitle(
                if (status.muted) {
                    R.string.action_unmute_conversation
                } else {
                    R.string.action_mute_conversation
                }
            )
        }

        // translation not there for your own posts
        popup.menu.findItem(R.id.status_translate)?.let { translateItem ->
            translateItem.isVisible =
                !status.language.equals(Locale.getDefault().language, ignoreCase = true) &&
                viewModel.supportsTranslation()
            translateItem.setTitle(if (statusViewData.translation != null) R.string.action_show_original else R.string.action_translate)
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.post_share_content -> {
                    val statusToShare: Status = status.actionableStatus

                    val sendIntent = Intent()
                    sendIntent.action = Intent.ACTION_SEND

                    val stringToShare = statusToShare.account.username +
                        " - " +
                        statusToShare.content
                    sendIntent.putExtra(Intent.EXTRA_TEXT, stringToShare)
                    sendIntent.type = "text/plain"
                    startActivity(
                        Intent.createChooser(
                            sendIntent,
                            resources.getText(R.string.send_post_content_to)
                        )
                    )
                    return@setOnMenuItemClickListener true
                }

                R.id.post_share_link -> {
                    val sendIntent = Intent()
                    sendIntent.action = Intent.ACTION_SEND
                    sendIntent.putExtra(Intent.EXTRA_TEXT, statusUrl)
                    sendIntent.type = "text/plain"
                    startActivity(
                        Intent.createChooser(
                            sendIntent,
                            resources.getText(R.string.send_post_link_to)
                        )
                    )
                    return@setOnMenuItemClickListener true
                }

                R.id.status_copy_link -> {
                    statusUrl?.let { requireActivity().copyToClipboard(it, getString(R.string.url_copied)) }
                    return@setOnMenuItemClickListener true
                }

                R.id.status_open_as -> {
                    showOpenAsDialog(statusUrl!!, item.title)
                    return@setOnMenuItemClickListener true
                }

                R.id.status_download_media -> {
                    requestDownloadAllMedia(status)
                    return@setOnMenuItemClickListener true
                }

                R.id.status_mute_conversation -> {
                    adapter?.peek(position)?.let { foundStatus ->
                        viewModel.muteConversation(foundStatus, !status.muted)
                    }
                    return@setOnMenuItemClickListener true
                }

                R.id.status_mute -> {
                    onMute(accountId, accountUsername)
                    return@setOnMenuItemClickListener true
                }

                R.id.status_block -> {
                    onBlock(accountId, accountUsername)
                    return@setOnMenuItemClickListener true
                }

                R.id.status_report -> {
                    openReportPage(accountId, accountUsername, id)
                    return@setOnMenuItemClickListener true
                }

                R.id.status_unreblog_private -> {
                    onReblog(false, position)
                    return@setOnMenuItemClickListener true
                }

                R.id.status_reblog_private -> {
                    onReblog(true, position)
                    return@setOnMenuItemClickListener true
                }

                R.id.status_delete -> {
                    showConfirmDeleteDialog(id, position)
                    return@setOnMenuItemClickListener true
                }

                R.id.status_delete_and_redraft -> {
                    showConfirmEditDialog(id, position, status)
                    return@setOnMenuItemClickListener true
                }

                R.id.status_edit -> {
                    editStatus(id, status)
                    return@setOnMenuItemClickListener true
                }

                R.id.pin -> {
                    viewModel.pinAccount(status, !status.pinned)
                    return@setOnMenuItemClickListener true
                }

                R.id.status_translate -> {
                    if (statusViewData.translation != null) {
                        viewModel.untranslate(statusViewData)
                    } else {
                        viewLifecycleOwner.lifecycleScope.launch {
                            viewModel.translate(statusViewData)
                                .onFailure {
                                    Snackbar.make(
                                        requireView(),
                                        getString(R.string.ui_error_translate, it.message),
                                        Snackbar.LENGTH_LONG
                                    ).show()
                                }
                        }
                    }
                }
            }
            false
        }
        popup.show()
    }

    private fun onBlock(accountId: String, accountUsername: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(getString(R.string.dialog_block_warning, accountUsername))
            .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.blockAccount(accountId) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onMute(accountId: String, accountUsername: String) {
        showMuteAccountDialog(
            this.requireActivity(),
            accountUsername
        ) { notifications, duration ->
            viewModel.muteAccount(accountId, notifications, duration)
        }
    }

    private fun accountIsInMentions(account: AccountEntity?, mentions: List<Mention>): Boolean = mentions.firstOrNull {
        account?.username == it.username && account.domain == Uri.parse(it.url)?.host
    } != null

    private fun showOpenAsDialog(statusUrl: String, dialogTitle: CharSequence?) {
        bottomSheetActivity?.showAccountChooserDialog(
            dialogTitle,
            false,
            object : AccountSelectionListener {
                override fun onAccountSelected(account: AccountEntity) {
                    bottomSheetActivity?.openAsAccount(statusUrl, account)
                }
            }
        )
    }

    private fun downloadAllMedia(mediaUrls: List<String>) {
        Toast.makeText(context, R.string.downloading_media, Toast.LENGTH_SHORT).show()
        val downloadManager: DownloadManager = requireContext().getSystemService()!!

        for (url in mediaUrls) {
            val uri = Uri.parse(url)
            val request = DownloadManager.Request(uri)
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                uri.lastPathSegment
            )
            downloadManager.enqueue(request)
        }
    }

    private fun requestDownloadAllMedia(status: Status) {
        if (status.attachments.isEmpty()) {
            return
        }
        val mediaUrls = status.attachments.map { it.url }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            pendingMediaDownloads = mediaUrls
            downloadAllMediaPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            downloadAllMedia(mediaUrls)
        }
    }

    private fun openReportPage(accountId: String, accountUsername: String, statusId: String) {
        startActivity(
            ReportActivity.getIntent(requireContext(), accountId, accountUsername, statusId)
        )
    }

    private fun showConfirmDeleteDialog(id: String, position: Int) {
        context?.let {
            MaterialAlertDialogBuilder(it)
                .setMessage(R.string.dialog_delete_post_warning)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    viewModel.deleteStatusAsync(id)
                    removeItem(position)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun showConfirmEditDialog(id: String, position: Int, status: Status) {
        context?.let { context ->
            MaterialAlertDialogBuilder(context)
                .setMessage(R.string.dialog_redraft_post_warning)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        viewModel.deleteStatusAsync(id).await().fold(
                            { deletedStatus ->
                                removeItem(position)

                                val redraftStatus = if (deletedStatus.isEmpty) {
                                    status.toDeletedStatus()
                                } else {
                                    deletedStatus
                                }

                                val intent = ComposeActivity.startIntent(
                                    context,
                                    ComposeOptions(
                                        content = redraftStatus.text.orEmpty(),
                                        inReplyToId = redraftStatus.inReplyToId,
                                        visibility = redraftStatus.visibility,
                                        contentWarning = redraftStatus.spoilerText,
                                        mediaAttachments = redraftStatus.attachments,
                                        sensitive = redraftStatus.sensitive,
                                        poll = redraftStatus.poll?.toNewPoll(status.createdAt),
                                        language = redraftStatus.language,
                                        kind = ComposeActivity.ComposeKind.NEW
                                    )
                                )
                                startActivity(intent)
                            },
                            { error ->
                                Log.w("SearchStatusesFragment", "error deleting status", error)
                                Toast.makeText(
                                    context,
                                    R.string.error_generic,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun editStatus(id: String, status: Status) {
        viewLifecycleOwner.lifecycleScope.launch {
            mastodonApi.statusSource(id).fold(
                { source ->
                    val composeOptions = ComposeOptions(
                        content = source.text,
                        inReplyToId = status.inReplyToId,
                        visibility = status.visibility,
                        contentWarning = source.spoilerText,
                        mediaAttachments = status.attachments,
                        sensitive = status.sensitive,
                        language = status.language,
                        statusId = source.id,
                        poll = status.poll?.toNewPoll(status.createdAt),
                        kind = ComposeActivity.ComposeKind.EDIT_POSTED
                    )
                    startActivity(ComposeActivity.startIntent(requireContext(), composeOptions))
                },
                {
                    Snackbar.make(
                        requireView(),
                        getString(R.string.error_status_source_load),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }

    companion object {
        private const val PENDING_MEDIA_DOWNLOADS_STATE_KEY = "pending_media_downloads"

        fun newInstance() = SearchStatusesFragment()
    }
}
