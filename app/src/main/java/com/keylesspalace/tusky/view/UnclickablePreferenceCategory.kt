package com.keylesspalace.tusky.view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder

/**
 * PreferenceCategory with disabled view click listener
 *
 * Created by pandasoft (joelpyska1@gmail.com) on 2019-05-04.
 */
class UnclickablePreferenceCategory : PreferenceCategory {
    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    @Suppress("unused")
    constructor(context: Context) : super(context)

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)
        //Disable click listener for an item
        holder?.itemView?.setOnClickListener(null)
        //Set item color to transparent
        holder?.itemView?.setBackgroundColor(Color.TRANSPARENT)
    }
}