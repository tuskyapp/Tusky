package com.keylesspalace.tusky.network

import android.text.TextUtils
import com.keylesspalace.tusky.entity.Filter
import com.keylesspalace.tusky.entity.Status
import com.keylesspalace.tusky.util.parseAsMastodonHtml
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

    fun initWithFilters(filters: List<Filter>) {
        this.pattern = makeFilter(filters)
    }

    fun shouldFilterStatus(status: Status): Boolean {
        // Patterns are expensive and thread-safe, matchers are neither.
        val matcher = pattern?.matcher("") ?: return false

        if (status.poll != null) {
            val pollMatches = status.poll.options.any { matcher.reset(it.title).find() }
            if (pollMatches) return true
        }

        val spoilerText = status.actionableStatus.spoilerText
        val attachmentsDescriptions = status.attachments
            .mapNotNull { it.description }

        return (
            matcher.reset(status.actionableStatus.content.parseAsMastodonHtml().toString()).find() ||
                (spoilerText.isNotEmpty() && matcher.reset(spoilerText).find()) ||
                (
                    attachmentsDescriptions.isNotEmpty() &&
                        matcher.reset(attachmentsDescriptions.joinToString("\n"))
                            .find()
                    )
            )
    }

    private fun filterToRegexToken(filter: Filter): String? {
        val phrase = filter.phrase
        val quotedPhrase = Pattern.quote(phrase)
        return if (filter.wholeWord && ALPHANUMERIC.matcher(phrase).matches()) {
            String.format("(^|\\W)%s($|\\W)", quotedPhrase)
        } else {
            quotedPhrase
        }
    }

    private fun makeFilter(filters: List<Filter>): Pattern? {
        val now = Date()
        val nonExpiredFilters = filters.filter { it.expiresAt?.before(now) != true }
        if (nonExpiredFilters.isEmpty()) return null
        val tokens = nonExpiredFilters
            .map { filterToRegexToken(it) }

        return Pattern.compile(TextUtils.join("|", tokens), Pattern.CASE_INSENSITIVE)
    }

    companion object {
        private val ALPHANUMERIC = Pattern.compile("^\\w+$")
    }
}
