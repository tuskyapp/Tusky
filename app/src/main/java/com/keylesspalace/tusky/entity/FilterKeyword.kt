package com.keylesspalace.tusky.entity

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

@JsonClass(generateAdapter = true)
@Parcelize
data class FilterKeyword(
    val id: String,
    val keyword: String,
    @Json(name = "whole_word") val wholeWord: Boolean
) : Parcelable
