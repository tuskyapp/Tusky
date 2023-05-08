# Tusky changelog

## Unreleased or Tusky Nightly

### New features and other improvements

### Significant bug fixes

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
