# Tusky

![](https://lh3.googleusercontent.com/6Ctl3PXaQi19qMaipWwzHAoKS9M9zy328cuulNZNAmRbjsPkSXs2xJ2OcyQNpOy23hI=w100)

Tusky is a beautiful Android client for [Mastodon](https://github.com/tootsuite/mastodon). Mastodon is a GNU social-compatible federated social network. That means not one entity controls the whole network, rather, like e-mail, volunteers and organisations operate their own independent servers, users from which can all interact with each other seamlessly.

It is currently available for alpha testing on [Google Play](https://play.google.com/store/apps/details?id=com.keylesspalace.tusky). You can also find it on F-Droid or at its [F-Droid page](https://f-droid.org/repository/browse/?fdid=com.keylesspalace.tusky).

## Features

- Material Design
- Most Mastodon APIs implemented
- Push notifications

#### Head of development

My Mastodon account is [Vavassor@mastodon.social](https://mastodon.social/users/Vavassor).

## Building

### Dependencies
- Java Development Kit 7 or higher
- Android SDK

The project uses [the Gradle build system](https://gradle.org). Android studio uses Gradle by default, so it'd be straightforward to import this repository using your chosen version control software from the menu:
<pre>VCS > Checkout from version control > Git/SVN/Mercurial</pre>
After making it into an android studio project you can build/run as you wish.

It's also possible to build using Gradle by itself on the command line if you have it installed and configured. This repository includes a gradle wrapper script that can be used, following this guide [Build You App On The Command Line](https://developer.android.com/studio/build/building-cmdline.html).