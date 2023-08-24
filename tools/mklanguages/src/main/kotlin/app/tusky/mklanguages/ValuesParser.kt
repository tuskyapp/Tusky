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

package app.tusky.mklanguages

import com.github.h0tk3y.betterParse.combinators.and
import com.github.h0tk3y.betterParse.combinators.optional
import com.github.h0tk3y.betterParse.combinators.or
import com.github.h0tk3y.betterParse.combinators.skip
import com.github.h0tk3y.betterParse.combinators.times
import com.github.h0tk3y.betterParse.combinators.use
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.lexer.literalToken
import com.github.h0tk3y.betterParse.lexer.regexToken

data class MobileCodes(val mcc: String, val mnc: String? = null)

data class Locale(val lang: String, val region: String? = null, val script: String? = null)

data class ConfigurationQualifier(val mobileCodes: MobileCodes? = null, val locale: Locale? = null)

/**
 * Parse an Android `values-*` resource directory name and extract the configuration qualifiers
 *
 * Directory name components and their ordering is described in
 * https://developer.android.com/guide/topics/resources/providing-resources#table2).
 */
// TODO: At the moment this only deals with Locale, as that's what mklanguages needs. This could
// be expanded in to a general parser for `values-*` directories if we needed that.
class ValuesParser : Grammar<ConfigurationQualifier>() {
    // Tokenizers
    private val values by literalToken("values")
    private val sep by literalToken("-")
    private val mobileCodes by regexToken("(?i:mcc\\d+)(?i:mnc\\d+)?")
    private val locale by regexToken("(?i:[a-z]{2,3})(?i:-r([a-z]{2,3}))?(?=-|$)")
    private val bcpStartTag by regexToken("(?i:b\\+[a-z]{2,3})")
    private val bcpSubtag by regexToken("(?i:\\+[a-z]+)")
    private val layoutDirection by regexToken("(?i:ldrtl|ldltr)")
    private val smallestWidth by regexToken("(?i:sw\\d+dp)")
    private val availableDimen by regexToken("(?i:[wh]\\d+dp)")
    private val screenSize by regexToken("(?i:small|normal|large|xlarge)")
    private val screenAspect by regexToken("(?i:long|notlong)")
    private val roundScreen by regexToken("(?i:round|notround)")
    private val wideColorGamut by regexToken("(?i:widecg|nowidecg)")
    private val highDynamicRange by regexToken("(?i:highdr|lowdr)")
    private val screenOrientation by regexToken("(?i:port|land)")
    private val uiMode by regexToken("(?i:car|desk|television|appliance|watch|vrheadset)")
    private val nightMode by regexToken("(?i:night|notNight)")
    private val screenDpi by regexToken("(?i:(?:l|m|h|x|xx|xxx|no|tv|any|\\d+)dpi)")
    private val touchScreen by regexToken("(?i:notouch|finger)")
    private val keyboardAvailability by regexToken("(?i:keysexposed|keyshidden|keyssoft)")
    private val inputMethod by regexToken("(?i:nokeys|qwerty|12key)")
    private val navKeyAvailability by regexToken("(?i:naxexposed|navhidden)")
    private val navMethod by regexToken("(?i:nonav|dpad|trackball|wheel)")
    private val platformVersion by regexToken("(?i:v\\d+)")

    // Parsers
    private val mobileCodesParser by mobileCodes use {
        val parts = this.text.split("-")
        MobileCodes(mcc = parts[0], mnc = parts.getOrNull(1))
    }

    private val localeParser by locale use {
        val parts = this.text.split("-r".toRegex(), 2)
        Locale(lang = parts[0], region = parts.getOrNull(1))
    }

    private val bcpLocaleParser = bcpStartTag and (0..2 times bcpSubtag) use {
        Locale(
            lang = this.t1.text.split("+")[1],
            script = this.t2.getOrNull(0)?.text?.split("+")?.get(1),
            region = this.t2.getOrNull(1)?.text?.split("+")?.get(1)
        )
    }

    private val qualifier = skip(values) and
        optional(skip(sep) and mobileCodesParser) and
        optional(skip(sep) and (localeParser or bcpLocaleParser)) and
        optional(skip(sep) and layoutDirection) and
        optional(skip(sep) and smallestWidth) and
        optional(skip(sep) and availableDimen) and
        optional(skip(sep) and screenSize) and
        optional(skip(sep) and screenAspect) and
        optional(skip(sep) and roundScreen) and
        optional(skip(sep) and wideColorGamut) and
        optional(skip(sep) and highDynamicRange) and
        optional(skip(sep) and screenOrientation) and
        optional(skip(sep) and uiMode) and
        optional(skip(sep) and nightMode) and
        optional(skip(sep) and screenDpi) and
        optional(skip(sep) and touchScreen) and
        // Commented out to work around https://github.com/h0tk3y/better-parse/issues/64
//        optional(skip(sep) and keyboardAvailability) and
//        optional(skip(sep) and inputMethod) and
//        optional(skip(sep) and navKeyAvailability) and
//        optional(skip(sep) and navMethod) and
        optional(skip(sep) and platformVersion)

    private val qualifierParser by qualifier use {
        ConfigurationQualifier(
            mobileCodes = this.t1,
            locale = this.t2
        )
    }

    override val rootParser by qualifierParser
}
