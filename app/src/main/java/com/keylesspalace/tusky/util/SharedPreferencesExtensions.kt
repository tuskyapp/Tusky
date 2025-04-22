package com.keylesspalace.tusky.util

import android.content.SharedPreferences

fun SharedPreferences.getNonNullString(key: String, defValue: String): String = this.getString(key, defValue) ?: defValue
