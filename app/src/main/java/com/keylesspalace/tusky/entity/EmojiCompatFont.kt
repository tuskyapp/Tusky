package com.keylesspalace.tusky.entity

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class EmojiCompatFont(
        // The name of this font - should be unique and should not include white spaces (as of now)
        val name: String = "",
        // The name which is displayed
        val display: String = "",
        // URL of the thumbnail
        val img: String = "",
        // URL of the font file
        val url: String = "",
        // URL of the font's website (not used yet)
        val src: String = ""
) : Parcelable