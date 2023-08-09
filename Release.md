# Releasing Tusky

Before each major release, make a beta for at least a week to make sure the release is well tested before being released for everybody. Minor releases can skip beta.

This approach of having ~500 user on the nightly releases and ~5000 users on the beta releases has so far worked very well and helped to fix bugs before they could reach most users.

## Identify two person team

One person to prepare the PRs for the release and run the relevant commands. One person to act as the reviewer.

## Check access permissions

- [ ] You can merge PRs
- [ ] You can access the [Tusky Google Play console](https://play.google.com/console/u/0/developers/8419715224772184120/app-list)
  - [ ] And see entries for Tusky and Tusky nightly
- [ ] You can post from the `@Tusky@mastodon.social` account
- [ ] A GitLab account, for editing F-Droid metadata
  - A forked, up to date copy of the F-Droid repo
  - TODO: Notes on doing that
- [ ] An OpenCollective account, for posting release notes

## Release each beta

### Check for error reports

- [ ] Make sure all new features are well tested by Nightly users and all issues addressed as well as possible. Check:
  - [ ] GitHub issues
  - [ ] Google Play crash reports
  - [ ] Messages on `@Tusky@mastodon.social`
  - [ ] E-mails on `tusky@connyduck.at`
  - [ ] `#Tusky` hashtag on Mastodon

### Update translations

- [ ] Merge the latest Weblate translations (Weblate -> Repository maintenance -> commit all changes
- [ ] Then merge the automatic PRs by @nailyk-weblate on GitHub
  - Check [Open translation PRs](https://github.com/tuskyapp/Tusky/pulls?q=is%3Apr+is%3Aopen+%22Translations+update+from+Weblate%22)

### Draft changelog

Find the changes between the last tagged release and `develop`.

For example, https://github.com/tuskyapp/Tusky/compare/v21.0...develop compares tag `v21.0` (the previous release) with the most recent commit on `develop`.

Update `CHANGELOG.md`. Include changes that are user-visible or of high user-importance.

That includes:

- A new feature
- A modification of an existing feature
- Fixing a user-visible bug
- Upgrading a dependency that has a security issue
- Upgrading the emoji dependency, if new emoji are supported
- Changing the minimum supported Android version
- Translations in to a new language
  - `git diff --name-only --diff-filter=A v21.0...develop` to find files added since that tag.

That does not include:

- General dependency upgrades
- Updates to existing translations

### Create a tracking issue

TODO: Document this.

### Bump version and write changelog

- [ ] Create and checkout a new branch (`3551-v22-beta-103`)
- [ ] Update `versionCode` and `versionName` in `app/build.gradle`
  - `versionCode` should be one higher than the current value
  - `versionName` should be `22.0 beta 1`
- [ ] Add a new short changelog under `fastlane/metadata/android/en-US/changelogs`. Use the next `versionCode` as the filename.

> Important! Changelog must be less than 500 characters or F-Droid will truncate it. So keep the English version to ~ 400 characters, so it is likely to work in languages that tend to have longer words.

This is so translators on Weblate have the duration of the beta to translate the changelog and F-Droid users will see it in their language on the release. If another beta is released, the changelogs have to be renamed.

- [ ] Commit (commit message: `Prepare 22.0 beta 1 (versionCode 103)`), push, create PR, assign to reviewer
- [ ] Wait for approval, merge to `develop`

### Merge changes from `develop` to `main`

Create a clone of the repo just for the release.

```shell
set JAVA_HOME="c:\Program Files\Android\Android Studio Electric Eel\jbr"
# or
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio Electric Eel\jbr"

git clone https://github.com/tuskyapp/Tusky.git tusky-release
cd tusky-release
-- copy local.properties file
git checkout main
```

- [ ] Merge `develop` into `main`

```shell
# Verify on the main branch
git branch --show-current           # Should show "main"

# Verify develop contains the expected commits
git fetch origin develop:develop    # Fetch newest commits
git log develop                     # Should show the "Prepare ..." commit first

# Merge develop in to main, as a fast-forward merge. If this fails something weird has
# happened in the history between main and develop
git merge --ff-only develop

# Verify the beta commit is now on main
git log

# Build to verify everything works
.\gradlew ktlintCheck lintGreenDebug testGreenDebugUnitTest bundleGreenDebug

# Tag
git tag -m v22.0-beta.4 -s v22.0-beta.4

# Push
git push
git push --tags
```

### Create GitHub release

- [ ] Create a new [GitHub release](https://github.com/tuskyapp/Tusky/releases).
  - [ ] Tag the head of `main`.
    - `git tag -s v22.0-beta.1 -m v22.0-beta.1`
    - `git push origin v22.0-beta.1`
  - [ ] Create the release https://github.com/tuskyapp/Tusky/releases/new
    - Select the tag you just created
    - Release title is "Tusky 22.0 beta 1"
    - Copy the changelog text from `CHANGELOG.md`, that you edited previously
  - [ ] Mark the release as being a pre-release.
  - [ ] Confirm that Bitrise has built and uploaded the release to the Internal Testing track on Google Play.
    - Bitrise console for Tusky is https://app.bitrise.io/app/a3e773c3c57a894c
  - From the Play console choose "Testing > Internal testing"
    - [ ] verify that the "Track summary" section at the top of the page lists the new release.
    - [ ] Do a quick check to make sure the build doesn't crash
      - Select the "Testers" tab, just below the track summary
      - Click the arrow on the "Internal testers" list
      - Add your e-mail address to the list
      - Accept the invitation, at https://play.google.com/apps/internaltest/4697784310821336813
      - In case there are any problems, delete the GitHub release, fix the problems and start again
  - [ ] Download the build as apk from Google Play (App Bundle Explorer -> chose the release -> Downloads -> Signed, universal APK). Attach it to the GitHub Release.
  - [ ] Create a new Open Testing release on Google Play. Reuse the build from the Internal Testing track.
    - Release > Open testing
    - "Create new release" button
    - In the "App bundles" section click "Add from library", chose the new release
    -
  - [ ] Create a merge request at F-Droid. [Example](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/11218) (F-Droid automatically picks up new release tags, but not beta ones. This could probably be changed somehow.)
    - Open https://gitlab.com/fdroid/fdroiddata
    - Click "Fork", top right, or just open https://gitlab.com/fdroid/fdroiddata/-/forks/new
    - Select a project namespace (under your account is fine)
    - Accept the remaining defaults
    - Click "Fork project"
    - Wait for the repository to import
    - Create a new branch, by opening https://gitlab.com/nikclayton/fdroiddata/-/branches/new
      - Branch name is the `versionCode` (e.g., `105`).
    - Edit the file `metadata/com.keylesspalace.tusky.yml` on the branch, and commit
    - Click "Create merge request"
  - [ ] Announce the release

## Full release

### Check for error reports

- [ ] Make sure all new features are well tested by beta users and all issues addressed as good as possible. Check:
  - [ ] GitHub issues
  - [ ] Google Play crash reports
  - [ ] Messages on `@Tusky@mastodon.social`
  - [ ] #Tusky hashtag

### Update translations

- [ ] Merge the latest Weblate translations (Weblate -> Repository maintenance -> commit all changes)
  - [ ] Then merge the automatic PRs by @nailyk-weblate on GitHub
    - Check [Open translation PRs](https://github.com/tuskyapp/Tusky/pulls?q=is%3Apr+is%3Aopen+%22Translations+update+from+Weblate%22)

### Bump version

- [ ] Update `versionCode` and `versionName` in `app/build.gradle`

### Merge and create GitHub release

- [ ] Merge `develop` into `main`
- [ ] Create a new [GitHub release](https://github.com/tuskyapp/Tusky/releases).
  - [ ] Tag the head of `main`.
  - [ ] Reuse the changelog from the beta release, or create a new one if this is only a minor release.
- (F-Droid will automatically detect and build the release)
- [ ] Bitrise will automatically build and upload the release to the Internal Testing track on Google Play.
- [ ] Do a quick check to make sure the build doesn't crash, e.g. by enrolling yourself into the test track.
  - In case there are any problems, delete the GitHub release, fix the problems and start again
- [ ] Download the build as apk from Google Play (App Bundle Explorer -> chose the release -> Downloads -> Signed, universal APK). Attach it to the GitHub Release.
- [ ] Create a new full release on Google Play. Reuse the build from the Internal Testing track.
- [ ] Update the download link on the homepage ([repo](https://github.com/tuskyapp/tuskyapp.github.io))
- [ ] Announce the release

## Versioning

Since Tusky is user facing software that has no Api, we don't use semantic versioning. Tusky version numbers only consist of two numbers major.minor with optional commit hash (nightly/test releases) or beta flag (beta releases).
- User visible changes in the release -> new major version
- Only bugfixes, new translations, refactorings or performance improvements in the release -> new minor version
