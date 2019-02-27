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
import com.keylesspalace.tusky.interfaces.StatusActionListener
import com.keylesspalace.tusky.viewdata.StatusViewData

// Not using lambdas because there's boxing of int then
interface StatusProvider {
    fun getStatus(pos: Int): StatusViewData
}

class ListStatusAccessibilityDelegate(
        private val recyclerView: RecyclerView,
        private val statusActionListener: StatusActionListener,
        private val statusProvider: StatusProvider
) : RecyclerViewAccessibilityDelegate(recyclerView) {
    override fun getItemDelegate(): AccessibilityDelegateCompat {
        return itemDelegate
    }

    private val context: Context
        get() = recyclerView.context

    private val itemDelegate = object : RecyclerViewAccessibilityDelegate.ItemDelegate(this) {
        override fun onInitializeAccessibilityNodeInfo(host: View,
                                                       info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)

            val pos = recyclerView.getChildAdapterPosition(host)
            val status = statusProvider.getStatus(pos)
            if (status is StatusViewData.Concrete) {
                if (!status.spoilerText.isNullOrEmpty()) {
                    if (status.isExpanded) {
                        info.addAction(AccessibilityActionCompat(
                                R.id.action_collapse_cw,
                                context.getString(R.string.status_content_warning_show_less)))
                    } else {
                        info.addAction(AccessibilityActionCompat(
                                R.id.action_expand_cw,
                                context.getString(R.string.status_content_warning_show_more)))
                    }
                }

                info.addAction(AccessibilityActionCompat(
                        R.id.action_reply,
                        context.getString(R.string.action_reply)))

                if (status.rebloggingEnabled) {
                    if (status.isReblogged) {
                        info.addAction(AccessibilityActionCompat(
                                R.id.action_unreblog,
                                context.getString(R.string.action_unreblog)))
                    } else {
                        info.addAction(AccessibilityActionCompat(
                                R.id.action_reblog,
                                context.getString(R.string.action_reblog)))
                    }
                }

                if (status.isFavourited) {
                    info.addAction(AccessibilityActionCompat(
                            R.id.action_unfavourite,
                            context.getString(R.string.action_unfavourite)))
                } else {
                    info.addAction(AccessibilityActionCompat(
                            R.id.action_favourite,
                            context.getString(R.string.action_favourite)))
                }
                val mediaActions = intArrayOf(
                        R.id.action_open_media_1,
                        R.id.action_open_media_2,
                        R.id.action_open_media_3,
                        R.id.action_open_media_4)
                for (i in 0 until status.attachments.size) {
                    info.addAction(AccessibilityActionCompat(
                            mediaActions[i],
                            context.getString(R.string.action_open_media_n, i + 1)))
                }
                info.addAction(AccessibilityActionCompat(
                        R.id.action_open_profile,
                        context.getString(R.string.action_view_profile)))

                if (getLinks(status).any()) {
                    info.addAction(AccessibilityActionCompat(
                            R.id.action_links,
                            context.getString(R.string.action_links)
                    ))
                }
                val mentions = status.mentions
                if (mentions != null && mentions.isNotEmpty()) {
                    info.addAction(AccessibilityActionCompat(
                            R.id.action_mentions,
                            context.getString(R.string.action_mentions)
                    ))
                }
                if (getHashtags(status).any()) {
                    info.addAction(AccessibilityActionCompat(
                            R.id.actions_hashtags,
                            context.getString(R.string.action_hashtags)
                    ))
                }
            }

        }

        override fun performAccessibilityAction(host: View, action: Int,
                                                args: Bundle?): Boolean {
            val pos = recyclerView.getChildAdapterPosition(host)
            when (action) {
                R.id.action_reply -> statusActionListener.onReply(pos)
                R.id.action_favourite -> statusActionListener.onFavourite(true, pos)
                R.id.action_unfavourite -> statusActionListener.onFavourite(false, pos)
                R.id.action_reblog -> statusActionListener.onReblog(true, pos)
                R.id.action_unreblog -> statusActionListener.onReblog(false, pos)
                R.id.action_open_profile -> statusActionListener.onViewAccount(
                        (statusProvider.getStatus(pos) as StatusViewData.Concrete).senderId)
                R.id.action_open_media_1 -> statusActionListener.onViewMedia(pos, 0, null)
                R.id.action_open_media_2 -> statusActionListener.onViewMedia(pos, 1, null)
                R.id.action_open_media_3 -> statusActionListener.onViewMedia(pos, 2, null)
                R.id.action_open_media_4 -> statusActionListener.onViewMedia(pos, 3, null)
                R.id.action_expand_cw -> {
                    statusActionListener.onExpandedChange(true, pos)
                    // Stop and restart narrator before it reads old description
                    forceFocus(host)
                }
                R.id.action_collapse_cw -> {
                    statusActionListener.onExpandedChange(false, pos)
                    // See above
                    val a11yManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                            as AccessibilityManager
                    a11yManager.interrupt()
                    host.post {
                        host.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                    }
                }
                R.id.action_links -> {
                    showLinksDialog(host)
                }
                R.id.action_mentions -> {
                    showMentionsDialog(host)
                }
                R.id.actions_hashtags -> {
                    showHashtagsDialog(host)
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
                    .setAdapter(ArrayAdapter<String>(
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
                    .setAdapter(ArrayAdapter<CharSequence>(host.context,
                            android.R.layout.simple_list_item_1, tags)
                    ) { _, which ->
                        statusActionListener.onViewTag(tags[which].toString())
                    }
                    .show()
                    .let { forceFocus(it.listView) }
        }

        private fun getStatus(childView: View): StatusViewData {
            return statusProvider.getStatus(recyclerView.getChildAdapterPosition(childView))
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
        val a11yManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                as AccessibilityManager
        a11yManager.interrupt()
        host.post {
            host.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
        }
    }

    private data class LinkSpanInfo(val text: String, val link: String)

    private fun isHashtag(text: CharSequence) = text.startsWith("#")
}