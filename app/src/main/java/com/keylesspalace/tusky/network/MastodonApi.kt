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

import at.connyduck.calladapter.networkresult.NetworkResult
import com.keylesspalace.tusky.components.filters.FilterExpiration
import com.keylesspalace.tusky.entity.AccessToken
import com.keylesspalace.tusky.entity.Account
import com.keylesspalace.tusky.entity.Announcement
import com.keylesspalace.tusky.entity.AppCredentials
import com.keylesspalace.tusky.entity.Attachment
import com.keylesspalace.tusky.entity.Conversation
import com.keylesspalace.tusky.entity.DeletedStatus
import com.keylesspalace.tusky.entity.Emoji
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.FilterKeyword
import com.keylesspalace.tusky.entity.FilterV1
import com.keylesspalace.tusky.entity.HashTag
import com.keylesspalace.tusky.entity.Instance
import com.keylesspalace.tusky.entity.InstanceV1
import com.keylesspalace.tusky.entity.Marker
import com.keylesspalace.tusky.entity.MastoList
import com.keylesspalace.tusky.entity.MediaUploadResult
import com.keylesspalace.tusky.entity.NewStatus
import com.keylesspalace.tusky.entity.Notification
import com.keylesspalace.tusky.entity.NotificationPolicy
import com.keylesspalace.tusky.entity.NotificationRequest
import com.keylesspalace.tusky.entity.NotificationSubscribeResult
import com.keylesspalace.tusky.entity.Poll
import com.keylesspalace.tusky.entity.Relationship
import com.keylesspalace.tusky.entity.ScheduledStatus
import com.keylesspalace.tusky.entity.ScheduledStatusReply
import com.keylesspalace.tusky.entity.SearchResult
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.entity.StatusContext
import com.keylesspalace.tusky.entity.StatusEdit
import com.keylesspalace.tusky.entity.StatusSource
import com.keylesspalace.tusky.entity.TimelineAccount
import com.keylesspalace.tusky.entity.Translation
import com.keylesspalace.tusky.entity.TrendingTag
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * for documentation of the Mastodon REST API see https://docs.joinmastodon.org/api/
 */

@JvmSuppressWildcards
interface MastodonApi {

    companion object {
        const val ENDPOINT_AUTHORIZE = "oauth/authorize"
        const val DOMAIN_HEADER = "domain"
        const val PLACEHOLDER_DOMAIN = "dummy.placeholder"
    }

    @GET("/api/v1/custom_emojis")
    suspend fun getCustomEmojis(): NetworkResult<List<Emoji>>

    @GET("api/v1/instance")
    suspend fun getInstanceV1(
        @Header(DOMAIN_HEADER) domain: String? = null
    ): NetworkResult<InstanceV1>

    @GET("api/v2/instance")
    suspend fun getInstance(
        @Header(DOMAIN_HEADER) domain: String? = null
    ): NetworkResult<Instance>

    @GET("api/v1/filters")
    suspend fun getFiltersV1(): NetworkResult<List<FilterV1>>

    @GET("api/v2/filters/{filterId}")
    suspend fun getFilter(@Path("filterId") filterId: String): NetworkResult<Filter>

    @GET("api/v2/filters")
    suspend fun getFilters(): NetworkResult<List<Filter>>

    @GET("api/v1/timelines/home")
    @Throws(Exception::class)
    suspend fun homeTimeline(
        @Query("max_id") maxId: String? = null,
        @Query("min_id") minId: String? = null,
        @Query("since_id") sinceId: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<List<Status>>

    @GET("api/v1/timelines/public")
    suspend fun publicTimeline(
        @Query("local") local: Boolean? = null,
        @Query("max_id") maxId: String? = null,
        @Query("since_id") sinceId: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<List<Status>>

    @GET("api/v1/timelines/tag/{hashtag}")
    suspend fun hashtagTimeline(
        @Path("hashtag") hashtag: String,
        @Query("any[]") any: List<String>?,
        @Query("local") local: Boolean?,
        @Query("max_id") maxId: String?,
        @Query("since_id") sinceId: String?,
        @Query("limit") limit: Int?
    ): Response<List<Status>>

    @GET("api/v1/timelines/list/{listId}")
    suspend fun listTimeline(
        @Path("listId") listId: String,
        @Query("max_id") maxId: String?,
        @Query("since_id") sinceId: String?,
        @Query("limit") limit: Int?
    ): Response<List<Status>>

    @GET("api/v1/notifications")
    @Throws(Exception::class)
    suspend fun notifications(
        /** Return results older than this ID */
        @Query("max_id") maxId: String? = null,
        /** Return results newer than this ID */
        @Query("since_id") sinceId: String? = null,
        /** Return results immediately newer than this ID */
        @Query("min_id") minId: String? = null,
        /** Maximum number of results to return. Defaults to 15, max is 30 */
        @Query("limit") limit: Int? = null,
        /** Types to excludes from the results */
        @Query("exclude_types[]") excludes: Set<Notification.Type>? = null,
        /** Return only notifications received from the specified account. */
        @Query("account_id") accountId: String? = null
    ): Response<List<Notification>>

    /** Fetch a single notification */
    @GET("api/v1/notifications/{id}")
    suspend fun notification(@Path("id") id: String): Response<Notification>

    @GET("api/v1/markers")
    suspend fun markersWithAuth(
        @Header("Authorization") auth: String,
        @Header(DOMAIN_HEADER) domain: String,
        @Query("timeline[]") timelines: List<String>
    ): Map<String, Marker>

    @FormUrlEncoded
    @POST("api/v1/markers")
    suspend fun updateMarkersWithAuth(
        @Header("Authorization") auth: String,
        @Header(DOMAIN_HEADER) domain: String,
        @Field("home[last_read_id]") homeLastReadId: String? = null,
        @Field("notifications[last_read_id]") notificationsLastReadId: String? = null
    ): NetworkResult<Unit>

    @GET("api/v1/notifications")
    suspend fun notificationsWithAuth(
        @Header("Authorization") auth: String,
        @Header(DOMAIN_HEADER) domain: String,
        /** Return results immediately newer than this ID */
        @Query("min_id") minId: String?
    ): Response<List<Notification>>

    @POST("api/v1/notifications/clear")
    suspend fun clearNotifications(): NetworkResult<Unit>

    @FormUrlEncoded
    @PUT("api/v1/media/{mediaId}")
    suspend fun updateMedia(
        @Path("mediaId") mediaId: String,
        @Field("description") description: String?,
        @Field("focus") focus: String?
    ): NetworkResult<Attachment>

    @GET("api/v1/media/{mediaId}")
    suspend fun getMedia(@Path("mediaId") mediaId: String): Response<MediaUploadResult>

    @POST("api/v1/statuses")
    suspend fun createStatus(
        @Header("Authorization") auth: String,
        @Header(DOMAIN_HEADER) domain: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body status: NewStatus
    ): NetworkResult<Status>

    @POST("api/v1/statuses")
    suspend fun createScheduledStatus(
        @Header("Authorization") auth: String,
        @Header(DOMAIN_HEADER) domain: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body status: NewStatus
    ): NetworkResult<ScheduledStatusReply>

    @GET("api/v1/statuses/{id}")
    suspend fun status(@Path("id") statusId: String): NetworkResult<Status>

    @PUT("api/v1/statuses/{id}")
    suspend fun editStatus(
        @Path("id") statusId: String,
        @Header("Authorization") auth: String,
        @Header(DOMAIN_HEADER) domain: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body editedStatus: NewStatus
    ): NetworkResult<Status>

    @GET("api/v1/statuses/{id}/source")
    suspend fun statusSource(@Path("id") statusId: String): NetworkResult<StatusSource>

    @GET("api/v1/statuses/{id}/context")
    suspend fun statusContext(@Path("id") statusId: String): NetworkResult<StatusContext>

    @GET("api/v1/statuses/{id}/history")
    suspend fun statusEdits(@Path("id") statusId: String): NetworkResult<List<StatusEdit>>

    @GET("api/v1/statuses/{id}/reblogged_by")
    suspend fun statusRebloggedBy(
        @Path("id") statusId: String,
        @Query("max_id") maxId: String?
    ): Response<List<TimelineAccount>>

    @GET("api/v1/statuses/{id}/favourited_by")
    suspend fun statusFavouritedBy(
        @Path("id") statusId: String,
        @Query("max_id") maxId: String?
    ): Response<List<TimelineAccount>>

    @DELETE("api/v1/statuses/{id}")
    suspend fun deleteStatus(
        @Path("id") statusId: String,
        @Query("delete_media") deleteMedia: Boolean? = null
    ): NetworkResult<DeletedStatus>

    @FormUrlEncoded
    @POST("api/v1/statuses/{id}/reblog")
    suspend fun reblogStatus(@Path("id") statusId: String, @Field("visibility") visibility: String?): NetworkResult<Status>

    @POST("api/v1/statuses/{id}/unreblog")
    suspend fun unreblogStatus(@Path("id") statusId: String): NetworkResult<Status>

    @POST("api/v1/statuses/{id}/favourite")
    suspend fun favouriteStatus(@Path("id") statusId: String): NetworkResult<Status>

    @POST("api/v1/statuses/{id}/unfavourite")
    suspend fun unfavouriteStatus(@Path("id") statusId: String): NetworkResult<Status>

    @POST("api/v1/statuses/{id}/bookmark")
    suspend fun bookmarkStatus(@Path("id") statusId: String): NetworkResult<Status>

    @POST("api/v1/statuses/{id}/unbookmark")
    suspend fun unbookmarkStatus(@Path("id") statusId: String): NetworkResult<Status>

    @POST("api/v1/statuses/{id}/pin")
    suspend fun pinStatus(@Path("id") statusId: String): NetworkResult<Status>

    @POST("api/v1/statuses/{id}/unpin")
    suspend fun unpinStatus(@Path("id") statusId: String): NetworkResult<Status>

    @POST("api/v1/statuses/{id}/mute")
    suspend fun muteConversation(@Path("id") statusId: String): NetworkResult<Status>

    @POST("api/v1/statuses/{id}/unmute")
    suspend fun unmuteConversation(@Path("id") statusId: String): NetworkResult<Status>

    @GET("api/v1/scheduled_statuses")
    suspend fun scheduledStatuses(
        @Query("limit") limit: Int? = null,
        @Query("max_id") maxId: String? = null
    ): NetworkResult<List<ScheduledStatus>>

    @DELETE("api/v1/scheduled_statuses/{id}")
    suspend fun deleteScheduledStatus(
        @Path("id") scheduledStatusId: String
    ): NetworkResult<Unit>

    @GET("api/v1/accounts/verify_credentials")
    suspend fun accountVerifyCredentials(
        @Header(DOMAIN_HEADER) domain: String? = null,
        @Header("Authorization") auth: String? = null
    ): NetworkResult<Account>

    @FormUrlEncoded
    @PATCH("api/v1/accounts/update_credentials")
    suspend fun accountUpdateSource(
        @Field("source[privacy]") privacy: String?,
        @Field("source[sensitive]") sensitive: Boolean?,
        @Field("source[language]") language: String?
    ): NetworkResult<Account>

    @Multipart
    @PATCH("api/v1/accounts/update_credentials")
    suspend fun accountUpdateCredentials(
        @Part(value = "display_name") displayName: RequestBody?,
        @Part(value = "note") note: RequestBody?,
        @Part(value = "locked") locked: RequestBody?,
        @Part avatar: MultipartBody.Part?,
        @Part header: MultipartBody.Part?,
        @PartMap fields: Map<String, RequestBody>
    ): NetworkResult<Account>

    @GET("api/v1/accounts/search")
    suspend fun searchAccounts(
        @Query("q") query: String,
        @Query("resolve") resolve: Boolean? = null,
        @Query("limit") limit: Int? = null,
        @Query("following") following: Boolean? = null
    ): NetworkResult<List<TimelineAccount>>

    @GET("api/v1/accounts/{id}")
    suspend fun account(@Path("id") accountId: String): NetworkResult<Account>

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
    suspend fun accountStatuses(
        @Path("id") accountId: String,
        @Query("max_id") maxId: String? = null,
        @Query("since_id") sinceId: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("exclude_replies") excludeReplies: Boolean? = null,
        @Query("only_media") onlyMedia: Boolean? = null,
        @Query("pinned") pinned: Boolean? = null
    ): Response<List<Status>>

    @GET("api/v1/accounts/{id}/followers")
    suspend fun accountFollowers(
        @Path("id") accountId: String,
        @Query("max_id") maxId: String?
    ): Response<List<TimelineAccount>>

    @GET("api/v1/accounts/{id}/following")
    suspend fun accountFollowing(
        @Path("id") accountId: String,
        @Query("max_id") maxId: String?
    ): Response<List<TimelineAccount>>

    @FormUrlEncoded
    @POST("api/v1/accounts/{id}/follow")
    suspend fun followAccount(
        @Path("id") accountId: String,
        @Field("reblogs") showReblogs: Boolean? = null,
        @Field("notify") notify: Boolean? = null
    ): NetworkResult<Relationship>

    @POST("api/v1/accounts/{id}/unfollow")
    suspend fun unfollowAccount(@Path("id") accountId: String): NetworkResult<Relationship>

    @POST("api/v1/accounts/{id}/block")
    suspend fun blockAccount(@Path("id") accountId: String): NetworkResult<Relationship>

    @POST("api/v1/accounts/{id}/unblock")
    suspend fun unblockAccount(@Path("id") accountId: String): NetworkResult<Relationship>

    @FormUrlEncoded
    @POST("api/v1/accounts/{id}/mute")
    suspend fun muteAccount(
        @Path("id") accountId: String,
        @Field("notifications") notifications: Boolean? = null,
        @Field("duration") duration: Int? = null
    ): NetworkResult<Relationship>

    @POST("api/v1/accounts/{id}/unmute")
    suspend fun unmuteAccount(@Path("id") accountId: String): NetworkResult<Relationship>

    @GET("api/v1/accounts/relationships")
    suspend fun relationships(
        @Query("id[]") accountIds: List<String>
    ): NetworkResult<List<Relationship>>

    @POST("api/v1/pleroma/accounts/{id}/subscribe")
    suspend fun subscribeAccount(@Path("id") accountId: String): NetworkResult<Relationship>

    @POST("api/v1/pleroma/accounts/{id}/unsubscribe")
    suspend fun unsubscribeAccount(@Path("id") accountId: String): NetworkResult<Relationship>

    @GET("api/v1/blocks")
    suspend fun blocks(@Query("max_id") maxId: String? = null): Response<List<TimelineAccount>>

    @GET("api/v1/mutes")
    suspend fun mutes(@Query("max_id") maxId: String? = null): Response<List<TimelineAccount>>

    @GET("api/v1/domain_blocks")
    suspend fun domainBlocks(
        @Query("max_id") maxId: String? = null,
        @Query("since_id") sinceId: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<List<String>>

    @FormUrlEncoded
    @POST("api/v1/domain_blocks")
    suspend fun blockDomain(@Field("domain") domain: String): NetworkResult<Unit>

    @FormUrlEncoded
    // @DELETE doesn't support fields
    @HTTP(method = "DELETE", path = "api/v1/domain_blocks", hasBody = true)
    suspend fun unblockDomain(@Field("domain") domain: String): NetworkResult<Unit>

    @GET("api/v1/favourites")
    suspend fun favourites(
        @Query("max_id") maxId: String?,
        @Query("since_id") sinceId: String?,
        @Query("limit") limit: Int?
    ): Response<List<Status>>

    @GET("api/v1/bookmarks")
    suspend fun bookmarks(
        @Query("max_id") maxId: String?,
        @Query("since_id") sinceId: String?,
        @Query("limit") limit: Int?
    ): Response<List<Status>>

    @GET("api/v1/follow_requests")
    suspend fun followRequests(@Query("max_id") maxId: String?): Response<List<TimelineAccount>>

    @POST("api/v1/follow_requests/{id}/authorize")
    suspend fun authorizeFollowRequest(@Path("id") accountId: String): NetworkResult<Relationship>

    @POST("api/v1/follow_requests/{id}/reject")
    suspend fun rejectFollowRequest(@Path("id") accountId: String): NetworkResult<Relationship>

    @FormUrlEncoded
    @POST("api/v1/apps")
    suspend fun authenticateApp(
        @Header(DOMAIN_HEADER) domain: String,
        @Field("client_name") clientName: String,
        @Field("redirect_uris") redirectUris: String,
        @Field("scopes") scopes: String,
        @Field("website") website: String
    ): NetworkResult<AppCredentials>

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun fetchOAuthToken(
        @Header(DOMAIN_HEADER) domain: String,
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("code") code: String,
        @Field("grant_type") grantType: String
    ): NetworkResult<AccessToken>

    @FormUrlEncoded
    @POST("oauth/revoke")
    suspend fun revokeOAuthToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("token") token: String
    ): NetworkResult<Unit>

    @GET("/api/v1/lists")
    suspend fun getLists(): NetworkResult<List<MastoList>>

    @GET("/api/v1/accounts/{id}/lists")
    suspend fun getListsIncludesAccount(
        @Path("id") accountId: String
    ): NetworkResult<List<MastoList>>

    @FormUrlEncoded
    @POST("api/v1/lists")
    suspend fun createList(
        @Field("title") title: String,
        @Field("exclusive") exclusive: Boolean?,
        @Field("replies_policy") replyPolicy: String
    ): NetworkResult<MastoList>

    @FormUrlEncoded
    @PUT("api/v1/lists/{listId}")
    suspend fun updateList(
        @Path("listId") listId: String,
        @Field("title") title: String,
        @Field("exclusive") exclusive: Boolean?,
        @Field("replies_policy") replyPolicy: String
    ): NetworkResult<MastoList>

    @DELETE("api/v1/lists/{listId}")
    suspend fun deleteList(@Path("listId") listId: String): NetworkResult<Unit>

    @GET("api/v1/lists/{listId}/accounts")
    suspend fun getAccountsInList(
        @Path("listId") listId: String,
        @Query("limit") limit: Int
    ): NetworkResult<List<TimelineAccount>>

    @FormUrlEncoded
    // @DELETE doesn't support fields
    @HTTP(method = "DELETE", path = "api/v1/lists/{listId}/accounts", hasBody = true)
    suspend fun deleteAccountFromList(
        @Path("listId") listId: String,
        @Field("account_ids[]") accountIds: List<String>
    ): NetworkResult<Unit>

    @FormUrlEncoded
    @POST("api/v1/lists/{listId}/accounts")
    suspend fun addAccountToList(
        @Path("listId") listId: String,
        @Field("account_ids[]") accountIds: List<String>
    ): NetworkResult<Unit>

    @GET("/api/v1/conversations")
    suspend fun getConversations(
        @Query("max_id") maxId: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<List<Conversation>>

    @DELETE("/api/v1/conversations/{id}")
    suspend fun deleteConversation(@Path("id") conversationId: String)

    @FormUrlEncoded
    @POST("api/v1/filters")
    suspend fun createFilterV1(
        @Field("phrase") phrase: String,
        @Field("context[]") context: List<String>,
        @Field("irreversible") irreversible: Boolean?,
        @Field("whole_word") wholeWord: Boolean?,
        @Field("expires_in") expiresIn: FilterExpiration?
    ): NetworkResult<FilterV1>

    @FormUrlEncoded
    @PUT("api/v1/filters/{id}")
    suspend fun updateFilterV1(
        @Path("id") id: String,
        @Field("phrase") phrase: String,
        @Field("context[]") context: List<String>,
        @Field("irreversible") irreversible: Boolean?,
        @Field("whole_word") wholeWord: Boolean?,
        @Field("expires_in") expiresIn: FilterExpiration?
    ): NetworkResult<FilterV1>

    @DELETE("api/v1/filters/{id}")
    suspend fun deleteFilterV1(@Path("id") id: String): NetworkResult<Unit>

    @FormUrlEncoded
    @POST("api/v2/filters")
    suspend fun createFilter(
        @Field("title") title: String,
        @Field("context[]") context: List<Filter.Kind>,
        @Field("filter_action") filterAction: Filter.Action,
        @Field("expires_in") expiresIn: FilterExpiration?
    ): NetworkResult<Filter>

    @FormUrlEncoded
    @PUT("api/v2/filters/{id}")
    suspend fun updateFilter(
        @Path("id") id: String,
        @Field("title") title: String? = null,
        @Field("context[]") context: List<Filter.Kind>? = null,
        @Field("filter_action") filterAction: Filter.Action? = null,
        @Field("expires_in") expires: FilterExpiration? = null
    ): NetworkResult<Filter>

    @DELETE("api/v2/filters/{id}")
    suspend fun deleteFilter(@Path("id") id: String): NetworkResult<Unit>

    @FormUrlEncoded
    @POST("api/v2/filters/{filterId}/keywords")
    suspend fun addFilterKeyword(
        @Path("filterId") filterId: String,
        @Field("keyword") keyword: String,
        @Field("whole_word") wholeWord: Boolean
    ): NetworkResult<FilterKeyword>

    @FormUrlEncoded
    @PUT("api/v2/filters/keywords/{keywordId}")
    suspend fun updateFilterKeyword(
        @Path("keywordId") keywordId: String,
        @Field("keyword") keyword: String,
        @Field("whole_word") wholeWord: Boolean
    ): NetworkResult<FilterKeyword>

    @DELETE("api/v2/filters/keywords/{keywordId}")
    suspend fun deleteFilterKeyword(
        @Path("keywordId") keywordId: String
    ): NetworkResult<Unit>

    @FormUrlEncoded
    @POST("api/v1/polls/{id}/votes")
    suspend fun voteInPoll(
        @Path("id") id: String,
        @Field("choices[]") choices: List<Int>
    ): NetworkResult<Poll>

    @GET("api/v1/announcements")
    suspend fun announcements(): NetworkResult<List<Announcement>>

    @POST("api/v1/announcements/{id}/dismiss")
    suspend fun dismissAnnouncement(@Path("id") announcementId: String): NetworkResult<Unit>

    @PUT("api/v1/announcements/{id}/reactions/{name}")
    suspend fun addAnnouncementReaction(
        @Path("id") announcementId: String,
        @Path("name") name: String
    ): NetworkResult<Unit>

    @DELETE("api/v1/announcements/{id}/reactions/{name}")
    suspend fun removeAnnouncementReaction(
        @Path("id") announcementId: String,
        @Path("name") name: String
    ): NetworkResult<Unit>

    @FormUrlEncoded
    @POST("api/v1/reports")
    suspend fun report(
        @Field("account_id") accountId: String,
        @Field("status_ids[]") statusIds: List<String>,
        @Field("comment") comment: String,
        @Field("forward") isNotifyRemote: Boolean?
    ): NetworkResult<Unit>

    @GET("api/v1/accounts/{id}/statuses")
    suspend fun accountStatuses(
        @Path("id") accountId: String,
        @Query("max_id") maxId: String?,
        @Query("since_id") sinceId: String?,
        @Query("min_id") minId: String?,
        @Query("limit") limit: Int?,
        @Query("exclude_reblogs") excludeReblogs: Boolean?
    ): NetworkResult<List<Status>>

    @GET("api/v2/search")
    suspend fun search(
        @Query("q") query: String?,
        @Query("type") type: String? = null,
        @Query("resolve") resolve: Boolean? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("following") following: Boolean? = null
    ): NetworkResult<SearchResult>

    @FormUrlEncoded
    @POST("api/v1/accounts/{id}/note")
    suspend fun updateAccountNote(
        @Path("id") accountId: String,
        @Field("comment") note: String
    ): NetworkResult<Relationship>

    @GET("api/v1/push/subscription")
    suspend fun pushNotificationSubscription(
        @Header("Authorization") auth: String,
        @Header(DOMAIN_HEADER) domain: String
    ): NetworkResult<NotificationSubscribeResult>

    @FormUrlEncoded
    @POST("api/v1/push/subscription")
    suspend fun subscribePushNotifications(
        @Header("Authorization") auth: String,
        @Header(DOMAIN_HEADER) domain: String,
        @Field("subscription[standard]") standard: Boolean,
        @Field("subscription[endpoint]") endpoint: String,
        @Field("subscription[keys][p256dh]") keysP256DH: String,
        @Field("subscription[keys][auth]") keysAuth: String,
        // The "data[alerts][]" fields to enable / disable notifications
        // Should be generated dynamically from all the available notification
        // types defined in [com.keylesspalace.tusky.entities.Notification.Types]
        @FieldMap data: Map<String, Boolean>
    ): NetworkResult<NotificationSubscribeResult>

    @FormUrlEncoded
    @PUT("api/v1/push/subscription")
    suspend fun updatePushNotificationSubscription(
        @Header("Authorization") auth: String,
        @Header(DOMAIN_HEADER) domain: String,
        @FieldMap data: Map<String, Boolean>
    ): NetworkResult<NotificationSubscribeResult>

    @DELETE("api/v1/push/subscription")
    suspend fun unsubscribePushNotifications(
        @Header("Authorization") auth: String,
        @Header(DOMAIN_HEADER) domain: String
    ): NetworkResult<Unit>

    @GET("api/v1/tags/{name}")
    suspend fun tag(@Path("name") name: String): NetworkResult<HashTag>

    @GET("api/v1/followed_tags")
    suspend fun followedTags(
        @Query("min_id") minId: String? = null,
        @Query("since_id") sinceId: String? = null,
        @Query("max_id") maxId: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<List<HashTag>>

    @POST("api/v1/tags/{name}/follow")
    suspend fun followTag(@Path("name") name: String): NetworkResult<HashTag>

    @POST("api/v1/tags/{name}/unfollow")
    suspend fun unfollowTag(@Path("name") name: String): NetworkResult<HashTag>

    @GET("api/v1/trends/tags")
    suspend fun trendingTags(): NetworkResult<List<TrendingTag>>

    @GET("api/v1/trends/statuses")
    suspend fun trendingStatuses(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: String? = null
    ): Response<List<Status>>

    @FormUrlEncoded
    @POST("api/v1/statuses/{id}/translate")
    suspend fun translate(
        @Path("id") statusId: String,
        @Field("lang") targetLanguage: String?
    ): NetworkResult<Translation>

    @GET("api/v2/notifications/policy")
    suspend fun notificationPolicy(): NetworkResult<NotificationPolicy>

    @FormUrlEncoded
    @PATCH("api/v2/notifications/policy")
    suspend fun updateNotificationPolicy(
        @Field("for_not_following") forNotFollowing: String?,
        @Field("for_not_followers") forNotFollowers: String?,
        @Field("for_new_accounts") forNewAccounts: String?,
        @Field("for_private_mentions") forPrivateMentions: String?,
        @Field("for_limited_accounts") forLimitedAccounts: String?
    ): NetworkResult<NotificationPolicy>

    @GET("api/v1/notifications/requests")
    suspend fun getNotificationRequests(
        @Query("max_id") maxId: String? = null,
        @Query("min_id") minId: String? = null,
        @Query("since_id") sinceId: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<List<NotificationRequest>>

    @POST("api/v1/notifications/requests/{id}/accept")
    suspend fun acceptNotificationRequest(@Path("id") notificationId: String): NetworkResult<Unit>

    @POST("api/v1/notifications/requests/{id}/dismiss")
    suspend fun dismissNotificationRequest(@Path("id") notificationId: String): NetworkResult<Unit>
}
