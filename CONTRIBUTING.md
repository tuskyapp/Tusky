# Contributing

Thanks for your interest in contributing to Tusky! Here are some informations to help you get started.

If you have any questions, don't hesitate to open an issue or join our [development chat on Matrix](https://riot.im/app/#/room/#Tusky:matrix.org).

## Contributing translations

Translations are managed on our [Weblate](https://weblate.tusky.app/projects/tusky/tusky/). You can create an account and translate texts through the interface, no coding knowledge required.
To add a new language, click on the 'Start a new translation' button on at the bottom of the page.

- Use gender-neutral language
- Address users informally (e.g. in German "du" and never "Sie")

## Contributing code

### Prerequisites
You should have a general understanding of Android development and Git. 

### Architecture
We try to follow the [Guide to app architecture](https://developer.android.com/topic/architecture).

### Kotlin
Tusky was originally written in Java, but is in the process of migrating to Kotlin. All new code must be written in Kotlin.
We try to follow the [Kotlin Style Guide](https://developer.android.com/kotlin/style-guide) and make format the code according to the default [ktlint codestyle](https://github.com/pinterest/ktlint).
You can check the codestyle by running `./gradlew ktlintCheck`.

### Text
All English text that will be visible to users must be put in `app/src/main/res/values/strings.xml` so it is translateable into other languages.
Try to keep texts friendly and concise.
If there is untranslatable text that you don't want to keep as a string constant in Kotlin code, you can use the string resource file `app/src/main/res/values/donottranslate.xml`.

### Viewbinding
We use [Viewbinding](https://developer.android.com/topic/libraries/view-binding) to reference views. No contribution using another mechanism will be accepted.
There are useful extensions in `src/main/java/com/keylesspalace/tusky/util/ViewExtensions.kt` that make working with viewbinding easier.

### Visuals
There are three themes in the app, so any visual changes should be checked with each of them to ensure they look appropriate no matter which theme is selected. Usually, you can use existing color attributes like `?attr/colorPrimary` and `?attr/textColorSecondary`.
All icons are from the Material iconset, find new icons [here](https://fonts.google.com/icons) (Google fonts) or [here](https://fonts.google.com/icons) (community contributions).

### Accessibility
We try to make Tusky as accessible as possible for as many people as possible. Please make sure that all touch targets are at least 48dpx48dp in size, Text has sufficient contrast and images or icons have a image description. See [this guide](https://developer.android.com/guide/topics/ui/accessibility/apps) for more information.

### Supported servers
Tusky is primarily a Mastodon client and aims to always support the newest Mastodon version. Other platforms implementing the Mastodon Api, e.g. Akkoma, GoToSocial or Pixelfed should also work with Tusky but no special effort is made to support their quirks or additional features.

## Troubleshooting / FAQ

- Tusky should be built with the newest version of Android Studio
- Tusky comes with two sets of build variants, "blue" and "green", which can be installed simultaneously and are distinguished by the colors of their icons. Green is intended for local development and testing, whereas blue is for releases.

## Resources
- [Mastodon Api documentation](https://docs.joinmastodon.org/api/)
