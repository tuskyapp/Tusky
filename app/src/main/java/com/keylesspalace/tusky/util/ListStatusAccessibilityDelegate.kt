package com.keylesspalace.tusky.util

import android.content.Context
import android.os.Bundle
import android.text.Spannable
import android.text.style.URLSpan
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerViewAccessibilityDelegate
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.adapter.StatusBaseViewHolder
import com.keylesspalace.tusky.entity.Status.Companion.MAX_MEDIA_ATTACHMENTS
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.viewdata.StatusViewData
import kotlin.math.min

// Not using lambdas because there's boxing of int then
interface StatusProvider {
    fun getStatus(pos: Int): StatusViewData?
}

class ListStatusAccessibilityDelegate(
        private val recyclerView: RecyclerView,
        private val statusActionListener: StatusActionListener,
        private val statusProvider: StatusProvider
) : RecyclerViewAccessibilityDelegate(recyclerView) {
    private val a11yManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as AccessibilityManager

    override fun getItemDelegate(): AccessibilityDelegateCompat = itemDelegate

    private val context: Context get() = recyclerView.context

    private val itemDelegate = object : RecyclerViewAccessibilityDelegate.ItemDelegate(this) {
        override fun onInitializeAccessibilityNodeInfo(host: View,
                                                       info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)

            val pos = recyclerView.getChildAdapterPosition(host)
            val status = statusProvider.getStatus(pos) ?: return
            if (status is StatusViewData.Concrete) {
                if (!status.spoilerText.isNullOrEmpty()) {
                    info.addAction(if (status.isExpanded) collapseCwAction else expandCwAction)
                }

                info.addAction(replyAction)

                if (status.rebloggingEnabled) {
                    info.addAction(if (status.isReblogged) unreblogAction else reblogAction)
                }
                info.addAction(if (status.isFavourited) unfavouriteAction else favouriteAction)
                info.addAction(if (status.isBookmarked) unbookmarkAction else bookmarkAction)

                val mediaActions = intArrayOf(
                        R.id.action_open_media_1,
                        R.id.action_open_media_2,
                        R.id.action_open_media_3,
                        R.id.action_open_media_4)
                val attachmentCount = min(status.attachments.size, MAX_MEDIA_ATTACHMENTS)
                for (i in 0 until attachmentCount) {
                    info.addAction(AccessibilityActionCompat(
                            mediaActions[i],
                            context.getString(R.string.action_open_media_n, i + 1)))
                }

                info.addAction(openProfileAction)
                if (getLinks(status).any()) info.addAction(linksAction)

                val mentions = status.mentions
                if (mentions != null && mentions.isNotEmpty()) info.addAction(mentionsAction)

                if (getHashtags(status).any()) info.addAction(hashtagsAction)
                if (!status.rebloggedByUsername.isNullOrEmpty()) {
                    info.addAction(openRebloggerAction)
                }
                if (status.reblogsCount > 0) info.addAction(openRebloggedByAction)
                if (status.favouritesCount > 0) info.addAction(openFavsAction)

                info.addAction(moreAction)
            }

        }

        override fun performAccessibilityAction(host: View, action: Int,
                                                args: Bundle?): Boolean {
            val pos = recyclerView.getChildAdapterPosition(host)
            when (action) {
                R.id.action_reply -> {
                    interrupt()
                    statusActionListener.onReply(pos)
                }
                R.id.action_favourite -> statusActionListener.onFavourite(true, pos)
                R.id.action_unfavourite -> statusActionListener.onFavourite(false, pos)
                R.id.action_bookmark -> statusActionListener.onBookmark(true, pos)
                R.id.action_unbookmark -> statusActionListener.onBookmark(false, pos)
                R.id.action_reblog -> statusActionListener.onReblog(true, pos)
                R.id.action_unreblog -> statusActionListener.onReblog(false, pos)
                R.id.action_open_profile -> {
                    interrupt()
                    statusActionListener.onViewAccount(
                            (statusProvider.getStatus(pos) as StatusViewData.Concrete).senderId)
                }
                R.id.action_open_media_1 -> {
                    interrupt()
                    statusActionListener.onViewMedia(pos, 0, null)
                }
                R.id.action_open_media_2 -> {
                    interrupt()
                    statusActionListener.onViewMedia(pos, 1, null)
                }
                R.id.action_open_media_3 -> {
                    interrupt()
                    statusActionListener.onViewMedia(pos, 2, null)
                }
                R.id.action_open_media_4 -> {
                    interrupt()
                    statusActionListener.onViewMedia(pos, 3, null)
                }
                R.id.action_expand_cw -> {
                    // Toggling it directly to avoid animations
                    // which cannot be disabled for detaild status for some reason
                    val holder = recyclerView.getChildViewHolder(host) as StatusBaseViewHolder
                    holder.toggleContentWarning()
                    // Stop and restart narrator before it reads old description.
                    // Would be nice if we could *just* read the content here but doesn't seem
                    // to be possible.
                    forceFocus(host)
                }
                R.id.action_collapse_cw -> {
                    statusActionListener.onExpandedChange(false, pos)
                    interrupt()
                }
                R.id.action_links -> showLinksDialog(host)
                R.id.action_mentions -> showMentionsDialog(host)
                R.id.action_hashtags -> showHashtagsDialog(host)
                R.id.action_open_reblogger -> {
                    interrupt()
                    statusActionListener.onOpenReblog(pos)
                }
                R.id.action_open_reblogged_by -> {
                    interrupt()
                    statusActionListener.onShowReblogs(pos)
                }
                R.id.action_open_faved_by -> {
                    interrupt()
                    statusActionListener.onShowFavs(pos)
                }
                R.id.action_more -> {
                    statusActionListener.onMore(host, pos)
                }
                else -> return super.performAccessibilityAction(host, action, args)
            }
            return true
        }


        private fun showLinksDialog(host: View) {
            val status = getStatus(host) as? StatusViewData.Concrete ?: return
            val links = getLinks(status).toList()
            val textLinks = links.map { item -> item.link }
            AlertDialog.Builder(host.context)
                    .setTitle(R.string.title_links_dialog)
                    .setAdapter(ArrayAdapter(
                            host.context,
                            android.R.layout.simple_list_item_1,
                            textLinks)
                    ) { _, which -> LinkHelper.openLink(links[which].link, host.context) }
                    .show()
                    .let { forceFocus(it.listView) }
        }

        private fun showMentionsDialog(host: View) {
            val status = getStatus(host) as? StatusViewData.Concrete ?: return
            val mentions = status.mentions ?: return
            val stringMentions = mentions.map { it.username }
            AlertDialog.Builder(host.context)
                    .setTitle(R.string.title_mentions_dialog)
                    .setAdapter(ArrayAdapter<CharSequence>(host.context,
                            android.R.layout.simple_list_item_1, stringMentions)
                    ) { _, which ->
                        statusActionListener.onViewAccount(mentions[which].id)
                    }
                    .show()
                    .let { forceFocus(it.listView) }
        }

        private fun showHashtagsDialog(host: View) {
            val status = getStatus(host) as? StatusViewData.Concrete ?: return
            val tags = getHashtags(status).map { it.subSequence(1, it.length) }.toList()
            AlertDialog.Builder(host.context)
                    .setTitle(R.string.title_hashtags_dialog)
                    .setAdapter(ArrayAdapter(host.context,
                            android.R.layout.simple_list_item_1, tags)
                    ) { _, which ->
                        statusActionListener.onViewTag(tags[which].toString())
                    }
                    .show()
                    .let { forceFocus(it.listView) }
        }

        private fun getStatus(childView: View): StatusViewData {
            return statusProvider.getStatus(recyclerView.getChildAdapterPosition(childView))!!
        }
    }


    private fun getLinks(status: StatusViewData.Concrete): Sequence<LinkSpanInfo> {
        val content = status.content
        return if (content is Spannable) {
            content.getSpans(0, content.length, URLSpan::class.java)
                    .asSequence()
                    .map { span ->
                        val text = content.subSequence(
                                content.getSpanStart(span),
                                content.getSpanEnd(span))
                        if (isHashtag(text)) null else LinkSpanInfo(text.toString(), span.url)
                    }
                    .filterNotNull()
        } else {
            emptySequence()
        }
    }

    private fun getHashtags(status: StatusViewData.Concrete): Sequence<CharSequence> {
        val content = status.content
        return content.getSpans(0, content.length, Object::class.java)
                .asSequence()
                .map { span ->
                    content.subSequence(content.getSpanStart(span), content.getSpanEnd(span))
                }
                .filter(this::isHashtag)
    }

    private fun forceFocus(host: View) {
        interrupt()
        host.post {
            host.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
        }
    }

    private fun interrupt() {
        a11yManager.interrupt()
    }


    private fun isHashtag(text: CharSequence) = text.startsWith("#")

    private val collapseCwAction = AccessibilityActionCompat(
            R.id.action_collapse_cw,
            context.getString(R.string.status_content_warning_show_less))

    private val expandCwAction = AccessibilityActionCompat(
            R.id.action_expand_cw,
            context.getString(R.string.status_content_warning_show_more))

    private val replyAction = AccessibilityActionCompat(
            R.id.action_reply,
            context.getString(R.string.action_reply))

    private val unreblogAction = AccessibilityActionCompat(
            R.id.action_unreblog,
            context.getString(R.string.action_unreblog))

    private val reblogAction = AccessibilityActionCompat(
            R.id.action_reblog,
            context.getString(R.string.action_reblog))

    private val unfavouriteAction = AccessibilityActionCompat(
            R.id.action_unfavourite,
            context.getString(R.string.action_unfavourite))

    private val favouriteAction = AccessibilityActionCompat(
            R.id.action_favourite,
            context.getString(R.string.action_favourite))

    private val bookmarkAction = AccessibilityActionCompat(
            R.id.action_bookmark,
            context.getString(R.string.action_bookmark))

    private val unbookmarkAction = AccessibilityActionCompat(
            R.id.action_unbookmark,
            context.getString(R.string.action_bookmark))

    private val openProfileAction = AccessibilityActionCompat(
            R.id.action_open_profile,
            context.getString(R.string.action_view_profile))

    private val linksAction = AccessibilityActionCompat(
            R.id.action_links,
            context.getString(R.string.action_links))

    private val mentionsAction = AccessibilityActionCompat(
            R.id.action_mentions,
            context.getString(R.string.action_mentions))

    private val hashtagsAction = AccessibilityActionCompat(
            R.id.action_hashtags,
            context.getString(R.string.action_hashtags))

    private val openRebloggerAction = AccessibilityActionCompat(
            R.id.action_open_reblogger,
            context.getString(R.string.action_open_reblogger))

    private val openRebloggedByAction = AccessibilityActionCompat(
            R.id.action_open_reblogged_by,
            context.getString(R.string.action_open_reblogged_by))

    private val openFavsAction = AccessibilityActionCompat(
            R.id.action_open_faved_by,
            context.getString(R.string.action_open_faved_by))

    private val moreAction = AccessibilityActionCompat(
            R.id.action_more,
            context.getString(R.string.action_more)
    )

    private data class LinkSpanInfo(val text: String, val link: String)
}