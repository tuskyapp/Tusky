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

### Find something to work on

https://github.com/tuskyapp/Tusky/contribute is a list of open issues that have been tagged `good first issue`. This means the changes should be relatively small and self-contained.

You do not have to restrict yourself to just working on one of those issues, but as the name suggests, they're a good place to start.

### Best practices for working on an issue

- Post a comment to the issue, saying that you plan on working on it
- Join the [Tusky Matrix channel](https://matrix.to/#/#Tusky-General:matrix.org)

### Best practices for creating PRs

- The PR title and first comment should be an effective commit message for a PR as a whole
- Send PRs early if you have something to show, but mark a PR as draft until it's ready for review
  - If you want early feedback `@` someone in to the PR discussion by mentioning their GitHub username
- Err on the side of over-communicating; if there are different approaches to solving the issue explain what they are, and why you chose one over another
- Include before/after screenshots as PR comments if your PR changes the UI
- PR should pass lint and tests before sending for review


### Architecture
We try to follow the [Guide to app architecture](https://developer.android.com/topic/architecture).

### Kotlin
Tusky was originally written in Java, but is in the process of migrating to Kotlin. All new code must be written in Kotlin.
We try to follow the [Kotlin Style Guide](https://developer.android.com/kotlin/style-guide) and format the code according to the default [ktlint codestyle](https://github.com/pinterest/ktlint).
You can check the codestyle by running `./gradlew ktlintCheck lint`. This will fail if you have any errors, and produces a detailed report which also lists warnings.
We intentionally have very few hard linting errors, so that new contributors can focus on what they want to achieve instead of fighting the linter.

This is enforced on CI; new code must pass `ktlintCheck`

### Android Lint

You can run Android Lint locally to check for potential problems: `./gradlew lintGreenDebug`.

The use of `@Suppress...` annotations to ignore lint issues in some areas of the code is allowed, but not encouraged. It is helpful if you preemptively explain in your PR why the lint issue can not be corrected.

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

## Project norms for GitHub issue management

> Some contributors have additional GitHub access rights to manage issues and issue metadata. This section is for them.

### How issues are assigned

1. An issue is never assigned *by* someone *to* someone else. As a volunteer project the assigner can never be sure that the assignee is available to resolve the issue right now.

2. Therefore, **issues are only ever self-assigned**.

3. Self-assigning an issue is a clear statement of “I will do the work necessary to resolve this issue. If I can not do that in reasonable time I will unassign the issue”

4. If you have assigned an issue to yourself, please provide periodic updates to that issue. This is to ensure that the state of the work on the issue is clear to everyone else on the project.

    a. The update may be a variation of “I have not been able to work on this”. That's OK. The important thing is that this is valuable information to convey to everyone else.

    b. If a PR is associated with the issue then updates to the PR are treated as updates to the issue

5. It is OK if you have assigned an issue to yourself and then discover that you are not able to complete it or provide useful updates. If that happens, just remove the assignment from the issue. You don't have to provide a justification for why you can't do it at this time -- it's enough to let the rest of the team know that someone else will need to pick this up.

6. If you are working on an issue and someone else needs to make a decision, `@` them into the issue, and add the `blocked` label so they can find it.

    a. Per item (1) do not assign the issue to the person who needs to make the decision.

    b. If you are blocked, please continue providing updates, even if the update is “Blocked, waiting on a decision from @whoever”, so that you continue communicating the state of the work to the rest of the team.

### Setting issue labels

The labels communicate three different things about the issue:

- The issue's type: `bug`, `enhancement`, `question`, `refactoring`
- The issue's state: `blocked`, `duplicate`, `help wanted`, `invalid`, `wontfix`
- Additional metadata: `good first issue`

From that it follows:

- Every issue should have exactly one of the type labels
    - The type may change over time. For example, the discussion on an issue initially labelled `question` may uncover a `bug` or `enhancement` proposal.
- The state labels are optional
    - `blocked` and `help wanted` only makes sense on issues that are open
    - `duplicate`, `invalid`, `wontfix` only make sense on issues that are closed

The line between a "bug" and an "enhancement" can be fuzzy. For example, at the time of writing users can upload profile photos, but they cannot delete them. Is that a bug to be fixed, or an enhancement to the existing functionality?

We don’t think it matters yet. We’re not tracking metrics around "How many bugs did we fix?" or "How many new features did we provide?", so don’t worry over which label is more appropriate.

### Finding stale issues

GitHub does not have native functionality to "Show all issues assigned to me that have not been updated in the last 7 days", but that can be performed by a browser bookmarklet, with the following Javascript.

```javascript
javascript: (
  () => {
    const d = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000);
    const yyyy = d.getFullYear();
    const mm = d.getMonth() + 1;
    const dd = d.getDate();
    const yyyymmdd = `${yyyy}-${mm.toString().padStart(2, '0')}-${dd.toString().padStart(2, '0')}`;
    const q = `is:open updated:<=${yyyymmdd} assignee:@me`;
    window.open("https://github.com/tuskyapp/Tusky/issues?q=" + encodeURIComponent(q), '_self'); 
  }
)();
```
