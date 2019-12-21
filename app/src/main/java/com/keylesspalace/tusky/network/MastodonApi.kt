/* Copyright 2017 Andrew Dawson
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

import com.keylesspalace.tusky.entity.*
import io.reactivex.Completable
import io.reactivex.Single
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*
import retrofit2.http.Field

/**
 * for documentation of the Mastodon REST API see https://docs.joinmastodon.org/api/
 */

@JvmSuppressWildcards
interface MastodonApi {

    companion object {
        const val ENDPOINT_AUTHORIZE = "/oauth/authorize"
        const val DOMAIN_HEADER = "domain"
        const val PLACEHOLDER_DOMAIN = "dummy.placeholder"
    }

    @GET("/api/v1/lists")
    fun getLists(): Single<List<MastoList>>

    @GET("/api/v1/custom_emojis")
    fun getCustomEmojis(): Single<List<Emoji>>

    @GET("api/v1/instance")
    fun getInstance(): Single<Instance>

    @GET("api/v1/filters")
    fun getFilters(): Call<List<Filter>>

    @GET("api/v1/timelines/home")
    fun homeTimeline(
            @Query("max_id") maxId: String?,
            @Query("since_id") sinceId: String?,
            @Query("limit") limit: Int?
    ): Call<List<Status>>

    @GET("api/v1/timelines/home")
    fun homeTimelineSingle(
            @Query("max_id") maxId: String?,
            @Query("since_id") sinceId: String?,
            @Query("limit") limit: Int?
    ): Single<List<Status>>

    @GET("api/v1/timelines/public")
    fun publicTimeline(
            @Query("local") local: Boolean?,
            @Query("max_id") maxId: String?,
            @Query("since_id") sinceId: String?,
            @Query("limit") limit: Int?
    ): Call<List<Status>>

    @GET("api/v1/timelines/tag/{hashtag}")
    fun hashtagTimeline(
            @Path("hashtag") hashtag: String,
            @Query("local") local: Boolean?,
            @Query("max_id") maxId: String?,
            @Query("since_id") sinceId: String?,
            @Query("limit") limit: Int?
    ): Call<List<Status>>

    @GET("api/v1/timelines/list/{listId}")
    fun listTimeline(
            @Path("listId") listId: String,
            @Query("max_id") maxId: String?,
            @Query("since_id") sinceId: String?,
            @Query("limit") limit: Int?
    ): Call<List<Status>>

    @GET("api/v1/notifications")
    fun notifications(
            @Query("max_id") maxId: String?,
            @Query("since_id") sinceId: String?,
            @Query("limit") limit: Int?,
            @Query("exclude_types[]") excludes: Set<Notification.Type>?
    ): Call<List<Notification>>

    @GET("api/v1/notifications")
    fun notificationsWithAuth(
            @Header("Authorization") auth: String,
            @Header(DOMAIN_HEADER) domain: String
    ): Call<List<Notification>>

    @POST("api/v1/notifications/clear")
    fun clearNotifications(): Call<ResponseBody>

    @GET("api/v1/notifications/{id}")
    fun notification(
            @Path("id") notificationId: String
    ): Call<Notification>

    @Multipart
    @POST("api/v1/media")
    fun uploadMedia(
            @Part file: MultipartBody.Part
    ): Single<Attachment>

    @FormUrlEncoded
    @PUT("api/v1/media/{mediaId}")
    fun updateMedia(
            @Path("mediaId") mediaId: String,
            @Field("description") description: String
    ): Single<Attachment>

    @POST("api/v1/statuses")
    fun createStatus(
            @Header("Authorization") auth: String,
            @Header(DOMAIN_HEADER) domain: String,
            @Header("Idempotency-Key") idempotencyKey: String,
            @Body status: NewStatus
    ): Call<Status>

    @GET("api/v1/statuses/{id}")
    fun status(
            @Path("id") statusId: String
    ): Call<Status>

    @GET("api/v1/statuses/{id}/context")
    fun statusContext(
            @Path("id") statusId: String
    ): Call<StatusContext>

    @GET("api/v1/statuses/{id}/reblogged_by")
    fun statusRebloggedBy(
            @Path("id") statusId: String,
            @Query("max_id") maxId: String?
    ): Single<Response<List<Account>>>

    @GET("api/v1/statuses/{id}/favourited_by")
    fun statusFavouritedBy(
            @Path("id") statusId: String,
            @Query("max_id") maxId: String?
    ): Single<Response<List<Account>>>

    @DELETE("api/v1/statuses/{id}")
    fun deleteStatus(
            @Path("id") statusId: String
    ): Single<DeletedStatus>

    @POST("api/v1/statuses/{id}/reblog")
    fun reblogStatus(
            @Path("id") statusId: String
    ): Single<Status>

    @POST("api/v1/statuses/{id}/unreblog")
    fun unreblogStatus(
            @Path("id") statusId: String
    ): Single<Status>

    @POST("api/v1/statuses/{id}/favourite")
    fun favouriteStatus(
            @Path("id") statusId: String
    ): Single<Status>

    @POST("api/v1/statuses/{id}/unfavourite")
    fun unfavouriteStatus(
            @Path("id") statusId: String
    ): Single<Status>

    @POST("api/v1/statuses/{id}/bookmark")
    fun bookmarkStatus(
            @Path("id") statusId: String
    ): Single<Status>

    @POST("api/v1/statuses/{id}/unbookmark")
    fun unbookmarkStatus(
            @Path("id") statusId: String
    ): Single<Status>

    @POST("api/v1/statuses/{id}/pin")
    fun pinStatus(
            @Path("id") statusId: String
    ): Single<Status>

    @POST("api/v1/statuses/{id}/unpin")
    fun unpinStatus(
            @Path("id") statusId: String
    ): Single<Status>

    @GET("api/v1/scheduled_statuses")
    fun scheduledStatuses(): Call<List<ScheduledStatus>>

    @DELETE("api/v1/scheduled_statuses/{id}")
    fun deleteScheduledStatus(
            @Path("id") scheduledStatusId: String
    ): Call<ResponseBody>

    @GET("api/v1/accounts/verify_credentials")
    fun accountVerifyCredentials(): Single<Account>

    @FormUrlEncoded
    @PATCH("api/v1/accounts/update_credentials")
    fun accountUpdateSource(
            @Field("source[privacy]") privacy: String?,
            @Field("source[sensitive]") sensitive: Boolean?
    ): Call<Account>

    @Multipart
    @PATCH("api/v1/accounts/update_credentials")
    fun accountUpdateCredentials(
            @Part(value = "display_name") displayName: RequestBody?,
            @Part(value = "note") note: RequestBody?,
            @Part(value = "locked") locked: RequestBody?,
            @Part avatar: MultipartBody.Part?,
            @Part header: MultipartBody.Part?,
            @Part(value = "fields_attributes[0][name]") fieldName0: RequestBody?,
            @Part(value = "fields_attributes[0][value]") fieldValue0: RequestBody?,
            @Part(value = "fields_attributes[1][name]") fieldName1: RequestBody?,
            @Part(value = "fields_attributes[1][value]") fieldValue1: RequestBody?,
            @Part(value = "fields_attributes[2][name]") fieldName2: RequestBody?,
            @Part(value = "fields_attributes[2][value]") fieldValue2: RequestBody?,
            @Part(value = "fields_attributes[3][name]") fieldName3: RequestBody?,
            @Part(value = "fields_attributes[3][value]") fieldValue3: RequestBody?
    ): Call<Account>

    @GET("api/v1/accounts/search")
    fun searchAccounts(
            @Query("q") query: String,
            @Query("resolve") resolve: Boolean? = null,
            @Query("limit") limit: Int? = null,
            @Query("following") following: Boolean? = null
    ): Single<List<Account>>

    @GET("api/v1/accounts/{id}")
    fun account(
            @Path("id") accountId: String
    ): Call<Account>

    /**
     * Method to fetch statuses for the specified account.
     * @param accountId ID for account for which statuses will be requested
     * @param maxId Only statuses with ID less than maxID will be returned
     * @param sinceId Only statuses with ID bigger than sinceID will be returned
     * @param limit Limit returned statuses (current API limits: default - 20, max - 40)
     * @param excludeReplies only return statuses that are no replies
     * @param onlyMedia only return statuses that have media attached
     */
    @GET("api/v1/accounts/{id}/statuses")
    fun accountStatuses(
            @Path("id") accountId: String,
            @Query("max_id") maxId: String?,
            @Query("since_id") sinceId: String?,
            @Query("limit") limit: Int?,
            @Query("exclude_replies") excludeReplies: Boolean?,
            @Query("only_media") onlyMedia: Boolean?,
            @Query("pinned") pinned: Boolean?
    ): Call<List<Status>>

    @GET("api/v1/accounts/{id}/followers")
    fun accountFollowers(
            @Path("id") accountId: String,
            @Query("max_id") maxId: String?
    ): Single<Response<List<Account>>>

    @GET("api/v1/accounts/{id}/following")
    fun accountFollowing(
            @Path("id") accountId: String,
            @Query("max_id") maxId: String?
    ): Single<Response<List<Account>>>

    @FormUrlEncoded
    @POST("api/v1/accounts/{id}/follow")
    fun followAccount(
            @Path("id") accountId: String,
            @Field("reblogs") showReblogs: Boolean
    ): Call<Relationship>

    @POST("api/v1/accounts/{id}/unfollow")
    fun unfollowAccount(
            @Path("id") accountId: String
    ): Call<Relationship>

    @POST("api/v1/accounts/{id}/block")
    fun blockAccount(
            @Path("id") accountId: String
    ): Call<Relationship>

    @POST("api/v1/accounts/{id}/unblock")
    fun unblockAccount(
            @Path("id") accountId: String
    ): Call<Relationship>

    @POST("api/v1/accounts/{id}/mute")
    fun muteAccount(
            @Path("id") accountId: String
    ): Call<Relationship>

    @POST("api/v1/accounts/{id}/unmute")
    fun unmuteAccount(
            @Path("id") accountId: String
    ): Call<Relationship>

    @GET("api/v1/accounts/relationships")
    fun relationships(
            @Query("id[]") accountIds: List<String>
    ): Call<List<Relationship>>

    @GET("api/v1/accounts/{id}/identity_proofs")
    fun identityProofs(
            @Path("id") accountId: String
    ): Call<List<IdentityProof>>

    @GET("api/v1/blocks")
    fun blocks(
            @Query("max_id") maxId: String?
    ): Single<Response<List<Account>>>

    @GET("api/v1/mutes")
    fun mutes(
            @Query("max_id") maxId: String?
    ): Single<Response<List<Account>>>

    @GET("api/v1/domain_blocks")
    fun domainBlocks(
            @Query("max_id") maxId: String? = null,
            @Query("since_id") sinceId: String? = null,
            @Query("limit") limit: Int? = null
    ): Single<Response<List<String>>>

    @FormUrlEncoded
    @POST("api/v1/domain_blocks")
    fun blockDomain(
            @Field("domain") domain: String
    ): Call<Any>

    @FormUrlEncoded
    // @DELETE doesn't support fields
    @HTTP(method = "DELETE", path = "api/v1/domain_blocks", hasBody = true)
    fun unblockDomain(@Field("domain") domain: String): Call<Any>

    @GET("api/v1/favourites")
    fun favourites(
            @Query("max_id") maxId: String?,
            @Query("since_id") sinceId: String?,
            @Query("limit") limit: Int?
    ): Call<List<Status>>

    @GET("api/v1/bookmarks")
    fun bookmarks(
            @Query("max_id") maxId: String?,
            @Query("since_id") sinceId: String?,
            @Query("limit") limit: Int?
    ): Call<List<Status>>

    @GET("api/v1/follow_requests")
    fun followRequests(
            @Query("max_id") maxId: String?
    ): Single<Response<List<Account>>>

    @POST("api/v1/follow_requests/{id}/authorize")
    fun authorizeFollowRequest(
            @Path("id") accountId: String
    ): Call<Relationship>

    @POST("api/v1/follow_requests/{id}/reject")
    fun rejectFollowRequest(
            @Path("id") accountId: String
    ): Call<Relationship>

    @FormUrlEncoded
    @POST("api/v1/apps")
    fun authenticateApp(
            @Header(DOMAIN_HEADER) domain: String,
            @Field("client_name") clientName: String,
            @Field("redirect_uris") redirectUris: String,
            @Field("scopes") scopes: String,
            @Field("website") website: String
    ): Call<AppCredentials>

    @FormUrlEncoded
    @POST("oauth/token")
    fun fetchOAuthToken(
            @Header(DOMAIN_HEADER) domain: String,
            @Field("client_id") clientId: String,
            @Field("client_secret") clientSecret: String,
            @Field("redirect_uri") redirectUri: String,
            @Field("code") code: String,
            @Field("grant_type") grantType: String
    ): Call<AccessToken>

    @FormUrlEncoded
    @POST("api/v1/lists")
    fun createList(
            @Field("title") title: String
    ): Single<MastoList>

    @FormUrlEncoded
    @PUT("api/v1/lists/{listId}")
    fun updateList(
            @Path("listId") listId: String,
            @Field("title") title: String
    ): Single<MastoList>

    @DELETE("api/v1/lists/{listId}")
    fun deleteList(
            @Path("listId") listId: String
    ): Completable

    @GET("api/v1/lists/{listId}/accounts")
    fun getAccountsInList(
            @Path("listId") listId: String,
            @Query("limit") limit: Int
    ): Single<List<Account>>

    @DELETE("api/v1/lists/{listId}/accounts")
    fun deleteAccountFromList(
            @Path("listId") listId: String,
            @Query("account_ids[]") accountIds: List<String>
    ): Completable

    @POST("api/v1/lists/{listId}/accounts")
    fun addCountToList(
            @Path("listId") listId: String,
            @Query("account_ids[]") accountIds: List<String>
    ): Completable

    @GET("/api/v1/conversations")
    fun getConversations(
            @Query("max_id") maxId: String? = null,
            @Query("limit") limit: Int
    ): Call<List<Conversation>>

    @FormUrlEncoded
    @POST("api/v1/filters")
    fun createFilter(
            @Field("phrase") phrase: String,
            @Field("context[]") context: List<String>,
            @Field("irreversible") irreversible: Boolean?,
            @Field("whole_word") wholeWord: Boolean?,
            @Field("expires_in") expiresIn: String?
    ): Call<Filter>

    @FormUrlEncoded
    @PUT("api/v1/filters/{id}")
    fun updateFilter(
            @Path("id") id: String,
            @Field("phrase") phrase: String,
            @Field("context[]") context: List<String>,
            @Field("irreversible") irreversible: Boolean?,
            @Field("whole_word") wholeWord: Boolean?,
            @Field("expires_in") expiresIn: String?
    ): Call<Filter>

    @DELETE("api/v1/filters/{id}")
    fun deleteFilter(
            @Path("id") id: String
    ): Call<ResponseBody>

    @FormUrlEncoded
    @POST("api/v1/polls/{id}/votes")
    fun voteInPoll(
            @Path("id") id: String,
            @Field("choices[]") choices: List<Int>
    ): Single<Poll>

    @POST("api/v1/accounts/{id}/block")
    fun blockAccountObservable(
            @Path("id") accountId: String
    ): Single<Relationship>

    @POST("api/v1/accounts/{id}/unblock")
    fun unblockAccountObservable(
            @Path("id") accountId: String
    ): Single<Relationship>

    @POST("api/v1/accounts/{id}/mute")
    fun muteAccountObservable(
            @Path("id") accountId: String
    ): Single<Relationship>

    @POST("api/v1/accounts/{id}/unmute")
    fun unmuteAccountObservable(
            @Path("id") accountId: String
    ): Single<Relationship>

    @GET("api/v1/accounts/relationships")
    fun relationshipsObservable(
            @Query("id[]") accountIds: List<String>
    ): Single<List<Relationship>>

    @FormUrlEncoded
    @POST("api/v1/reports")
    fun reportObservable(
            @Field("account_id") accountId: String,
            @Field("status_ids[]") statusIds: List<String>,
            @Field("comment") comment: String,
            @Field("forward") isNotifyRemote: Boolean?
    ): Single<ResponseBody>

    @GET("api/v1/accounts/{id}/statuses")
    fun accountStatusesObservable(
            @Path("id") accountId: String,
            @Query("max_id") maxId: String?,
            @Query("since_id") sinceId: String?,
            @Query("limit") limit: Int?,
            @Query("exclude_reblogs") excludeReblogs: Boolean?
    ): Single<List<Status>>

    @GET("api/v1/statuses/{id}")
    fun statusObservable(
            @Path("id") statusId: String
    ): Single<Status>

    @GET("api/v2/search")
    fun searchObservable(
            @Query("q") query: String?,
            @Query("type") type: String? = null,
            @Query("resolve") resolve: Boolean? = null,
            @Query("limit") limit: Int? = null,
            @Query("offset") offset: Int? = null,
            @Query("following") following: Boolean? = null
    ): Single<SearchResult>

}
