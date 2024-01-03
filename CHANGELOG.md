# Tusky changelog

## Unreleased or Tusky Nightly

### New features and other improvements

### Significant bug fixes

## v24.1

- The screen will stay on again while a video is playing. [PR#4168](https://github.com/tuskyapp/Tusky/pull/4168)
- A memory leak has been fixed. This should improve stability and performance. [PR#4150](https://github.com/tuskyapp/Tusky/pull/4150) [PR#4153](https://github.com/tuskyapp/Tusky/pull/4153)
- Emojis are now correctly counted as 1 character when composing a post. [PR#4152](hhttps://github.com/tuskyapp/Tusky/pull/4152)
- Fixed a crash when text was selected on some devices. [PR#4166](https://github.com/tuskyapp/Tusky/pull/4166)
- The icons in the help texts of empty timelines will now always be correctly
  aligned. [PR#4179](https://github.com/tuskyapp/Tusky/pull/4179)
- Fixed ANR caused by direct message badge [PR#4182](https://github.com/tuskyapp/Tusky/pull/4182)

## v24.0

### New features and other improvements

- The number of tabs that can be configured is no longer limited. [PR#4058](https://github.com/tuskyapp/Tusky/pull/4058)
- Blockquotes and code blocks in posts now look nicer [PR#4090](https://github.com/tuskyapp/Tusky/pull/4090) [PR#4091](https://github.com/tuskyapp/Tusky/pull/4091)
- The old behavior of the notification tab (pre Tusky 22.0) has been restored. [PR#4015](https://github.com/tuskyapp/Tusky/pull/4015)
- Role badges are now shown on profiles (Mastodon 4.2 feature). [PR#4029](https://github.com/tuskyapp/Tusky/pull/4029)
- The video player has been upgraded to Google Jetpack Media3; video compatibility should be improved, and you can now adjust playback speed. [PR#3857](https://github.com/tuskyapp/Tusky/pull/3857)
- New theme option to use the black theme when following the system design. [PR#3957](https://github.com/tuskyapp/Tusky/pull/3957)
- Following the system design is now the default theme setting. [PR#3813](https://github.com/tuskyapp/Tusky/pull/3957)
- A new view to see trending posts is available both in the menu and as custom tab. [PR#4007](https://github.com/tuskyapp/Tusky/pull/4007)
- A new option to hide self boosts has been added. [PR#4101](https://github.com/tuskyapp/Tusky/pull/4101)
- The `api/v2/instance` endpoint is now supported. [PR#4062](https://github.com/tuskyapp/Tusky/pull/4062)
- New settings for lists:
    - Hide from the home timeline [PR#3932](https://github.com/tuskyapp/Tusky/pull/3932)
    - Decide which replies should be shown in the list [PR#4072](https://github.com/tuskyapp/Tusky/pull/4072)
- The oldest supported Android version is now Android 7 Nougat [PR#4014](https://github.com/tuskyapp/Tusky/pull/4014)

### Significant bug fixes

- **Empty trends no longer causes Tusky to crash**, [PR#3853](https://github.com/tuskyapp/Tusky/pull/3853)


## v23.0

### New features and other improvements

- **New preference to scale UI text**, [PR#3248](https://github.com/tuskyapp/Tusky/pull/3248) by [@nikclayton](https://mastodon.social/@nikclayton)

### Significant bug fixes

- **Save account information correctly**, [PR#3720](https://github.com/tuskyapp/Tusky/pull/3720) by [@connyduck](https://chaos.social/@ConnyDuck)
  - If you were logged in with multiple accounts it was possible to switch accounts in a way that the UI showed the new account, but database operations were happening using the old account.
- **"pull" notifications on devices running Android versions <= 11**, [PR#3649](https://github.com/tuskyapp/Tusky/pull/3649) by [@nikclayton](https://mastodon.social/@nikclayton)
  - Pull notifications (i.e., not using ntfy.sh) could silently fail on devices running Android 11 and below
- **Work around Android bug where text fields could "forget" they can copy/paste**, [PR#3707](https://github.com/tuskyapp/Tusky/pull/3707) by [@nikclayton](https://mastodon.social/@nikclayton)
- **Viewing "diffs" in edit history will not extend off screen edge**, [PR#3431](https://github.com/tuskyapp/Tusky/pull/3431) by [@nikclayton](https://mastodon.social/@nikclayton)
- **Don't crash if your server has no post edit history**, [PR#3747](https://github.com/tuskyapp/Tusky/pull/3747) by [@nikclayton](https://mastodon.social/@nikclayton)
  - Your Mastodon server might know that a post has been edited, but not know the details of those edits. Trying to view the history of those statuses no longer crashes.
- **Add a "Delete" button when editing a filter**, [PR#3553](https://github.com/tuskyapp/Tusky/pull/3553) by [@Tak](https://mastodon.gamedev.place/@Tak)
- **Show non-square emoji correctly**, [PR#3711](https://github.com/tuskyapp/Tusky/pull/3711) by [@connyduck](https://chaos.social/@ConnyDuck)
- **Potential crash when editing profile fields**, [PR#3808](https://github.com/tuskyapp/Tusky/pull/3808) by [@nikclayton](https://mastodon.social/@nikclayton)
- **Oversized context menu when editing image descriptions**, [PR#3787](https://github.com/tuskyapp/Tusky/pull/3787) by [@connyduck](https://chaos.social/@ConnyDuck)

## v23.0 beta 2

### Significant bug fixes

- **Potential crash when editing profile fields**, [PR#3808](https://github.com/tuskyapp/Tusky/pull/3808) by [@nikclayton](https://mastodon.social/@nikclayton)
- **Oversized context menu when editing image descriptions**, [PR#3787](https://github.com/tuskyapp/Tusky/pull/3787) by [@connyduck](https://chaos.social/@ConnyDuck)

## v23.0 beta 1

### New features and other improvements

- **New preference to scale UI text**, [PR#3248](https://github.com/tuskyapp/Tusky/pull/3248) by [@nikclayton](https://mastodon.social/@nikclayton)

### Significant bug fixes

- **Save account information correctly**, [PR#3720](https://github.com/tuskyapp/Tusky/pull/3720) by [@connyduck](https://chaos.social/@ConnyDuck)
  - If you were logged in with multiple accounts it was possible to switch accounts in a way that the UI showed the new account, but database operations were happening using the old account.
- **"pull" notifications on devices running Android versions <= 11**, [PR#3649](https://github.com/tuskyapp/Tusky/pull/3649) by [@nikclayton](https://mastodon.social/@nikclayton)
  - Pull notifications (i.e., not using ntfy.sh) could silently fail on devices running Android 11 and below
- **Work around Android bug where text fields could "forget" they can copy/paste**, [PR#3707](https://github.com/tuskyapp/Tusky/pull/3707) by [@nikclayton](https://mastodon.social/@nikclayton)
- **Viewing "diffs" in edit history will not extend off screen edge**, [PR#3431](https://github.com/tuskyapp/Tusky/pull/3431) by [@nikclayton](https://mastodon.social/@nikclayton)
- **Don't crash if your server has no post edit history**, [PR#3747](https://github.com/tuskyapp/Tusky/pull/3747) by [@nikclayton](https://mastodon.social/@nikclayton)
  - Your Mastodon server might know that a post has been edited, but not know the details of those edits. Trying to view the history of those statuses no longer crashes.
- **Add a "Delete" button when editing a filter**, [PR#3553](https://github.com/tuskyapp/Tusky/pull/3553) by [@Tak](https://mastodon.gamedev.place/@Tak)
- **Show non-square emoji correctly**, [PR#3711](https://github.com/tuskyapp/Tusky/pull/3711) by [@connyduck](https://chaos.social/@ConnyDuck)

## v22.0

### New features and other improvements

- **View trending hashtags**, [PR#3149](https://github.com/tuskyapp/Tusky/pull/3149) by [@knossos](https://fosstodon.org/@knossos)
  - View trending hashtags from the side menu, or by adding them to a new tab.
- **Edit image description and focus point**, [PR#3215](https://github.com/tuskyapp/Tusky/pull/3215) by [@Tak](https://mastodon.gamedev.place/@Tak)
  - Edit image descriptions and focus points when editing posts.
- **View profile banner images**, [PR#3274](https://github.com/tuskyapp/Tusky/pull/3274) by [@Tak](https://mastodon.gamedev.place/@Tak)
  - Tap the banner image on any profile to view it full size, save, share, etc.
- **Follow new hashtags**, [PR#3275](https://github.com/tuskyapp/Tusky/pull/3275) by [@nikclayton](https://mastodon.social/@nikclayton)
  - Follow new hashtags from the "Followed hashtags" screen.
- **Better ordering when selecting languages**, [PR#3293](https://github.com/tuskyapp/Tusky/pull/3293) by [@Tak](https://mastodon.gamedev.place/@Tak)
  - Tusky will prioritise the language of the post being replied to, your default posting language, configured Tusky languages, and configured system languages when ordering the list of languages to post in.
- **"Load more" break is more prominent**, [PR#3376](https://github.com/tuskyapp/Tusky/pull/3376) by [@lakoja](https://freiburg.social/@lakoja)
  - Adjusted the design so the "Load more" break in a timeline is more obvious.
- **Add "Refresh" menu**, [PR#3121](https://github.com/tuskyapp/Tusky/pull/3121) by [@nikclayton](https://mastodon.social/@nikclayton)
  - Tusky timelines can now be refreshed from a menu as well as swiping, making this accessible to assistive devices.
- **Notifications timeline improvements**, [PR#3159](https://github.com/tuskyapp/Tusky/pull/3159) by [@nikclayton](https://mastodon.social/@nikclayton)
  - Notifications no longer need to "Load more", they are loaded automatically as you scroll.
  - Errors when interacting with notifications are displayed to the user, with a "Retry" option.
- **Show the difference between versions of a post**, [PR#3314](https://github.com/tuskyapp/Tusky/pull/3314) by [@nikclayton](https://mastodon.social/@nikclayton)
  - Viewing the edits to a post highlights the differences (text that was added or deleted) between the different versions.
- **Support Mastodon v4 filters**, [PR#3188](https://github.com/tuskyapp/Tusky/pull/3188) by [@Tak](https://mastodon.gamedev.place/@Tak)
  - Mastodon v4 introduced additional [filtering controls](https://docs.joinmastodon.org/user/moderating/#filters).
- **Option to show post statistics in the timeline**, [PR#3413](https://github.com/tuskyapp/Tusky/pull/3413)
  - Tusky can now (optionally) show the number of replies, reposts, and favourites a post has received, in the timeline.
- **Expanded tappable area for links, hashtags, and mentions in a post**, [PR#3382](https://github.com/tuskyapp/Tusky/pull/3382) by [@nikclayton](https://mastodon.social/@nikclayton)
  - Links, hashtags, and mentions in a post now react to taps that are a little above, below, or to the side of the tappable text, making them more accessible.

### Significant bug fixes

- **Remember selected tab and position**, [PR#3255](https://github.com/tuskyapp/Tusky/pull/3255) by [@nikclayton](https://mastodon.social/@nikclayton)
  - Changing your tab settings (adding, removing, re-ordering) remembers your reading position in those tabs.
- **Show player controls during audio playback**, [PR#3286](https://github.com/tuskyapp/Tusky/pull/3286) by [@EricFrohnhoefer](https://mastodon.social/@EricFrohnhoefer)
  - A regression from v21.0 where the media player controls could not be used.
- **Keep notifications until read**, [PR#3312](https://github.com/tuskyapp/Tusky/pull/3312) by [@lakoja](https://freiburg.social/@lakoja)
  - Opening Tusky would dismiss all active Tusky Android notifications.
- **Fix copying URLs at the end of a post**, [PR#3380](https://github.com/tuskyapp/Tusky/pull/3380) by [@nikclayton](https://mastodon.social/@nikclayton)
  - Copying a URL from the end of a post could include an extra Unicode whitespace character, making the URL unusable as is.
- **Correctly display mixed RTL and LTR text in profiles**, [PR#3328](https://github.com/tuskyapp/Tusky/pull/3328) by [@nikclayton](https://mastodon.social/@nikclayton)
  - Profile text that contained a mix of right-to-left and left-to-right writing directions would display incorrectly.
- **Stop showing duplicates of edited posts in threads**, [PR#3377](https://github.com/tuskyapp/Tusky/pull/3377) by [@Tak](https://mastodon.gamedev.place/@Tak)
  - Editing a post in thread view would show the old and new version of the post in the thread.
- **Correct post length calculation**, [PR#3392](https://github.com/tuskyapp/Tusky/pull/3392) by [@nikclayton](https://mastodon.social/@nikclayton)
  - In a post that mentioned a user (e.g., `@tusky@mastodon.social`) Tusky was incorrectly including the `@mastodon.social` part when calculating the post's length, leading to incorrect "This post is too long" errors.
- **Always publish image captions**, [PR#3421](https://github.com/tuskyapp/Tusky/pull/3421) by [@lakoja](https://freiburg.social/@lakoja)
  - Finishing editing an image caption before the image had finished loading would lose the caption.
- **Clicking "Compose" from a notification would set the wrong account**, [PR#3688](https://github.com/tuskyapp/Tusky/pull/3688)

## v22.0 beta 7

### Significant bug fixes

- **Fetch all outstanding Mastodon notifications when creating Android notifications**, [PR#3700](https://github.com/tuskyapp/Tusky/pull/3700)
- **Clicking "Compose" from a notification would set the wrong account**, [PR#3688](https://github.com/tuskyapp/Tusky/pull/3688)
- **Ensure "last read notification ID" is saved to the correct account**, [PR#3697](https://github.com/tuskyapp/Tusky/pull/3697)

## v22.0 beta 6

### Significant bug fixes

- **Save reading position in the Notifications tab more frequently**, [PR#3685](https://github.com/tuskyapp/Tusky/pull/3685)

## v22.0 beta 5

## Significant bug fixes

- **Rolled back APNG library to fix broken animated emojis**, [PR#3676](https://github.com/tuskyapp/Tusky/pull/3676)
- **Save local copy of notification marker in case server does not support the API**, [PR#3672](https://github.com/tuskyapp/Tusky/pull/3672)

## v22.0 beta 4

### Significant bug fixes

- **Fixed repeated fetch of notifications if configured with multiple accounts**, [PR#3660](https://github.com/tuskyapp/Tusky/pull/3660)

## v22.0 beta 3

### Significant bug fixes

- **Fixed crash when viewing a thread**, [PR#3622](https://github.com/tuskyapp/Tusky/pull/3622)
- **Fixed crash processing Mastodon filters**, [PR#3634](https://github.com/tuskyapp/Tusky/pull/3634)
- **Links in bios of follow/follow request notifications are clickable**, [PR#3646](https://github.com/tuskyapp/Tusky/pull/3646)
- **Android Notifications updates**, [PR#3636](https://github.com/tuskyapp/Tusky/pull/3626)
  - Android notification for a Mastodon notification should only be shown once
  - Android notifications are grouped by Mastodon notification type (follow, mention, boost, etc)
  - Potential for missing notifications has been removed

## v22.0 beta 2

### Significant bug fixes

- **Improved notification loading speed**, [PR#3598](https://github.com/tuskyapp/Tusky/pull/3598)
- **Restore showing 0/1/1+ for replies**, [PR#3590](https://github.com/tuskyapp/Tusky/pull/3590)
- **Show filter titles, not filter keywords, on filtered posts**, [PR#3589](https://github.com/tuskyapp/Tusky/pull/3589)
- **Fixed a bug where opening a status could open an unrelated link**, [PR#3600](https://github.com/tuskyapp/Tusky/pull/3600)
- **Show "Add" button in correct place when there are no filters**, [PR#3561](https://github.com/tuskyapp/Tusky/pull/3561)
- **Fixed assorted crashes**

## v22.0 beta 1

### New features and other improvements

- **View trending hashtags**, [PR#3149](https://github.com/tuskyapp/Tusky/pull/3149) by [@knossos](https://fosstodon.org/@knossos)
  - View trending hashtags from the side menu, or by adding them to a new tab.
- **Edit image description and focus point**, [PR#3215](https://github.com/tuskyapp/Tusky/pull/3215) by [@Tak](https://mastodon.gamedev.place/@Tak)
  - Edit image descriptions and focus points when editing posts.
- **View profile banner images**, [PR#3274](https://github.com/tuskyapp/Tusky/pull/3274) by [@Tak](https://mastodon.gamedev.place/@Tak)
  - Tap the banner image on any profile to view it full size, save, share, etc.
- **Follow new hashtags**, [PR#3275](https://github.com/tuskyapp/Tusky/pull/3275) by [@nikclayton](https://mastodon.social/@nikclayton)
  - Follow new hashtags from the "Followed hashtags" screen.
- **Better ordering when selecting languages**, [PR#3293](https://github.com/tuskyapp/Tusky/pull/3293) by [@Tak](https://mastodon.gamedev.place/@Tak)
  - Tusky will prioritise the language of the post being replied to, your default posting language, configured Tusky languages, and configured system languages when ordering the list of languages to post in.
- **"Load more" break is more prominent**, [PR#3376](https://github.com/tuskyapp/Tusky/pull/3376) by [@lakoja](https://freiburg.social/@lakoja)
  - Adjusted the design so the "Load more" break in a timeline is more obvious.
- **Add "Refresh" menu**, [PR#3121](https://github.com/tuskyapp/Tusky/pull/3121) by [@nikclayton](https://mastodon.social/@nikclayton)
  - Tusky timelines can now be refreshed from a menu as well as swiping, making this accessible to assistive devices.
- **Notifications timeline improvements**, [PR#3159](https://github.com/tuskyapp/Tusky/pull/3159) by [@nikclayton](https://mastodon.social/@nikclayton)
  - Notifications no longer need to "Load more", they are loaded automatically as you scroll.
  - Errors when interacting with notifications are displayed to the user, with a "Retry" option.
- **Show the difference between versions of a post**, [PR#3314](https://github.com/tuskyapp/Tusky/pull/3314) by [@nikclayton](https://mastodon.social/@nikclayton)
  - Viewing the edits to a post highlights the differences (text that was added or deleted) between the different versions.
- **Support Mastodon v4 filters**, [PR#3188](https://github.com/tuskyapp/Tusky/pull/3188) by [@Tak](https://mastodon.gamedev.place/@Tak)
  - Mastodon v4 introduced additional [filtering controls](https://docs.joinmastodon.org/user/moderating/#filters).
- **Option to show post statistics in the timeline**, [PR#3413](https://github.com/tuskyapp/Tusky/pull/3413)
  - Tusky can now (optionally) show the number of replies, reposts, and favourites a post has received, in the timeline.
- **Expanded tappable area for links, hashtags, and mentions in a post**, [PR#3382](https://github.com/tuskyapp/Tusky/pull/3382) by [@nikclayton](https://mastodon.social/@nikclayton)
  - Links, hashtags, and mentions in a post now react to taps that are a little above, below, or to the side of the tappable text, making them more accessible.

### Significant bug fixes

- **Remember selected tab and position**, [PR#3255](https://github.com/tuskyapp/Tusky/pull/3255) by [@nikclayton](https://mastodon.social/@nikclayton)
  - Changing your tab settings (adding, removing, re-ordering) remembers your reading position in those tabs.
- **Show player controls during audio playback**, [PR#3286](https://github.com/tuskyapp/Tusky/pull/3286) by [@EricFrohnhoefer](https://mastodon.social/@EricFrohnhoefer)
  - A regression from v21.0 where the media player controls could not be used.
- **Keep notifications until read**, [PR#3312](https://github.com/tuskyapp/Tusky/pull/3312) by [@lakoja](https://freiburg.social/@lakoja)
  - Opening Tusky would dismiss all active Tusky Android notifications.
- **Fix copying URLs at the end of a post**, [PR#3380](https://github.com/tuskyapp/Tusky/pull/3380) by [@nikclayton](https://mastodon.social/@nikclayton)
  - Copying a URL from the end of a post could include an extra Unicode whitespace character, making the URL unusable as is.
- **Correctly display mixed RTL and LTR text in profiles**, [PR#3328](https://github.com/tuskyapp/Tusky/pull/3328) by [@nikclayton](https://mastodon.social/@nikclayton)
  - Profile text that contained a mix of right-to-left and left-to-right writing directions would display incorrectly.
- **Stop showing duplicates of edited posts in threads**, [PR#3377](https://github.com/tuskyapp/Tusky/pull/3377) by [@Tak](https://mastodon.gamedev.place/@Tak)
  - Editing a post in thread view would show the old and new version of the post in the thread.
- **Correct post length calculation**, [PR#3392](https://github.com/tuskyapp/Tusky/pull/3392) by [@nikclayton](https://mastodon.social/@nikclayton)
  - In a post that mentioned a user (e.g., `@tusky@mastodon.social`) Tusky was incorrectly including the `@mastodon.social` part when calculating the post's length, leading to incorrect "This post is too long" errors.
- **Always publish image captions**, [PR#3421](https://github.com/tuskyapp/Tusky/pull/3421) by [@lakoja](https://freiburg.social/@lakoja)
  - Finishing editing an image caption before the image had finished loading would lose the caption.
