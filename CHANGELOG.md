# Tusky changelog

## Unreleased or Tusky Nightly

### New features and other improvements

### Significant bug fixes

## v27.1

### Significant bug fixes

- Improves rendering of some animated custom emojis https://github.com/tuskyapp/Tusky/pull/4281
- Fixes an issue where the input field for media descriptions was too small in some cases https://github.com/tuskyapp/Tusky/pull/4831
- Fixes an issue where hashtags at the end of posts were duplicated https://github.com/tuskyapp/Tusky/pull/4845

## v27.0

### New features and other improvements

- Tusky has been redesigned with Material 3 https://github.com/tuskyapp/Tusky/pull/4637 https://github.com/tuskyapp/Tusky/pull/4673
- Support for Notification Policies (Mastodon 4.3 feature) https://github.com/tuskyapp/Tusky/pull/4768
- Hashtags at the end of posts are now shown in a separate bar https://github.com/tuskyapp/Tusky/pull/4761
- Full support for folding devices https://github.com/tuskyapp/Tusky/pull/4689
- Improved post rendering in some edge cases https://github.com/tuskyapp/Tusky/pull/4650 https://github.com/tuskyapp/Tusky/pull/4672 https://github.com/tuskyapp/Tusky/pull/4723
- Descriptions can now be added to audio attachments https://github.com/tuskyapp/Tusky/pull/4711
- The screen keyboard now pops up automatically when opening a dialog that contains a textfield https://github.com/tuskyapp/Tusky/pull/4667

### Significant bug fixes

- fixes a bug where Tusky would drop your draft when switching apps https://github.com/tuskyapp/Tusky/pull/4685 https://github.com/tuskyapp/Tusky/pull/4813 https://github.com/tuskyapp/Tusky/pull/4818
- fixes a bug where Tusky would drop media that is being added to a post https://github.com/tuskyapp/Tusky/pull/4662
- fixes a bug that caused the login to fail in some cases https://github.com/tuskyapp/Tusky/pull/4704

## v26.2

### Significant bug fixes

- Fixes a bug where Tusky would not correctly switch between accounts https://github.com/tuskyapp/Tusky/pull/4636
- Fixes a crash when a status in a notification contains a reblog (happens when subscribed to a Friendica group) https://github.com/tuskyapp/Tusky/pull/4638
- Long video descriptions can no longer cover the video controls https://github.com/tuskyapp/Tusky/pull/4632
- Fixes a bug where Tusky's URL detection algorithm was different from Mastodon's https://github.com/tuskyapp/Tusky/pull/4642

## v26.1

### New features and other improvements

- The "Reply privacy" account preference now has two additional options: "Match default post privacy" and "Direct". "Match default post privacy" is the default for new accounts. https://github.com/tuskyapp/Tusky/pull/4568
- Tusky now includes ISRG root certificates to keep working on Android 7 and servers that use Let's Encrypt. https://github.com/tuskyapp/Tusky/pull/4609
- The soft keyboard will now be hidden after performing a search. https://github.com/tuskyapp/Tusky/pull/4578

### Significant bug fixes

- Fixes a bug where Tusky sometimes mixes up timelines and/or notifications of accounts. https://github.com/tuskyapp/Tusky/pull/4577 https://github.com/tuskyapp/Tusky/pull/4599
- Fixes two bugs where Tusky would not provide the translation option even though the server is configured correctly. https://github.com/tuskyapp/Tusky/pull/4560 https://github.com/tuskyapp/Tusky/pull/4590
- Fixes a rare bug where Tusky would sometimes randomly crash on startup. https://github.com/tuskyapp/Tusky/pull/4569
- Fixes a bug where the timeline would randomly jump to the position of the last clicked "show more" placeholder when "Reading order" was set to "Oldest first". https://github.com/tuskyapp/Tusky/pull/4619

## v26.0

### New features and other improvements

- The blue primary color that previously was the same for all themes is now slightly lighter in the dark theme and darker in the light theme for better contrast.
  Consequently, the color that is used on top of the primary color (e.g. on buttons) is now dark instead of white in the dark theme. [PR#3921](https://github.com/tuskyapp/Tusky/pull/3921) [PR#4507](https://github.com/tuskyapp/Tusky/pull/4507)
- New account preference "default reply privacy".
  Note that in contrast to the "default post privacy" this setting will not be synced with the server as Mastodon does not have this feature. [PR#4496](https://github.com/tuskyapp/Tusky/pull/4496)
- New preference "Show confirmation before following" [PR#4445](https://github.com/tuskyapp/Tusky/pull/4445)
- The notification tab is now cached on the device for better offline behavior.
  Since it shares the cache with the home timeline, interactions with posts will now sync between those tabs more often than before. [PR#4026](https://github.com/tuskyapp/Tusky/pull/4026)
- Tusky will now only make one call to the server to check which version of the filters api is supported and cache the result instead of everytime filters are needed. [PR#4539](https://github.com/tuskyapp/Tusky/pull/4539)
- The "Hide compose button while scrolling" preference, which had the main purpose of making content behind the button accessible, has been removed and bottom padding added to all lists that could be obscured by buttons. [PR#4486](https://github.com/tuskyapp/Tusky/pull/4486)
- When viewing media of a translated post the media descriptions will now also be translated [PR#4463](https://github.com/tuskyapp/Tusky/pull/4463)
- The custom emojis in the emoji picker are now sorted by category [PR#4533](https://github.com/tuskyapp/Tusky/pull/4533)
- Various internal refactorings to improve performance and maintainability.
  [PR#4515](https://github.com/tuskyapp/Tusky/pull/4515)
  [PR#4502](https://github.com/tuskyapp/Tusky/pull/4502)
  [PR#4472](https://github.com/tuskyapp/Tusky/pull/4472)
  [PR#4470](https://github.com/tuskyapp/Tusky/pull/4470)
  [PR#4443](https://github.com/tuskyapp/Tusky/pull/4443)
  [PR#4441](https://github.com/tuskyapp/Tusky/pull/4441)
  [PR#4461](https://github.com/tuskyapp/Tusky/pull/4461)
  [PR#4447](https://github.com/tuskyapp/Tusky/pull/4447)
  [PR#4411](https://github.com/tuskyapp/Tusky/pull/4411)
  [PR#4413](https://github.com/tuskyapp/Tusky/pull/4413)

### Significant bug fixes

- Posts with null media focus values will no longer cause Tusky to show an error [PR#4462](https://github.com/tuskyapp/Tusky/pull/4462)
- A lot of other bugfixes, mostly smaller display bugs
  [PR#4536](https://github.com/tuskyapp/Tusky/pull/4536)
  [PR#4537](https://github.com/tuskyapp/Tusky/pull/4537)
  [PR#4527](https://github.com/tuskyapp/Tusky/pull/4527)
  [PR#4521](https://github.com/tuskyapp/Tusky/pull/4521)
  [PR#4525](https://github.com/tuskyapp/Tusky/pull/4525)
  [PR#4518](https://github.com/tuskyapp/Tusky/pull/4518)
  [PR#4514](https://github.com/tuskyapp/Tusky/pull/4514)
  [PR#4491](https://github.com/tuskyapp/Tusky/pull/4491)
  [PR#4490](https://github.com/tuskyapp/Tusky/pull/4490)
  [PR#4474](https://github.com/tuskyapp/Tusky/pull/4474)
  [PR#4436](https://github.com/tuskyapp/Tusky/pull/4436)

## v25.2

### Significant bug fixes

- Fixes a bug that could sometimes crash Tusky when rotating the screen while viewing an account list [PR#4430](https://github.com/tuskyapp/Tusky/pull/4430)
- Fixes a bug that could crash Tusky at startup under certain conditions [PR#4431](https://github.com/tuskyapp/Tusky/pull/4431)
- Fixes a bug that caused Tusky to crash when custom emojis with too large dimensions were loaded [PR#4429](https://github.com/tuskyapp/Tusky/pull/4429)
- Makes Tusky work again with Iceshrimp by working around a quirk in their API implementation [PR#4426](https://github.com/tuskyapp/Tusky/pull/4426)
- Fixes a bug that made translations not work on some servers [PR#4422](https://github.com/tuskyapp/Tusky/pull/4422)

## v25.1

### Significant bug fixes

- Fixed two crashes at startup introduced in 25.0 [PR#4415](https://github.com/tuskyapp/Tusky/pull/4415) [PR#4417](https://github.com/tuskyapp/Tusky/pull/4417)

## v25.0

### New features and other improvements

- Added support for the [Mastodon translation api](https://docs.joinmastodon.org/methods/statuses/#translate).
  You can now find a new option "translate" in the three-dot-menu on posts that are not in your display language when your server supports the translation api.
  Support is determined by checking the `configuration.translation.enabled` attribute of the `/api/v2/instance` endpoint.
  [PR#4307](https://github.com/tuskyapp/Tusky/pull/4307)
- The language of a post is now shown in the metadata section of the detail post view, if it is available. [PR#4127](https://github.com/tuskyapp/Tusky/pull/4127)
- The transitions between screens have been changed to feel faster and align more with default Android transitions. [PR#4285](https://github.com/tuskyapp/Tusky/pull/4285)
- The post statistic section below the detail post view is now always shown to prevent layout shifts on the first like or boost.
  [PR#4205](https://github.com/tuskyapp/Tusky/pull/4205) [PR#4260](https://github.com/tuskyapp/Tusky/pull/4260)
- The filters for boosts/replies/self-boosts in the home timeline have moved from general preferences to account specific preferences. [PR#4115](https://github.com/tuskyapp/Tusky/pull/4115)
- The json parsing library has been migrated from Gson to Moshi. This change will make Tusky no longer crash on unexpected server responses. [PR#4309](https://github.com/tuskyapp/Tusky/pull/4309)
- Small layout improvements to the header of the profile view [PR#4375](https://github.com/tuskyapp/Tusky/pull/4375) [PR#4371](https://github.com/tuskyapp/Tusky/pull/4371)
- support for Android 14 Upside Down Cake [PR#4224](https://github.com/tuskyapp/Tusky/pull/4224)
- Various internal refactorings to improve performance and maintainability.
  [PR#4269](https://github.com/tuskyapp/Tusky/pull/4269)
  [PR#4290](https://github.com/tuskyapp/Tusky/pull/4290)
  [PR#4291](https://github.com/tuskyapp/Tusky/pull/4291)
  [PR#4296](https://github.com/tuskyapp/Tusky/pull/4296)
  [PR#4364](https://github.com/tuskyapp/Tusky/pull/4364)
  [PR#4366](https://github.com/tuskyapp/Tusky/pull/4366)
  [PR#4372](https://github.com/tuskyapp/Tusky/pull/4372)
  [PR#4356](https://github.com/tuskyapp/Tusky/pull/4356)
  [PR#4348](https://github.com/tuskyapp/Tusky/pull/4348)
  [PR#4339](https://github.com/tuskyapp/Tusky/pull/4339)
  [PR#4337](https://github.com/tuskyapp/Tusky/pull/4337)
  [PR#4336](https://github.com/tuskyapp/Tusky/pull/4336)
  [PR#4330](https://github.com/tuskyapp/Tusky/pull/4330)
  [PR#4235](https://github.com/tuskyapp/Tusky/pull/4235)
  [PR#4081](https://github.com/tuskyapp/Tusky/pull/4081)

### Significant bug fixes

- The setting to hide the notification filter bar that was accidentally removed is back. [PR#4225](https://github.com/tuskyapp/Tusky/pull/4225)
- The profile picture in the bottom navigation bar now has the correct content description. [PR#4400](https://github.com/tuskyapp/Tusky/pull/4400)

## v24.1

- The screen will stay on again while a video is playing. [PR#4168](https://github.com/tuskyapp/Tusky/pull/4168)
- A memory leak has been fixed. This should improve stability and performance. [PR#4150](https://github.com/tuskyapp/Tusky/pull/4150) [PR#4153](https://github.com/tuskyapp/Tusky/pull/4153)
- Emojis are now correctly counted as 1 character when composing a post. [PR#4152](https://github.com/tuskyapp/Tusky/pull/4152)
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
