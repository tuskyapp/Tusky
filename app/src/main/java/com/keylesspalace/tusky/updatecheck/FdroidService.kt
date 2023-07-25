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

package com.keylesspalace.tusky.updatecheck

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

data class FdroidPackageVersion(
    val versionName: String,
    val versionCode: Int
)

data class FdroidPackage(
    val packageName: String,
    val suggestedVersionCode: Int,
    val packages: List<FdroidPackageVersion>
)

interface FdroidService {
    @GET("/api/v1/packages/{package}")
    suspend fun getPackage(
        @Path("package") pkg: String
    ): Response<FdroidPackage>
}
