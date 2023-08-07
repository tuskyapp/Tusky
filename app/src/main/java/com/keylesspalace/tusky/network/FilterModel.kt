package com.keylesspalace.tusky.network

import com.keylesspalace.tusky.core.database.model.Filter
import com.keylesspalace.tusky.core.database.model.FilterV1
import com.keylesspalace.tusky.core.database.model.Status
import com.keylesspalace.tusky.core.text.parseAsMastodonHtml
import java.util.Date
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * One-stop for status filtering logic using Mastodon's filters.
 *
 * 1. You init with [initWithFilters], this compiles regex pattern.
 * 2. You call [shouldFilterStatus] to figure out what to display when you load statuses.
 */
class FilterModel @Inject constructor() {
    private var pattern: Pattern? = null
    private var v1 = false
    lateinit var kind: Filter.Kind

    fun initWithFilters(filters: List<FilterV1>) {
        v1 = true
        this.pattern = makeFilter(filters)
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

        val matchingKind = status.filtered?.filter { result ->
            result.filter.kinds.contains(kind)
        }

        return if (matchingKind.isNullOrEmpty()) {
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
        private val ALPHANUMERIC = Pattern.compile("^\\w+$")
    }
}
