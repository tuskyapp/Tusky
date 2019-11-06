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

package com.keylesspalace.tusky.service

import android.annotation.TargetApi
import android.content.Intent
import android.service.quicksettings.TileService
import com.keylesspalace.tusky.MainActivity

/**
 * Small Addition that adds in a QuickSettings tile
 * opens the Compose activity or shows an account selector when multiple accounts are present
 */

@TargetApi(24)
class TuskyTileService : TileService() {

    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            action = Intent.ACTION_SEND
            type = "text/plain"
        }
        startActivityAndCollapse(intent)
    }
}
