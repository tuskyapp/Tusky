/* Copyright 2017 Andrew Dawson
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
package com.keylesspalace.tusky.fragment

import android.Manifest
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import at.connyduck.calladapter.networkresult.fold
import autodispose2.AutoDispose
import autodispose2.androidx.lifecycle.AndroidLifecycleScopeProvider
import com.google.android.material.snackbar.Snackbar
import com.keylesspalace.tusky.BaseActivity
import com.keylesspalace.tusky.BottomSheetActivity
import com.keylesspalace.tusky.PostLookupFallbackBehavior
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.StatusListActivity.Companion.newHashtagIntent
import com.keylesspalace.tusky.ViewMediaActivity.Companion.newIntent
import com.keylesspalace.tusky.components.compose.ComposeActivity
import com.keylesspalace.tusky.components.compose.ComposeActivity.Companion.startIntent
import com.keylesspalace.tusky.components.compose.ComposeActivity.ComposeOptions
import com.keylesspalace.tusky.components.report.ReportActivity.Companion.getIntent
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.di.Injectable
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.interfaces.AccountSelectionListener
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.usecase.TimelineCases
import com.keylesspalace.tusky.util.openLink
import com.keylesspalace.tusky.util.parseAsMastodonHtml
import com.keylesspalace.tusky.view.showMuteAccountDialog
import com.keylesspalace.tusky.viewdata.AttachmentViewData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.launch
import javax.inject.Inject

/* Note from Andrew on Jan. 22, 2017: This class is a design problem for me, so I left it with an
 * awkward name. TimelineFragment and NotificationFragment have significant overlap but the nature
 * of that is complicated by how they're coupled with Status and Notification and the corresponding
 * adapters. I feel like the profile pages and thread viewer, which I haven't made yet, will also
 * overlap functionality. So, I'm momentarily leaving it and hopefully working on those will clear
 * up what needs to be where. */
abstract class SFragment : Fragment(), Injectable {
    protected abstract fun removeItem(position: Int)
    protected abstract fun onReblog(reblog: Boolean, position: Int)
    private lateinit var bottomSheetActivity: BottomSheetActivity

    @Inject
    lateinit var mastodonApi: MastodonApi

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var timelineCases: TimelineCases

    override fun startActivity(intent: Intent) {
        super.startActivity(intent)
        requireActivity().overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        bottomSheetActivity = if (context is BottomSheetActivity) {
            context
        } else {
            throw IllegalStateException("Fragment must be attached to a BottomSheetActivity!")
        }
    }

    protected fun openReblog(status: Status?) {
        if (status == null) return
        bottomSheetActivity.viewAccount(status.account.id)
    }

    protected fun viewThread(statusId: String?, statusUrl: String?) {
        bottomSheetActivity.viewThread(statusId!!, statusUrl)
    }

    protected fun viewAccount(accountId: String?) {
        bottomSheetActivity.viewAccount(accountId!!)
    }

    open fun onViewUrl(url: String) {
        bottomSheetActivity.viewUrl(url, PostLookupFallbackBehavior.OPEN_IN_BROWSER)
    }

    protected fun reply(status: Status) {
        val actionableStatus = status.actionableStatus
        val account = actionableStatus.account
        var loggedInUsername: String? = null
        val activeAccount = accountManager.activeAccount
        if (activeAccount != null) {
            loggedInUsername = activeAccount.username
        }
        val mentionedUsernames = LinkedHashSet(
            listOf(account.username) + actionableStatus.mentions.map { it.username }
        ).apply { remove(loggedInUsername) }

        val composeOptions = ComposeOptions(
            inReplyToId = status.actionableId,
            replyVisibility = actionableStatus.visibility,
            contentWarning = actionableStatus.spoilerText,
            mentionedUsernames = mentionedUsernames,
            replyingStatusAuthor = account.localUsername,
            replyingStatusContent = actionableStatus.content.parseAsMastodonHtml().toString(),
            language = actionableStatus.language,
            kind = ComposeActivity.ComposeKind.NEW
        )

        val intent = startIntent(requireContext(), composeOptions)
        requireActivity().startActivity(intent)
    }

    protected fun more(status: Status, view: View, position: Int) {
        val id = status.actionableId
        val accountId = status.actionableStatus.account.id
        val accountUsername = status.actionableStatus.account.username
        val statusUrl = status.actionableStatus.url
        var loggedInAccountId: String? = null
        val activeAccount = accountManager.activeAccount
        if (activeAccount != null) {
            loggedInAccountId = activeAccount.accountId
        }
        val popup = PopupMenu(requireContext(), view)
        // Give a different menu depending on whether this is the user's own toot or not.
        val statusIsByCurrentUser = loggedInAccountId != null && loggedInAccountId == accountId
        if (statusIsByCurrentUser) {
            popup.inflate(R.menu.status_more_for_user)
            val menu = popup.menu
            when (status.visibility) {
                Status.Visibility.PUBLIC, Status.Visibility.UNLISTED -> {
                    menu.add(0, R.id.pin, 1, getString(if (status.isPinned()) R.string.unpin_action else R.string.pin_action))
                }
                Status.Visibility.PRIVATE -> {
                    val reblogged = status.reblog?.reblogged ?: status.reblogged
                    menu.findItem(R.id.status_reblog_private).isVisible = !reblogged
                    menu.findItem(R.id.status_unreblog_private).isVisible = reblogged
                }
                else -> {}
            }
        } else {
            popup.inflate(R.menu.status_more)
            popup.menu.findItem(R.id.status_download_media).isVisible = status.attachments.isNotEmpty()
        }
        val menu = popup.menu
        val openAsItem = menu.findItem(R.id.status_open_as)
        val openAsText = (activity as BaseActivity?)?.openAsText
        if (openAsText == null) {
            openAsItem.isVisible = false
        } else {
            openAsItem.title = openAsText
        }
        val muteConversationItem = menu.findItem(R.id.status_mute_conversation)
        val mutable = statusIsByCurrentUser || accountIsInMentions(activeAccount, status.mentions)
        muteConversationItem.isVisible = mutable
        if (mutable) {
            muteConversationItem.setTitle(
                if (status.muted != true) {
                    R.string.action_mute_conversation
                } else {
                    R.string.action_unmute_conversation
                }
            )
        }
        popup.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.post_share_content -> {
                    val statusToShare = status.reblog ?: status
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "${statusToShare.account.username} - ${statusToShare.content.parseAsMastodonHtml()}"
                        )
                        putExtra(Intent.EXTRA_SUBJECT, statusUrl)
                    }
                    startActivity(
                        Intent.createChooser(
                            sendIntent,
                            resources.getText(R.string.send_post_content_to)
                        )
                    )
                    return@setOnMenuItemClickListener true
                }
                R.id.post_share_link -> {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, statusUrl)
                        type = "text/plain"
                    }
                    startActivity(
                        Intent.createChooser(
                            sendIntent,
                            resources.getText(R.string.send_post_link_to)
                        )
                    )
                    return@setOnMenuItemClickListener true
                }
                R.id.status_copy_link -> {
                    (requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).apply {
                        setPrimaryClip(ClipData.newPlainText(null, statusUrl))
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.status_open_as -> {
                    showOpenAsDialog(statusUrl, item.title)
                    return@setOnMenuItemClickListener true
                }
                R.id.status_download_media -> {
                    requestDownloadAllMedia(status)
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
                    timelineCases.pin(status.id, !status.isPinned())
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnError { e: Throwable ->
                            val message = e.message ?: getString(if (status.isPinned()) R.string.failed_to_unpin else R.string.failed_to_pin)
                            Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()
                        }
                        .to(
                            AutoDispose.autoDisposable(
                                AndroidLifecycleScopeProvider.from(this, Lifecycle.Event.ON_DESTROY)
                            )
                        )
                        .subscribe()
                    return@setOnMenuItemClickListener true
                }
                R.id.status_mute_conversation -> {
                    timelineCases.muteConversation(status.id, status.muted != true)
                        .onErrorReturnItem(status)
                        .observeOn(AndroidSchedulers.mainThread())
                        .to(
                            AutoDispose.autoDisposable(
                                AndroidLifecycleScopeProvider.from(this, Lifecycle.Event.ON_DESTROY)
                            )
                        )
                        .subscribe()
                    return@setOnMenuItemClickListener true
                }
            }
            false
        }
        popup.show()
    }

    private fun onMute(accountId: String, accountUsername: String) {

        showMuteAccountDialog(this.requireActivity(), accountUsername) { notifications: Boolean?, duration: Int? ->
            lifecycleScope.launch {
                timelineCases.mute(accountId, notifications == true, duration)
            }
        }
    }

    private fun onBlock(accountId: String, accountUsername: String) {
        AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.dialog_block_warning, accountUsername))
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                lifecycleScope.launch {
                    timelineCases.block(accountId)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    protected fun viewMedia(urlIndex: Int, attachments: List<AttachmentViewData>, view: View?) {
        val (attachment) = attachments[urlIndex]
        when (attachment.type) {
            Attachment.Type.GIFV, Attachment.Type.VIDEO, Attachment.Type.IMAGE, Attachment.Type.AUDIO -> {
                val intent = newIntent(context, attachments, urlIndex)
                if (view != null) {
                    val url = attachment.url
                    view.transitionName = url
                    val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        requireActivity(),
                        view, url
                    )
                    startActivity(intent, options.toBundle())
                } else {
                    startActivity(intent)
                }
            }
            Attachment.Type.UNKNOWN -> {
                requireContext().openLink(attachment.url)
            }
        }
    }

    protected fun viewTag(tag: String) {
        startActivity(newHashtagIntent(requireContext(), tag))
    }

    private fun openReportPage(accountId: String, accountUsername: String, statusId: String) {
        startActivity(getIntent(requireContext(), accountId, accountUsername, statusId))
    }

    private fun showConfirmDeleteDialog(id: String, position: Int) {
        AlertDialog.Builder(requireActivity())
            .setMessage(R.string.dialog_delete_post_warning)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                lifecycleScope.launch {
                    val result = timelineCases.delete(id).exceptionOrNull()
                    if (result != null) {
                        Log.w("SFragment", "error deleting status", result)
                        Toast.makeText(context, R.string.error_generic, Toast.LENGTH_SHORT).show()
                    }
                    // XXX: Removes the item even if there was an error. This is probably not
                    // correct (see similar code in showConfirmEditDialog() which only
                    // removes the item if the timelineCases.delete() call succeeded.
                    //
                    // Either way, this logic should be in the view model.
                    removeItem(position)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showConfirmEditDialog(id: String, position: Int, status: Status) {
        if (activity == null) {
            return
        }
        AlertDialog.Builder(requireActivity())
            .setMessage(R.string.dialog_redraft_post_warning)
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                lifecycleScope.launch {
                    timelineCases.delete(id).fold(
                        { deletedStatus ->
                            removeItem(position)
                            val sourceStatus = if (deletedStatus.isEmpty()) {
                                status.toDeletedStatus()
                            } else {
                                deletedStatus
                            }
                            val composeOptions = ComposeOptions(
                                content = sourceStatus.text,
                                inReplyToId = sourceStatus.inReplyToId,
                                visibility = sourceStatus.visibility,
                                contentWarning = sourceStatus.spoilerText,
                                mediaAttachments = sourceStatus.attachments,
                                sensitive = sourceStatus.sensitive,
                                modifiedInitialState = true,
                                language = sourceStatus.language,
                                poll = sourceStatus.poll?.toNewPoll(sourceStatus.createdAt),
                                kind = ComposeActivity.ComposeKind.NEW
                            )
                            startActivity(startIntent(requireContext(), composeOptions))
                        },
                        { error: Throwable? ->
                            Log.w("SFragment", "error deleting status", error)
                            Toast.makeText(context, R.string.error_generic, Toast.LENGTH_SHORT)
                                .show()
                        }
                    )
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun editStatus(id: String, status: Status) {
        lifecycleScope.launch {
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
                    startActivity(startIntent(requireContext(), composeOptions))
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

    private fun showOpenAsDialog(statusUrl: String?, dialogTitle: CharSequence?) {
        if (statusUrl == null) {
            return
        }

        (activity as BaseActivity).apply {
            showAccountChooserDialog(
                dialogTitle,
                false,
                object : AccountSelectionListener {
                    override fun onAccountSelected(account: AccountEntity) {
                        openAsAccount(statusUrl, account)
                    }
                }
            )
        }
    }

    private fun downloadAllMedia(status: Status) {
        Toast.makeText(context, R.string.downloading_media, Toast.LENGTH_SHORT).show()
        val downloadManager = requireActivity().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        for ((_, url) in status.attachments) {
            val uri = Uri.parse(url)
            downloadManager.enqueue(
                DownloadManager.Request(uri).apply {
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, uri.lastPathSegment)
                }
            )
        }
    }

    private fun requestDownloadAllMedia(status: Status) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val permissions = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            (activity as BaseActivity).requestPermissions(permissions) { _: Array<String?>?, grantResults: IntArray ->
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    downloadAllMedia(status)
                } else {
                    Toast.makeText(
                        context,
                        R.string.error_media_download_permission,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            downloadAllMedia(status)
        }
    }

    companion object {
        private const val TAG = "SFragment"
        private fun accountIsInMentions(account: AccountEntity?, mentions: List<Status.Mention>): Boolean {
            return mentions.any { mention ->
                account?.username == mention.username && account.domain == Uri.parse(mention.url)?.host
            }
        }
    }
}
