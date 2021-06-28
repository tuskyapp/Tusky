package com.keylesspalace.tusky.view

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class EmojiPicker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

    init {
        clipToPadding = false
        layoutManager = GridLayoutManager(context, 3, GridLayoutManager.HORIZONTAL, false)
    }
}
