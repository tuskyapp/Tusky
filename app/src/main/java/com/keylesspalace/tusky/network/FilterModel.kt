package com.keylesspalace.tusky.network

import android.util.Log
import at.connyduck.calladapter.networkresult.fold
import at.connyduck.calladapter.networkresult.getOrElse
import com.keylesspalace.tusky.components.instanceinfo.InstanceInfoRepository
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.FilterV1
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.util.isHttpNotFound
import com.keylesspalace.tusky.util.parseAsMastodonHtml
import java.util.Date
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * One-stop for status filtering logic using Mastodon's filters.
 *
 * 1. You init with [init], this checks which filter version to use and compiles regex pattern if needed.
 * 2. You call [shouldFilterStatus] to figure out what to display when you load statuses.
 */
class FilterModel @Inject constructor(
    private val instanceInfoRepo: InstanceInfoRepository,
    private val api: MastodonApi
) {
    private var pattern: Pattern? = null
    private var v1 = false
    private lateinit var kind: Filter.Kind

    /**
     * @param kind the [Filter.Kind] that should be filtered
     * @return true when filters v1 have been loaded successfully and the currently shown posts may need to be filtered
     */
    suspend fun init(kind: Filter.Kind): Boolean {
        this.kind = kind

        if (instanceInfoRepo.isFilterV2Supported()) {
            // nothing to do - Instance supports V2 so posts are filtered by the server
            return false
        }

        api.getFilters().fold(
            {
                instanceInfoRepo.saveFilterV2Support(true)
                return false
            },
            { throwable ->
                if (throwable.isHttpNotFound()) {
                    val filters = api.getFiltersV1().getOrElse {
                        Log.w(TAG, "Failed to fetch filters", it)
                        return false
                    }

                    this.v1 = true

                    val activeFilters = filters.filter { filter -> filter.context.contains(kind.kind) }

                    this.pattern = makeFilter(activeFilters)

                    return activeFilters.isNotEmpty()
                } else {
                    Log.e(TAG, "Error getting filters", throwable)
                    return false
                }
            }
        )
    }

    fun shouldFilterStatus(status: Status): Filter.Action {
        if (v1) {
            // Patterns are expensive and thread-safe, matchers are neither.
            val matcher = pattern?.matcher("") ?: return Filter.Action.NONE

            if (status.poll?.options?.any { matcher.reset(it.title).find() } == true) {
                return Filter.Action.HIDE
            }

            val spoilerText = status.actionableStatus.spoilerText
            val attachmentsDescriptions = status.attachments.mapNotNull { it.description }

            return if (
                matcher.reset(status.actionableStatus.content.parseAsMastodonHtml().toString()).find() ||
                (spoilerText.isNotEmpty() && matcher.reset(spoilerText).find()) ||
                (attachmentsDescriptions.isNotEmpty() && matcher.reset(attachmentsDescriptions.joinToString("\n")).find())
            ) {
                Filter.Action.HIDE
            } else {
                Filter.Action.NONE
            }
        }

        val matchingKind = status.filtered.orEmpty().filter { result ->
            result.filter.kinds.contains(kind)
        }

        return if (matchingKind.isEmpty()) {
            Filter.Action.NONE
        } else {
            matchingKind.maxOf { it.filter.action }
        }
    }

    private fun filterToRegexToken(filter: FilterV1): String? {
        val phrase = filter.phrase
        val quotedPhrase = Pattern.quote(phrase)
        return if (filter.wholeWord && ALPHANUMERIC.matcher(phrase).matches()) {
            String.format("(^|\\W)%s($|\\W)", quotedPhrase)
        } else {
            quotedPhrase
        }
    }

    private fun makeFilter(filters: List<FilterV1>): Pattern? {
        val now = Date()
        val nonExpiredFilters = filters.filter { it.expiresAt?.before(now) != true }
        if (nonExpiredFilters.isEmpty()) return null
        val tokens = nonExpiredFilters
            .asSequence()
            .map { filterToRegexToken(it) }
            .joinToString("|")

        return Pattern.compile(tokens, Pattern.CASE_INSENSITIVE)
    }

    companion object {
        private const val TAG = "FilterModel"
        private val ALPHANUMERIC = Pattern.compile("^\\w+$")
    }
}
