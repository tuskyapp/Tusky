package com.keylesspalace.tusky.components.viewthread.edits

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.keylesspalace.tusky.entity.StatusEdit
import com.keylesspalace.tusky.interfaces.LinkListener
import com.keylesspalace.tusky.settings.PrefKeys

fun Context.showViewEditsDialog(
    edits: List<StatusEdit>,
    listener: LinkListener
) {

    val sortedEdits = edits.sortedBy { edit -> edit.createdAt }.reversed()

    val recyclerView = RecyclerView(this)

    val preferences = PreferenceManager.getDefaultSharedPreferences(this)

    val animateAvatars = preferences.getBoolean(PrefKeys.ANIMATE_GIF_AVATARS, false)
    val animateEmojis = preferences.getBoolean(PrefKeys.ANIMATE_CUSTOM_EMOJIS, false)
    val useBlurhash = preferences.getBoolean(PrefKeys.USE_BLURHASH, true)

    recyclerView.adapter = ViewEditsAdapter(sortedEdits, animateAvatars, animateEmojis, useBlurhash, listener)
    recyclerView.layoutManager = LinearLayoutManager(this)
    recyclerView.overScrollMode = View.OVER_SCROLL_NEVER

    recyclerView.addItemDecoration(MaterialDividerItemDecoration(this, LinearLayout.VERTICAL))

    AlertDialog.Builder(this)
        .setView(recyclerView)
        .setPositiveButton(android.R.string.ok, null)
        .show()
}
