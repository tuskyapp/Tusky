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
import android.util.Log
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.GlideException
import com.keylesspalace.tusky.MainActivity
import com.keylesspalace.tusky.R
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.entity.AccountEntity
import com.keylesspalace.tusky.di.ApplicationScope
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ShareShortcutHelper @Inject constructor(
    private val context: Context,
    private val accountManager: AccountManager,
    @ApplicationScope private val externalScope: CoroutineScope
) {

    fun updateShortcuts() {
        externalScope.launch(Dispatchers.IO) {
            val innerSize = context.resources.getDimensionPixelSize(R.dimen.adaptive_bitmap_inner_size)
            val outerSize = context.resources.getDimensionPixelSize(R.dimen.adaptive_bitmap_outer_size)

            val maxNumberOfShortcuts = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)

            val shortcuts = accountManager.accounts.take(maxNumberOfShortcuts).mapNotNull { account ->

                val bmp = try {
                    Glide.with(context)
                        .asBitmap()
                        .load(account.profilePictureUrl)
                        .submitAsync(innerSize, innerSize)
                } catch (e: GlideException) {
                    // https://github.com/bumptech/glide/issues/4672 :/
                    Log.w(TAG, "failed to load avatar ${account.profilePictureUrl}", e)
                    AppCompatResources.getDrawable(context, R.drawable.avatar_default)?.toBitmap(innerSize, innerSize) ?: return@mapNotNull null
                }

                // inset the loaded bitmap inside a 108dp transparent canvas so it looks good as adaptive icon
                val outBmp = Bitmap.createBitmap(outerSize, outerSize, Bitmap.Config.ARGB_8888)

                val canvas = Canvas(outBmp)
                canvas.drawBitmap(
                    bmp,
                    (outerSize - innerSize).toFloat() / 2f,
                    (outerSize - innerSize).toFloat() / 2f,
                    null
                )

                val icon = IconCompat.createWithAdaptiveBitmap(outBmp)

                val person = Person.Builder()
                    .setIcon(icon)
                    .setName(account.displayName)
                    .setKey(account.identifier)
                    .build()

                // This intent will be sent when the user clicks on one of the launcher shortcuts. Intent from share sheet will be different
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID, account.id.toString())
                }

                ShortcutInfoCompat.Builder(context, account.id.toString())
                    .setIntent(intent)
                    .setCategories(setOf("com.keylesspalace.tusky.Share"))
                    .setShortLabel(account.displayName)
                    .setPerson(person)
                    .setIcon(icon)
                    .build()
            }

            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }

    fun removeShortcut(account: AccountEntity) {
        ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(account.id.toString()))
    }

    companion object {
        private const val TAG = "ShareShortcutHelper"
    }
}
