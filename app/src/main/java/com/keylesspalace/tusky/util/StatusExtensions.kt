package com.keylesspalace.tusky.util

import com.keylesspalace.tusky.entity.Status

fun Status.isCollapsible(): Boolean {
    return !SmartLengthInputFilter.hasBadRatio(content, SmartLengthInputFilter.LENGTH_DEFAULT)
}

