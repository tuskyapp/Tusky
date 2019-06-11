package com.keylesspalace.tusky.entity

import com.google.gson.annotations.SerializedName

data class History(
        @field:SerializedName("day")
        val day: String,

        @field:SerializedName("uses")
        val uses: Int,

        @field:SerializedName("accounts")
        val accounts: Int
)