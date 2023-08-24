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

application {
    mainClass.set("app.tusky.mklanguages.MainKt")
}

dependencies {
    // ICU
    implementation("com.ibm.icu:icu4j:73.1")

    // Parsing
    implementation("com.github.h0tk3y.betterParse:better-parse:0.4.4")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:4.0.0-beta-28")
    implementation("ch.qos.logback:logback-classic:1.3.0")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.2") // for parameterized tests
}

tasks.test {
    useJUnitPlatform()
}
