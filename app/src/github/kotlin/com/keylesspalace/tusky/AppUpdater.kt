/**
 * Copyright 2023 Tusky Contributors
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
 * see <http://www.gnu.org/licenses>.
 */

package com.keylesspalace.tusky

import android.content.Intent
import android.net.Uri
import com.keylesspalace.tusky.updatecheck.GitHubService
import javax.inject.Inject

class AppUpdater @Inject constructor(
    private val gitHubService: GitHubService
) : AppUpdaterBase() {
    private val versionCodeExtractor = """(\d+)\.apk""".toRegex()

    override val updateIntent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("https://www.github.com/tuskyapp/tusky/releases/latest")
    }

    override suspend fun remoteFetchLatestVersionCode(): Int? {
        val release = gitHubService.getLatestRelease("tuskyapp", "tusky").getOrNull() ?: return null
        for (asset in release.assets) {
            if (asset.contentType != "application/vnd.android.package-archive") continue
            return versionCodeExtractor.find(asset.name)?.groups?.get(1)?.value?.toIntOrNull() ?: continue
        }

        return null
    }

}
