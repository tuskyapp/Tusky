package com.keylesspalace.tusky.util

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import java.io.Serializable

inline fun <reified T : Parcelable> Bundle.getParcelableCompat(key: String?): T? =
    BundleCompat.getParcelable(this, key, T::class.java)

inline fun <reified T : Serializable> Bundle.getSerializableCompat(key: String?): T? =
    BundleCompat.getSerializable(this, key, T::class.java)

inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String?): T? =
    IntentCompat.getParcelableExtra(this, key, T::class.java)

inline fun <reified T : Serializable> Intent.getSerializableExtraCompat(key: String?): T? =
    IntentCompat.getSerializableExtra(this, key, T::class.java)

inline fun <reified T : Parcelable> Intent.getParcelableArrayListExtraCompat(key: String?): ArrayList<T>? =
    IntentCompat.getParcelableArrayListExtra(this, key, T::class.java)
