@file:Suppress("DEPRECATION")

package com.keylesspalace.tusky.util

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import java.io.Serializable

inline fun <reified T : Serializable> Intent.requireSerializableExtra(name: String?): T {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSerializableExtra(name, T::class.java)!!
    } else {
        getSerializableExtra(name) as T
    }
}

inline fun <reified T : Parcelable> Intent.parcelableArrayListExtra(name: String?): ArrayList<T>? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(name, T::class.java)
    } else {
        getParcelableArrayListExtra(name)
    }
}

inline fun <reified T : Parcelable> Bundle.parcelable(name: String?): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelable(name, T::class.java)
    } else {
        getParcelable(name)
    }
}

inline fun <reified T : Parcelable> Intent.parcelableExtra(name: String?): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, T::class.java)
    } else {
        getParcelableExtra(name) as T?
    }
}

inline fun <reified T : Serializable> Bundle.requireSerializable(name: String?): T {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSerializable(name, T::class.java)!!
    } else {
        getSerializable(name) as T
    }
}
