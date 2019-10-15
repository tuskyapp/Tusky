/* Copyright 2019 Tusky Contributors
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

@file:JvmName("ShareShortcutHelper")

package com.keylesspalace.tusky.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.TextUtils
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.bumptech.glide.Glide
import com.keylesspalace.tusky.MainActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.db.AccountEntity
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers

fun updateShortcut(context: Context, account: AccountEntity) {

    Single.fromCallable {

        val icon = if (TextUtils.isEmpty(account.profilePictureUrl)) {
            IconCompat.createWithResource(context, R.drawable.avatar_default)
        } else {
            val innerSize = context.resources.getDimensionPixelSize(R.dimen.adaptive_bitmap_inner_size)
            val outerSize = context.resources.getDimensionPixelSize(R.dimen.adaptive_bitmap_outer_size)

            val bmp = Glide.with(context)
                    .asBitmap()
                    .load(account.profilePictureUrl)
                    .error(R.drawable.avatar_default)
                    .submit(innerSize, innerSize)
                    .get()

            val outBmp = Bitmap.createBitmap(outerSize, outerSize, Bitmap.Config.ARGB_8888)

            val canvas = Canvas(outBmp)
            canvas.drawBitmap(bmp, (outerSize - innerSize).toFloat() / 2f, (outerSize - innerSize).toFloat() / 2f, null)

            IconCompat.createWithAdaptiveBitmap(outBmp)
        }

        val person = Person.Builder()
                .setIcon(icon)
                .setName(account.displayName)
                .setKey(account.identifier)
                .build()

        // This intent will be sent when the user clicks on one of the launcher shortcuts. Intent from share sheet will be different
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(NotificationHelper.ACCOUNT_ID, account.id)
        }

        val shortcutInfo = ShortcutInfoCompat.Builder(context, account.id.toString())
                .setIntent(intent)
                .setCategories(setOf("com.keylesspalace.tusky.Share"))
                .setShortLabel(account.displayName)
                .setPerson(person)
                .setLongLived(true)
                .setIcon(icon)
                .build()

        ShortcutManagerCompat.addDynamicShortcuts(context, listOf(shortcutInfo))

    }
            .subscribeOn(Schedulers.computation())
            .subscribe()


}

fun removeShortcut(context: Context, account: AccountEntity) {

    ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(account.id.toString()))

}