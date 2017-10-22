# Tusky

![](/assets/tusky_logo.png)

Tusky is a beautiful Android client for [Mastodon](https://github.com/tootsuite/mastodon). Mastodon is a GNU social-compatible federated social network. That means not one entity controls the whole network, rather, like e-mail, volunteers and organisations operate their own independent servers, users from which can all interact with each other seamlessly.

## Features

- Material Design
- Most Mastodon APIs implemented
- completely Open-source - no non-free dependencies like Google services

#### Head of development

This app was developed by [Vavassor@mastodon.social](https://mastodon.social/users/Vavassor).
The current maintainer is [ConnyDuck@mastodon.social](https://mastodon.social/users/ConnyDuck).

[<img src="/assets/fdroid_badge.png" alt="Get it on F-Droid" height="80" />](https://f-droid.org/repository/browse/?fdid=com.keylesspalace.tusky)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" alt="Get it on Google Play" height="80" />](https://play.google.com/store/apps/details?id=com.keylesspalace.tusky&utm_source=github&pcampaignid=MKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1)
[![Available at Amazon](/assets/amazon_badge.png)](https://www.amazon.com/gp/product/B06ZYXT88G/ref=mas_pm_tusky)

## Building
The most basic things needed are the Java Development Kit 7 or higher and the Android SDK.

The project uses [the Gradle build system](https://gradle.org). Android studio uses Gradle by default, so it'd be straightforward to import this repository using your chosen version control software from the menu:
<pre>VCS > Checkout from version control > Git/SVN/Mercurial</pre>
After making it into an android studio project you can build/run as you wish.

It's also possible to build using Gradle by itself on the command line if you have it installed and configured. This repository includes a gradle wrapper script that can be used, following this guide [Build You App On The Command Line](https://developer.android.com/studio/build/building-cmdline.html).

The project's gradle files describe its building needs more in-depth and dependencies can be found in ```app/build.gradle```.
