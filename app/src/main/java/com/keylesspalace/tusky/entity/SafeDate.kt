package com.keylesspalace.tusky.entity

import java.util.*

sealed class SafeDate {
    data class KnownDate(
            val date: Date
    ) : SafeDate()

    object UnknownDate : SafeDate()
}
