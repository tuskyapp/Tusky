/* Copyright 2022 kylegoetz
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

package com.keylesspalace.tusky.network

import at.connyduck.calladapter.networkresult.NetworkResult
import com.keylesspalace.tusky.entity.AccessToken
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.AppCredentials
import com.keylesspalace.tusky.entity.Instance
import com.keylesspalace.tusky.entity.Marker
import com.keylesspalace.tusky.entity.NewStatus
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.NotificationSubscribeResult
import com.keylesspalace.tusky.entity.Status
import io.reactivex.rxjava3.core.Single
import okhttp3.ResponseBody
import java.net.IDN

class UnicodeSafeMastodonApi(private val api: MastodonApi) : MastodonApi by api {
    override suspend fun getInstance(domain: String?): NetworkResult<Instance> {
        return api.getInstance(domain?.let(IDN::toASCII))
    }

    override fun markersWithAuth(
        auth: String,
        domain: String,
        timelines: List<String>
    ): Single<Map<String, Marker>> {
        return api.markersWithAuth(auth, IDN.toASCII(domain), timelines)
    }

    override fun notificationsWithAuth(
        auth: String,
        domain: String,
        sinceId: String?
    ): Single<List<Notification>> {
        return api.notificationsWithAuth(auth, IDN.toASCII(domain), sinceId)
    }

    override suspend fun createStatus(
        auth: String,
        domain: String,
        idempotencyKey: String,
        status: NewStatus
    ): NetworkResult<Status> {
        return api.createStatus(auth, IDN.toASCII(domain), idempotencyKey, status)
    }

    override suspend fun accountVerifyCredentials(
        domain: String?,
        auth: String?
    ): NetworkResult<Account> {
        return api.accountVerifyCredentials(domain?.let(IDN::toASCII), auth)
    }

    override suspend fun authenticateApp(
        domain: String,
        clientName: String,
        redirectUris: String,
        scopes: String,
        website: String
    ): NetworkResult<AppCredentials> {
        return api.authenticateApp(
            IDN.toASCII(domain),
            clientName,
            redirectUris,
            scopes,
            website
        )
    }

    override suspend fun fetchOAuthToken(
        domain: String,
        clientId: String,
        clientSecret: String,
        redirectUri: String,
        code: String,
        grantType: String
    ): NetworkResult<AccessToken> {
        return api.fetchOAuthToken(
            IDN.toASCII(domain),
            clientId,
            clientSecret,
            redirectUri,
            code,
            grantType
        )
    }

    override suspend fun subscribePushNotifications(
        auth: String,
        domain: String,
        endPoint: String,
        keysP256DH: String,
        keysAuth: String,
        data: Map<String, Boolean>
    ): NetworkResult<NotificationSubscribeResult> {
        return api.subscribePushNotifications(
            auth,
            IDN.toASCII(domain),
            endPoint,
            keysP256DH,
            keysAuth,
            data
        )
    }

    override suspend fun updatePushNotificationSubscription(
        auth: String,
        domain: String,
        data: Map<String, Boolean>
    ): NetworkResult<NotificationSubscribeResult> {
        return api.updatePushNotificationSubscription(
            auth,
            IDN.toASCII(domain),
            data
        )
    }

    override suspend fun unsubscribePushNotifications(
        auth: String,
        domain: String
    ): NetworkResult<ResponseBody> {
        return api.unsubscribePushNotifications(
            auth,
            IDN.toASCII(domain)
        )
    }
}
