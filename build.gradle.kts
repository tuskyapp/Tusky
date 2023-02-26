plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.ktlint) apply false
}

allprojects {
    plugins.apply(rootProject.libs.plugins.ktlint.get().pluginId)

    // Configure Java to use our chosen language level. Kotlin will automatically pick this up.
    // See https://kotlinlang.org/docs/gradle-configure-project.html#gradle-java-toolchains-support
    plugins.withType<JavaBasePlugin>().configureEach {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(11))
            }
        }
    }
}

tasks.register("clean") {
    delete(rootProject.buildDir)
}
