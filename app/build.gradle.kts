import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import java.io.ByteArrayOutputStream
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.parcelize)
}

val gitSha = providers.of(GitShaValueSource::class) {}.get()

// The app name
val APP_NAME = "Tusky"
// The application id. Must be unique, e.g. based on your domain
val APP_ID = "com.keylesspalace.tusky"
// url of a custom app logo. Recommended size at least 600x600. Keep empty to use the Tusky elephant friend.
val CUSTOM_LOGO_URL = ""
// e.g. mastodon.social. Keep empty to not suggest any instance on the signup screen
val CUSTOM_INSTANCE = ""
// link to your support account. Will be linked on the about page when not empty.
val SUPPORT_ACCOUNT_URL = "https://mastodon.social/@Tusky"

android {
    compileSdk = 33
    namespace = "com.keylesspalace.tusky"
    defaultConfig {
        applicationId = APP_ID
        namespace = "com.keylesspalace.tusky"
        minSdk = 23
        targetSdk = 33
        versionCode = 100
        versionName = "21.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        resValue("string", "app_name", APP_NAME)

        buildConfigField("String", "CUSTOM_LOGO_URL", "\"$CUSTOM_LOGO_URL\"")
        buildConfigField("String", "CUSTOM_INSTANCE", "\"$CUSTOM_INSTANCE\"")
        buildConfigField("String", "SUPPORT_ACCOUNT_URL", "\"$SUPPORT_ACCOUNT_URL\"")
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
        }
    }

    flavorDimensions += "color"
    productFlavors {
        create("blue")
        create("green") {
            resValue("string", "app_name", "$APP_NAME Test")
            applicationIdSuffix = ".test"
            versionNameSuffix = "-$gitSha"
        }
    }

    lint {
        disable += "MissingTranslation"
    }
    buildFeatures {
        buildConfig = true
        resValues = true
        viewBinding = true
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
        unitTests.all {
            it.systemProperty("robolectric.logging.enabled", "true")
        }
    }
    sourceSets {
        getByName("androidTest").assets.srcDirs(files("$projectDir/schemas"))
    }

    // Exclude unneeded files added by libraries
    packagingOptions.resources.excludes += setOf(
        "LICENSE_OFL",
        "LICENSE_UNICODE",
    )

    bundle {
        language {
            // bundle all languages in every apk so the dynamic language switching works
            enableSplit = false
        }
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    // Can remove this once https://issuetracker.google.com/issues/260059413 is fixed.
    // https://kotlinlang.org/docs/gradle-configure-project.html#gradle-java-toolchains-support
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    applicationVariants.configureEach {
        outputs.configureEach {
            (this as? ApkVariantOutputImpl)?.outputFileName =
                "Tusky_${versionName}_${versionCode}" + "_${gitSha}_${flavorName}_${buildType.name}.apk"
        }
    }
}

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
}

// library versions are in PROJECT_ROOT/gradle/libs.versions.toml
dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.rx3)

    implementation(libs.bundles.androidx)
    implementation(libs.bundles.room)
    kapt(libs.androidx.room.compiler)

    implementation(libs.android.material)

    implementation(libs.gson)

    implementation(libs.bundles.retrofit)
    implementation(libs.networkresult.calladapter)

    implementation(libs.bundles.okhttp)

    implementation(libs.conscrypt.android)

    implementation(libs.bundles.glide)
    kapt(libs.glide.compiler)

    implementation(libs.bundles.rxjava3)

    implementation(libs.bundles.autodispose)

    implementation(libs.bundles.dagger)
    kapt(libs.bundles.dagger.processors)

    implementation(libs.sparkbutton)

    implementation(libs.photoview)

    implementation(libs.bundles.material.drawer)
    implementation(libs.material.typeface)

    implementation(libs.image.cropper)

    implementation(libs.bundles.filemojicompat)

    implementation(libs.bouncycastle)
    implementation(libs.unified.push)

    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.bundles.mockito)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.androidx.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.work.testing)

    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.junit)
}

// Must wrap this in a ValueSource in order to get well-defined fail behavior without confusing Gradle on repeat builds.
abstract class GitShaValueSource : ValueSource<String, ValueSourceParameters.None> {
    @Inject abstract fun getExecOperations(): ExecOperations

    override fun obtain(): String {
        try {
            val output = ByteArrayOutputStream()

            getExecOperations().exec {
                commandLine("git", "rev-parse", "--short=8", "HEAD")
                standardOutput = output
            }
            return output.toString().trim()
        } catch (ignore: GradleException) {
            // Git executable unavailable, or we are not building in a git repo. Fall through:
        }
        return "unknown"
    }
}
