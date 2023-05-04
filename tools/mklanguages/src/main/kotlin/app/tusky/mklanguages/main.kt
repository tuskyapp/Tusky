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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.ibm.icu.text.CaseMap
import com.ibm.icu.text.Collator
import com.ibm.icu.util.ULocale
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

/** The information needed to encode a language in the XML resources */
data class Language(
    /** Language code */
    val code: String,

    /**
     * Name of the language, in that language. E.g., the display name for English is "English",
     * the display name for Icelandic is "Íslenska".
     */
    val displayName: String,

    /** Name of the language in English */
    val displayNameEnglish: String
) {
    companion object {
        private val toTitle = CaseMap.toTitle()

        /** Create a [Language] from a [ULocale] */
        fun from(locale: ULocale): Language {
            return when (locale.name) {
                // Berber is special cased, as at the time of writing the ICU data does not
                // recognise the language code "ber".
                "ber" -> Language(
                    "ber",
                    "ⵣⴰⵎⴰⵣⵉⵖⵝ",
                    "Berber"
                )
                else -> Language(
                    locale.name.replace("_", "-"),
                    toTitle.apply(locale.toLocale(), null, locale.getDisplayName(locale)),
                    locale.getDisplayName(ULocale.ENGLISH)
                )
            }
        }
    }
}

/**
 * Constructs the `language_entries` and `language_values` string arrays in donottranslate.xml.
 *
 * - Finds all the `values-*` directories that contain `strings.xml`
 * - Parses out the language code from the directory name
 * - Uses the ICU libraries to determine the correct name for the language
 * - Sorts the list of languages using ICU collation rules
 * - Updates donottranslate.xml with the new data
 *
 * Run this after creating a new translation.
 *
 * Run with `gradlew :tools:mklanguages:run` or `runtools mklanguages`.
 */
class App: CliktCommand(help = """Update languages in donottranslate.xml""") {
    private val verbose by option("-n", "--verbose", help="show additional information").flag()

    /**
     * Returns the full path to the Tusky `.../app/src/main/res` directory, starting from the
     * given [start] directory, walking up the tree if it can't be found there.
     *
     * @return the path, or null if it's not a subtree of [start] or any of its parents.
     */
    fun findResourcePath(start: Path): Path? {
        val suffix = Path("app/src/main/res")

        var prefix = start
        var resourcePath: Path
        do {
            resourcePath = prefix / suffix
            if (resourcePath.exists()) return resourcePath
            prefix = prefix.parent
        } while(prefix != prefix.root)

        return null
    }

    override fun run() {
        System.setProperty("file.encoding", "UTF8")

        val cwd = Paths.get("").toAbsolutePath()
        if (verbose) println("Working directory: $cwd")

        val resourcePath = findResourcePath(cwd) ?: throw UsageError("could not find app/src/main/res in tree")

        // Enumerate all the values-* directories that contain a strings.xml file
        val resourceDirs = resourcePath.listDirectoryEntries("values-*")
            .filter { entry -> entry.isDirectory() }
            .filter { dir -> (dir / "strings.xml").isRegularFile() }

        if (resourceDirs.isEmpty()) throw UsageError("no strings.xml files found in ${resourcePath}/values-*")

        // Map a list of directories ("values-xx", "values-yyrzz", ...) to a list of lists of
        // language codes (("xx"), ("yy", "zz"), ...).
        //
        // The suffixes on the `values-` directories are either:
        // - 2 letter ISO 639-1 code followed by
        // - (optional) "r" + 2 letter ISO 3166-1-alpha-2 region code, so "en", "fr", "en-rUS", etc
        //
        // Or:
        // - "b+" + 2 letter ISO 639-1 code
        // - (optional) additional subtags separated by "+", so "b+en", "b+en+US", "b+zh+Hant"
        //
        // TODO: This only handles the first format, not the "b+" format.
        val r = Regex("values-([a-zA-Z]+)(?:-r([a-zA-Z]+))?.*")
        val langCodes = resourceDirs
            .asSequence()
            .map { it.fileName }
            .map { r.find(it.toString()) }
            .filterNotNull()
            .map { it.groupValues.subList(1, it.groupValues.size).filterNot { it.isEmpty() }}
            .toMutableList()

        // "values" directory has been skipped, explicitly add its language code
        langCodes.add(listOf("en"))
        if (verbose) println("langCodes: $langCodes")

        // Construct the locales
        val locales = langCodes.map { if (it.size == 1) ULocale(it[0]) else ULocale(it[0], it[1])}

        // Construct the languages. Sort each locale by its display name, as rendered in that
        // locale, and fold case.
        val collator = Collator.getInstance(ULocale.ENGLISH)
        val casemapFold = CaseMap.fold()

        val languages = locales.sortedBy { collator.getCollationKey(casemapFold.apply(it.getDisplayName(it))) }
            .map { Language.from(it) }
            .toMutableList()

        // The first language in the list is the system default
        languages.add(0, Language("default", "@string/system_default", "System default"))

        // Copy donottranslate.xml line by line to a new file, replacing the contents of the
        // `language_entries` and `language_values` arrays with fresh data.
        val tmpFile = createTempFile().toFile()
        val w = tmpFile.printWriter()
        val donottranslate_xml = resourcePath / "values/donottranslate.xml"
        donottranslate_xml.toFile().useLines { lines ->
            var inLanguageEntries = false
            var inLanguageValues = false

            for (line in lines) {
                // Default behaviour, copy the line unless inside one of the arrays
                if (!inLanguageEntries && !inLanguageValues) {
                    w.println(line)
                }

                // Started the `language_entries` array
                if (line.contains("<string-array name=\"language_entries\">")) {
                    inLanguageEntries = true
                    continue
                }

                // Started the `language_values` array
                if (line.contains("<string-array name=\"language_values\">")) {
                    inLanguageValues = true
                    continue
                }

                // At the end of `language_entries`? Emit each language, one per line. The
                // item is the language's name, then a comment with the code and English name.
                // Then close the array.
                if (inLanguageEntries && line.contains("</string-array>")) {
                    languages.forEach {
                        w.println("        <item>${it.displayName}</item> <!-- ${it.code}: ${it.displayNameEnglish} -->")
                    }
                    w.println(line)
                    inLanguageEntries = false
                    continue
                }

                // At the end of `language_values`? Emit each language code, one per line.
                // Then close the array.
                if (inLanguageValues && line.contains("</string-array>")) {
                    languages.forEach { w.println("        <item>${it.code}</item>") }
                    w.println(line)
                    inLanguageValues = false
                    continue
                }
            }
        }

        // Close, then replace donotranslate.xml
        w.close()
        Files.move(tmpFile.toPath(), donottranslate_xml, StandardCopyOption.REPLACE_EXISTING)
        if (verbose) println("replaced ${donottranslate_xml.fileName}")
    }
}

fun main(args: Array<String>) = App().main(args)
