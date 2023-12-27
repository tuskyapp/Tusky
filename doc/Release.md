# Releasing Tusky

Before each major release, make a beta for at least a week to make sure the release is well tested before being released for everybody. Minor releases can skip beta.

This approach of having ~500 user on the nightly releases and ~5000 users on the beta releases has so far worked very well and helped to fix bugs before they could reach most users.

## Beta

- Make sure all new features are well tested by Nightly users and all issues addressed as good as possible. Check GitHub issues, Google Play crash reports, messages on `@Tusky@mastodon.social`, emails on `tusky@connyduck.at`, #Tusky hashtag.
- Merge the latest Weblate translations (Weblate -> Repository maintenance -> commit all changes, then merge the automatic PRs by @nailyk-weblate on GitHub)
- Update `versionCode` and `versionName` in `app/build.gradle`
- Add a new short changelog under `fastlane/metadata/android/en-US/changelogs`. Use the next versionCode as the filename. This is so translators on Weblate have the duration of the beta to translate the changelog and F-Droid users will see it in their language on the release. If another beta is released, the changelogs have to be renamed. Note that changelogs shouldn't be over 500 characters or F-Droid will truncate them.
- Merge `develop` into `main`
- Create a new [GitHub release](https://github.com/tuskyapp/Tusky/releases).
  - Tag the head of `main`.
  - Create an exhaustive changelog by going through all commits since the last release.
  - Mark the release as being a pre-release.
- Bitrise will automatically build and upload the release to the Internal Testing track on Google Play.
- Do a quick check to make sure the build doesn't crash, e.g. by enrolling yourself into the test track.
    - In case there are any problems, delete the GitHub release, fix the problems and start again
- Download the build as apk from Google Play (App Bundle Explorer -> chose the release -> Downloads -> Signed, universal APK). Attach it to the GitHub Release.
- Create a new Open Testing release on Google Play. Reuse the build from the Internal Testing track.
- Create a merge request at F-Droid. [Example](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/11218) (F-Droid automatically picks up new release tags, but not beta ones. This could probably be changed somehow.)
- Announce the release

## Full release

- Make sure all new features are well tested by beta users and all issues addressed as good as possible. Check GitHub issues, Google Play crash reports, messages on `@Tusky@mastodon.social`, #Tusky hashtag.
- Merge the latest Weblate translations (Weblate -> Repository maintenance -> commit all changes, then merge the automatic PRs by @nailyk-weblate on GitHub)
- Update `versionCode` and `versionName` in `app/build.gradle`
- Merge `develop` into `main`
- Create a new [GitHub release](https://github.com/tuskyapp/Tusky/releases).
  - Tag the head of `main`.
  - Reuse the changelog from the beta release, or create a new one if this is only a minor release.
- (F-Droid will automatically detect and build the release)
- Bitrise will automatically build and upload the release to the Internal Testing track on Google Play.
- Do a quick check to make sure the build doesn't crash, e.g. by enrolling yourself into the test track.
    - In case there are any problems, delete the GitHub release, fix the problems and start again
- Download the build as apk from Google Play (App Bundle Explorer -> choose the release -> Downloads -> Signed, universal APK). Attach it to the GitHub Release.
- Create a new full release on Google Play. Reuse the build from the Internal Testing track.
- Update the download link on the homepage ([repo](https://github.com/tuskyapp/tuskyapp.github.io))
- Announce the release

## Versioning

Since Tusky is user facing software that has no Api, we don't use semantic versioning. Tusky version numbers only consist of two numbers major.minor with optional commit hash (nightly/test releases) or beta flag (beta releases).
- User visible changes in the release -> new major version
- Only bugfixes, new translations, refactorings or performance improvements in the release -> new minor version
