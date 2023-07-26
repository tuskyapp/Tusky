/*
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
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource.Monotonic.markNow

enum class UpdateNotificationFrequency {
    /** Never prompt the user to update */
    NEVER,

    /** Prompt the user to update once per version */
    ONCE_PER_VERSION,

    /** Always prompt the user to update */
    ALWAYS;

    companion object {
        fun from(s: String?): UpdateNotificationFrequency {
            s ?: return ALWAYS

            return try {
                valueOf(s.uppercase())
            } catch (_: Throwable) {
                ALWAYS
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
@Singleton
abstract class AppUpdaterBase {
    /** The last time the latest version was checked */
    private var lastCheck = markNow() - MINIMUM_DURATION_BETWEEN_CHECKS

    /** The latest known version code */
    private var latestVersionCode = BuildConfig.VERSION_CODE

    /** An intent that can be used to start the upgrade process (e.g., open a store listing) */
    abstract val updateIntent: Intent

    /**
     * @return The newest available versionCode, or null, if [MINIMUM_DURATION_BETWEEN_CHECKS]
     *    has not elapsed since the last check.
     */
    suspend fun getLatestVersionCode(): Int? {
        if (lastCheck.elapsedNow() < MINIMUM_DURATION_BETWEEN_CHECKS) {
            return null
        }
        lastCheck = markNow()

        remoteFetchLatestVersionCode()?.let { latestVersionCode = it }
        return latestVersionCode
    }

    /**
     * Fetch the version code of the latest available version of Tusky from whatever
     * remote service the running version was downloaded from.
     *
     * @return The latest version code, or null if it could not be determined
     */
    abstract suspend fun remoteFetchLatestVersionCode(): Int?

    companion object {
        /** How much time should elapse between version checks */
        private val MINIMUM_DURATION_BETWEEN_CHECKS = 24.hours
    }
}
