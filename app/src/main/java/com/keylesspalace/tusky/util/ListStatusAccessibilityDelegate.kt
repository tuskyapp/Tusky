package com.keylesspalace.tusky.util

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
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
                info.addAction(AccessibilityActionCompat(
                        R.id.action_reply,
                        context.getString(R.string.action_reply)))

                if (!status.spoilerText.isNullOrEmpty()) {
                    if (status.isExpanded) {
                        info.addAction(AccessibilityActionCompat(R.id.action_collapse_cw, context.getString(R.string.status_content_warning_show_less)))
                    } else {
                        info.addAction(AccessibilityActionCompat(R.id.action_expand_cw, context.getString(R.string.status_content_warning_show_more)))
                    }
                }

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
                    val a11yManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                                    as AccessibilityManager
                    // Stop narrator before it reads old description
                    a11yManager.interrupt()
                    // Play new description when it's updated
                    host.post {
                        host.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                    }

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
                else -> return super.performAccessibilityAction(host, action, args)
            }
            return true
        }
    }
}