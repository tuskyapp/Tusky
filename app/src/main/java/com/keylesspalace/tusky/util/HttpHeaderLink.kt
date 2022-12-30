/* Copyright 2022 Tusky Contributors
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
package com.keylesspalace.tusky.util

import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri

/**
 * Represents one link and its parameters from the link header of an HTTP message.
 *
 * @see [RFC5988](https://tools.ietf.org/html/rfc5988)
 */
class HttpHeaderLink @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE) constructor(
    uri: String
) {
    data class Parameter(val name: String, val value: String?)

    private val parameters: MutableList<Parameter> = ArrayList()

    val uri: Uri = uri.toUri()

    private data class ValueResult(val value: String, val end: Int = -1)

    companion object {
        private fun findEndOfQuotedString(line: String, start: Int): Int {
            var i = start
            while (i < line.length) {
                val c = line[i]
                if (c == '\\') {
                    i += 1
                } else if (c == '"') {
                    return i
                }
                i++
            }
            return -1
        }

        private fun parseValue(line: String, start: Int): ValueResult {
            val foundIndex = line.indexOfAny(charArrayOf(';', ',', '"'), start, false)
            if (foundIndex == -1) {
                return ValueResult(line.substring(start).trim())
            }
            val c = line[foundIndex]
            return if (c == ';' || c == ',') {
                ValueResult(line.substring(start, foundIndex).trim(), foundIndex)
            } else {
                var quoteEnd = findEndOfQuotedString(line, foundIndex + 1)
                if (quoteEnd == -1) {
                    quoteEnd = line.length
                }
                ValueResult(line.substring(foundIndex + 1, quoteEnd).trim(), quoteEnd)
            }
        }

        private fun parseParameters(line: String, start: Int, link: HttpHeaderLink): Int {
            var i = start
            while (i < line.length) {
                val foundIndex = line.indexOfAny(charArrayOf('=', ','), i, false)
                if (foundIndex == -1) {
                    return -1
                } else if (line[foundIndex] == ',') {
                    return foundIndex
                }
                val name = line.substring(line.indexOf(';', i) + 1, foundIndex).trim()
                val result = parseValue(line, foundIndex)
                val value = result.value
                val parameter = Parameter(name, value)
                link.parameters.add(parameter)
                i = if (result.end == -1) {
                    return -1
                } else {
                    result.end
                }
            }
            return -1
        }

        /**
         * @param line the entire link header, not including the initial "Link:"
         * @return all links found in the header
         */
        fun parse(line: String?): List<HttpHeaderLink> {
            val links: MutableList<HttpHeaderLink> = mutableListOf()
            line ?: return links

            var i = 0
            while (i < line.length) {
                val uriEnd = line.indexOf('>', i)
                val uri = line.substring(line.indexOf('<', i) + 1, uriEnd)
                val link = HttpHeaderLink(uri)
                links.add(link)
                val parseEnd = parseParameters(line, uriEnd, link)
                i = if (parseEnd == -1) {
                    break
                } else {
                    parseEnd
                }
                i++
            }

            return links
        }

        /**
         * @param links intended to be those returned by parse()
         * @param relationType of the parameter "rel", commonly "next" or "prev"
         * @return the link matching the given relation type
         */
        fun findByRelationType(
            links: List<HttpHeaderLink>,
            relationType: String
        ): HttpHeaderLink? {
            return links.find { link ->
                link.parameters.any { parameter ->
                    parameter.name == "rel" && parameter.value == relationType
                }
            }
        }
    }
}
