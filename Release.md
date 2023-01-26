# Releasing Tusky

Before each major release, make a beta for at least a week to make sure the release is well tested before being released for everybody. Minor releases can skip beta.

This approach of having ~500 user on the nightly releases and ~5000 users on the beta releases has so far worked very well and helped to fix bugs before they could reach most users.

## Beta

- Make sure all new features are well tested by Nightly users and all issues addressed as good as possible. Check GitHub issues, Google Play crash reports, messages on `@Tusky@mastodon.social`, emails on `tusky@connyduck.at`, #Tusky hashtag.
- Merge the latest Weblate translations (Weblate -> Repository maintenance -> commit all changes, then merge the automatic PRs by @nailyk-weblate on GitHub) 
- Check all the translations (Android Studio shows warnings on problems). Sometimes translators add faulty translations that would crash Tusky in this language, e.g. wrong number of formatting parameters. In this case it is usually easiest to just delete the string. [Example cleanup](https://github.com/tuskyapp/Tusky/commit/feaea70af418c77178985144a2d01a8e97725dfd).
- Update `versionCode` and `versionName` in `app/build.gradle`
- Add a new short changelog under `fastlane/metadata/android/en-US/changelogs`. Use the next versionCode as the filename. This is so translators on Weblate have the duration of the beta to translate the changelog and F-Droid users will see it in their language on the release. If another beta is released, the changelogs have to be renamed. Note that changelogs shouldn't be over 500 characters or F-Droid will truncate them.
- Build the app as apk and as app bundle.
- Do a quick check to make sure the build doesn't crash. Also install it over the last release to make sure the database migrations are correct.
- Merge `develop` into `main`
- Create a new [GitHub release](https://github.com/tuskyapp/Tusky/releases).
  - Tag the head of `main`.
  - Create an exhaustive changelog by going through all commits since the last release.
  - Attach the apk, adb and mapping.txt files to the release
  - Mark the release as being a pre-release.
- Create a merge request at F-Droid. [Example](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/11218) (F-Droid automatically picks up new release tags, but not beta ones. This could probably be changed somehow.)
- Upload the release to the Open Testing track on Google Play.
- Announce the release

## Full release

- Make sure all new features are well tested by beta users and all issues addressed as good as possible. Check GitHub issues, Google Play crash reports, messages on `@Tusky@mastodon.social`, #Tusky hashtag.
- Merge the latest Weblate translations (Weblate -> Repository maintenance -> commit all changes, then merge the automatic PRs by @nailyk-weblate on GitHub)
- Update `versionCode` and `versionName` in `app/build.gradle`
- Build the app as apk and as app bundle.
- Do a quick check to make sure the build doesn't crash. Also install it over the last release to make sure the database migrations are correct.
- Merge `develop` into `main`
- Create a new [GitHub release](https://github.com/tuskyapp/Tusky/releases).
  - Tag the head of `main`.
  - Resuse the changelog from the beta release, or create a new one if this is only a minor release.
  - Attach the apk, adb and mapping.txt files to the release
- (F-Droid will automatically detect and build the release)
- Upload the release to the Production track on Google Play.
- update the download link on the homepage ([repo](https://github.com/tuskyapp/tuskyapp.github.io))
- Announce the release

## Versioning

Since Tusky is user facing software that has no Api, we don't use semantic versioning. Tusky verion numbers only consist of two numbers major.minor with optional commit hash (nightly/test releases) or beta flag (beta releases).
- User visible changes in the release -> new major version
- Only bugfixes, new translations, refactorings or performance improvements in the release -> new minor version
