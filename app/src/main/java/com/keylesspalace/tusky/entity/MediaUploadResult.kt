package com.keylesspalace.tusky.entity

/**
 * The same as Attachment, except the url is null - see https://docs.joinmastodon.org/methods/statuses/media/
 * We are only interested in the id, so other attributes are omitted
 */
data class MediaUploadResult(
    val id: String
)
