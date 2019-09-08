/* Copyright 2019 Joel Pyska
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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.paging.PagedList
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.keylesspalace.tusky.*
import com.keylesspalace.tusky.components.report.ReportActivity
import com.keylesspalace.tusky.components.search.adapter.SearchStatusesAdapter
import com.keylesspalace.tusky.db.AccountEntity
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.interfaces.AccountSelectionListener
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.util.NetworkState
import com.keylesspalace.tusky.viewdata.AttachmentViewData
import com.keylesspalace.tusky.viewdata.StatusViewData
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider.from
import com.uber.autodispose.autoDisposable
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.fragment_search.*
import java.util.*

class SearchStatusesFragment : SearchFragment<Pair<Status, StatusViewData.Concrete>>(), StatusActionListener {

    override val networkStateRefresh: LiveData<NetworkState>
        get() = viewModel.networkStateStatusRefresh
    override val networkState: LiveData<NetworkState>
        get() = viewModel.networkStateStatus
    override val data: LiveData<PagedList<Pair<Status, StatusViewData.Concrete>>>
        get() = viewModel.statuses

    override fun createAdapter(): PagedListAdapter<Pair<Status, StatusViewData.Concrete>, *> {
        val preferences = PreferenceManager.getDefaultSharedPreferences(searchRecyclerView.context)
        val useAbsoluteTime = preferences.getBoolean("absoluteTimeView", false)
        val showBotOverlay = preferences.getBoolean("showBotOverlay", true)
        val animateAvatar = preferences.getBoolean("animateGifAvatars", false)

        searchRecyclerView.addItemDecoration(DividerItemDecoration(searchRecyclerView.context, DividerItemDecoration.VERTICAL))
        searchRecyclerView.layoutManager = LinearLayoutManager(searchRecyclerView.context)
        return SearchStatusesAdapter(useAbsoluteTime, viewModel.mediaPreviewEnabled, showBotOverlay, animateAvatar, this)
    }


    override fun onContentHiddenChange(isShowing: Boolean, position: Int) {
        (adapter as? SearchStatusesAdapter)?.getItem(position)?.let {
            viewModel.contentHiddenChange(it, isShowing)
        }
    }

    override fun onReply(position: Int) {
        (adapter as? SearchStatusesAdapter)?.getItem(position)?.first?.let { status ->
            reply(status)
        }
    }

    override fun onFavourite(favourite: Boolean, position: Int) {
        (adapter as? SearchStatusesAdapter)?.getItem(position)?.let { status ->
            viewModel.favorite(status, favourite)
        }
    }

    override fun onMore(view: View, position: Int) {
        (adapter as? SearchStatusesAdapter)?.getItem(position)?.first?.let {
            more(it, view, position)
        }
    }

    override fun onViewMedia(position: Int, attachmentIndex: Int, view: View?) {
        (adapter as? SearchStatusesAdapter)?.getItem(position)?.first?.actionableStatus?.let { actionable ->
            when (actionable.attachments[attachmentIndex].type) {
                Attachment.Type.GIFV, Attachment.Type.VIDEO, Attachment.Type.IMAGE, Attachment.Type.AUDIO -> {
                    val attachments = AttachmentViewData.list(actionable)
                    val intent = ViewMediaActivity.newIntent(context, attachments,
                            attachmentIndex)
                    if (view != null) {
                        val url = actionable.attachments[attachmentIndex].url
                        ViewCompat.setTransitionName(view, url)
                        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(requireActivity(),
                                view, url)
                        startActivity(intent, options.toBundle())
                    } else {
                        startActivity(intent)
                    }
                }
                Attachment.Type.UNKNOWN -> {
                }
            }

        }

    }

    override fun onViewThread(position: Int) {
        (adapter as? SearchStatusesAdapter)?.getItem(position)?.first?.let { status ->
            val actionableStatus = status.actionableStatus
            bottomSheetActivity?.viewThread(actionableStatus.id, actionableStatus.url)
        }
    }

    override fun onOpenReblog(position: Int) {
        (adapter as? SearchStatusesAdapter)?.getItem(position)?.first?.let { status ->
            bottomSheetActivity?.viewAccount(status.account.id)
        }
    }

    override fun onExpandedChange(expanded: Boolean, position: Int) {
        (adapter as? SearchStatusesAdapter)?.getItem(position)?.let {
            viewModel.expandedChange(it, expanded)
        }
    }

    override fun onLoadMore(position: Int) {
        //Ignore
    }

    override fun onContentCollapsedChange(isCollapsed: Boolean, position: Int) {
        (adapter as? SearchStatusesAdapter)?.getItem(position)?.let {
            viewModel.collapsedChange(it, isCollapsed)
        }
    }

    override fun onVoteInPoll(position: Int, choices: MutableList<Int>) {
        (adapter as? SearchStatusesAdapter)?.getItem(position)?.let {
            viewModel.voteInPoll(it, choices)
        }
    }

    private fun removeItem(position: Int) {
        (adapter as? SearchStatusesAdapter)?.getItem(position)?.let {
            viewModel.removeItem(it)
        }
    }

    override fun onReblog(reblog: Boolean, position: Int) {
        (adapter as? SearchStatusesAdapter)?.getItem(position)?.let { status ->
            viewModel.reblog(status, reblog)
        }
    }

    companion object {
        fun newInstance() = SearchStatusesFragment()
    }

    private fun reply(status: Status) {
        val inReplyToId = status.actionableId
        val actionableStatus = status.actionableStatus
        val replyVisibility = actionableStatus.visibility
        val contentWarning = actionableStatus.spoilerText
        val mentions = actionableStatus.mentions
        val mentionedUsernames = LinkedHashSet<String>()
        mentionedUsernames.add(actionableStatus.account.username)
        val loggedInUsername = viewModel.activeAccount?.username
        for ((_, _, username) in mentions) {
            mentionedUsernames.add(username)
        }
        mentionedUsernames.remove(loggedInUsername)
        val intent = ComposeActivity.IntentBuilder()
                .inReplyToId(inReplyToId)
                .replyVisibility(replyVisibility)
                .contentWarning(contentWarning)
                .mentionedUsernames(mentionedUsernames)
                .replyingStatusAuthor(actionableStatus.account.localUsername)
                .replyingStatusContent(actionableStatus.content.toString())
                .build(context)
        requireActivity().startActivity(intent)
    }

    private fun more(status: Status, view: View, position: Int) {
        val id = status.actionableId
        val accountId = status.actionableStatus.account.id
        val accountUsername = status.actionableStatus.account.username
        val statusUrl = status.actionableStatus.url
        val accounts = viewModel.getAllAccountsOrderedByActive()
        var openAsTitle: String? = null

        val loggedInAccountId = viewModel.activeAccount?.accountId

        val popup = PopupMenu(view.context, view)
        // Give a different menu depending on whether this is the user's own toot or not.
        if (loggedInAccountId == null || loggedInAccountId != accountId) {
            popup.inflate(R.menu.status_more)
            val menu = popup.menu
            menu.findItem(R.id.status_download_media).isVisible = status.attachments.isNotEmpty()
        } else {
            popup.inflate(R.menu.status_more_for_user)
            val menu = popup.menu
            menu.findItem(R.id.status_open_as).isVisible = !statusUrl.isNullOrBlank()
            when (status.visibility) {
                Status.Visibility.PUBLIC, Status.Visibility.UNLISTED -> {
                    val textId = getString(if (status.isPinned()) R.string.unpin_action else R.string.pin_action)
                    menu.add(0, R.id.pin, 1, textId)
                }
                Status.Visibility.PRIVATE -> {
                    var reblogged = status.reblogged
                    if (status.reblog != null) reblogged = status.reblog.reblogged
                    menu.findItem(R.id.status_reblog_private).isVisible = !reblogged
                    menu.findItem(R.id.status_unreblog_private).isVisible = reblogged
                }
                Status.Visibility.UNKNOWN, Status.Visibility.DIRECT -> {
                } //Ignore
            }
        }

        val menu = popup.menu
        val openAsItem = menu.findItem(R.id.status_open_as)
        when (accounts.size) {
            0, 1 -> openAsItem.isVisible = false
            2 -> for (account in accounts) {
                if (account !== viewModel.activeAccount) {
                    openAsTitle = String.format(getString(R.string.action_open_as), account.fullName)
                    break
                }
            }
            else -> openAsTitle = String.format(getString(R.string.action_open_as), "…")
        }
        openAsItem.title = openAsTitle

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.status_share_content -> {
                    var statusToShare: Status? = status
                    if (statusToShare!!.reblog != null) statusToShare = statusToShare.reblog

                    val sendIntent = Intent()
                    sendIntent.action = Intent.ACTION_SEND

                    val stringToShare = statusToShare!!.account.username +
                            " - " +
                            statusToShare.content.toString()
                    sendIntent.putExtra(Intent.EXTRA_TEXT, stringToShare)
                    sendIntent.type = "text/plain"
                    startActivity(Intent.createChooser(sendIntent, resources.getText(R.string.send_status_content_to)))
                    return@setOnMenuItemClickListener true
                }
                R.id.status_share_link -> {
                    val sendIntent = Intent()
                    sendIntent.action = Intent.ACTION_SEND
                    sendIntent.putExtra(Intent.EXTRA_TEXT, statusUrl)
                    sendIntent.type = "text/plain"
                    startActivity(Intent.createChooser(sendIntent, resources.getText(R.string.send_status_link_to)))
                    return@setOnMenuItemClickListener true
                }
                R.id.status_copy_link -> {
                    val clipboard = activity!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(null, statusUrl)
                    clipboard.primaryClip = clip
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
                R.id.status_mute -> {
                    viewModel.muteAcount(accountId)
                    return@setOnMenuItemClickListener true
                }
                R.id.status_block -> {
                    viewModel.blockAccount(accountId)
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
                R.id.pin -> {
                    viewModel.pinAccount(status, !status.isPinned())
                    return@setOnMenuItemClickListener true
                }
            }
            false
        }
        popup.show()
    }

    private fun showOpenAsDialog(statusUrl: String, dialogTitle: CharSequence) {
        bottomSheetActivity?.showAccountChooserDialog(dialogTitle, false, object : AccountSelectionListener {
            override fun onAccountSelected(account: AccountEntity) {
                openAsAccount(statusUrl, account)
            }
        })
    }

    private fun openAsAccount(statusUrl: String, account: AccountEntity) {
        viewModel.activeAccount = account
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        intent.putExtra(MainActivity.STATUS_URL, statusUrl)
        startActivity(intent)
        (activity as BaseActivity).finishWithoutSlideOutAnimation()
    }

    private fun downloadAllMedia(status: Status) {
        Toast.makeText(context, R.string.downloading_media, Toast.LENGTH_SHORT).show()
        for ((_, url) in status.attachments) {
            val uri = Uri.parse(url)
            val filename = uri.lastPathSegment

            val downloadManager = activity!!.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(uri)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            downloadManager.enqueue(request)
        }
    }

    private fun requestDownloadAllMedia(status: Status) {
        val permissions = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        (activity as BaseActivity).requestPermissions(permissions) { _, grantResults ->
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                downloadAllMedia(status)
            } else {
                Toast.makeText(context, R.string.error_media_download_permission, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openReportPage(accountId: String, accountUsername: String, statusId: String) {
        startActivity(ReportActivity.getIntent(requireContext(), accountId, accountUsername, statusId))
    }

    private fun showConfirmDeleteDialog(id: String, position: Int) {
        context?.let {
            AlertDialog.Builder(it)
                    .setMessage(R.string.dialog_delete_toot_warning)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        viewModel.deleteStatus(id)
                        removeItem(position)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
        }
    }

    private fun showConfirmEditDialog(id: String, position: Int, status: Status) {
        activity?.let {
            AlertDialog.Builder(it)
                    .setMessage(R.string.dialog_redraft_toot_warning)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        viewModel.deleteStatus(id)
                                .observeOn(AndroidSchedulers.mainThread())
                                .autoDisposable(from(this, Lifecycle.Event.ON_DESTROY))
                                .subscribe ({ deletedStatus ->
                                    removeItem(position)

                                    val redraftStatus = if(deletedStatus.isEmpty()) {
                                        status.toDeletedStatus()
                                    } else {
                                        deletedStatus
                                    }

                                    val intent = ComposeActivity.IntentBuilder()
                                            .tootText(redraftStatus.text)
                                            .inReplyToId(redraftStatus.inReplyToId)
                                            .visibility(redraftStatus.visibility)
                                            .contentWarning(redraftStatus.spoilerText)
                                            .mediaAttachments(redraftStatus.attachments)
                                            .sensitive(redraftStatus.sensitive)
                                            .poll(redraftStatus.poll?.toNewPoll(status.createdAt))
                                            .build(context)
                                    startActivity(intent)
                                }, { error ->
                                    Log.w("SearchStatusesFragment", "error deleting status", error)
                                    Toast.makeText(context, R.string.error_generic, Toast.LENGTH_SHORT).show()
                                })

                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
        }
    }

}
