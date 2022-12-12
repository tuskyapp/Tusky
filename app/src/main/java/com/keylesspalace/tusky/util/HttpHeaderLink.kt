/* Written in 2017 by Andrew Dawson
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and
 * neighboring rights to this software to the public domain worldwide. This software is distributed
 * without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package com.keylesspalace.tusky.util

import android.net.Uri

/**
 * Represents one link and its parameters from the link header of an HTTP message.
 *
 * @see [RFC5988](https://tools.ietf.org/html/rfc5988)
 */
class HttpHeaderLink private constructor(uri: String) {
    data class Parameter(var name: String? = null, var value: String? = null)

    private val parameters: MutableList<Parameter> = ArrayList()

    var uri: Uri

    init {
        this.uri = Uri.parse(uri)
    }

    private class ValueResult {
        var value: String? = null
            set(value) {
                value ?: return
                val v = value.trim()
                if (v.isNotEmpty()) {
                    field = v
                }
            }
        var end: Int = -1
    }

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
            val result = ValueResult()
            val foundIndex = line.indexOfAny(charArrayOf(';', ',', '"'), start, false)
            if (foundIndex == -1) {
                result.value = line.substring(start)
                return result
            }
            val c = line[foundIndex]
            return if (c == ';' || c == ',') {
                result.end = foundIndex
                result.value = line.substring(start, foundIndex)
                result
            } else {
                var quoteEnd = findEndOfQuotedString(line, foundIndex + 1)
                if (quoteEnd == -1) {
                    quoteEnd = line.length
                }
                result.end = quoteEnd
                result.value = line.substring(foundIndex + 1, quoteEnd)
                result
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
                val parameter = Parameter()
                parameter.name =
                    line.substring(line.indexOf(';', i) + 1, foundIndex).trim()
                link.parameters.add(parameter)
                val result = parseValue(line, foundIndex)
                parameter.value = result.value
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
            val linkList: MutableList<HttpHeaderLink> = ArrayList()
            line ?: return linkList

            var i = 0
            while (i < line.length) {
                val uriEnd = line.indexOf('>', i)
                val uri = line.substring(line.indexOf('<', i) + 1, uriEnd)
                val link = HttpHeaderLink(uri)
                linkList.add(link)
                val parseEnd = parseParameters(line, uriEnd, link)
                i = if (parseEnd == -1) {
                    break
                } else {
                    parseEnd
                }
                i++
            }

            return linkList
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
            for (link in links) {
                for (parameter in link.parameters) {
                    if (parameter.name == "rel" && parameter.value == relationType) {
                        return link
                    }
                }
            }
            return null
        }
    }
}
