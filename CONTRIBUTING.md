# Contributing

## Getting Started
1. Fork the repository on the GitHub page by clicking the Fork button. This makes a fork of the project under your GitHub account.
2. Clone your fork to your machine. ```git clone https://github.com/<Your_Username>/Tusky```
3. Create a new branch named after your change. ```git checkout -b your-change-name``` (```checkout``` switches to a branch, ```-b``` specifies that the branch is a new one)

## Making Changes

### Text
All English text that will be visible to users should be put in ```app/src/main/res/values/strings.xml```. Any text that is missing in a translation will fall back to the version in this file. Be aware that anything added to this file will need to be translated, so be very concise with wording and try to add as few things as possible. Look for existing strings to use first. If there is untranslatable text that you don't want to keep as a string constant in a Java class, you can use the string resource file ```app/src/main/res/values/donottranslate.xml```.

### Translation
Translations are done through https://weblate.tusky.app/projects/tusky/tusky/ . 
To add a new language, clic on the 'Start a new translation' button on at the bottom of the page. 

### Kotlin
This project is in the process of migrating to Kotlin, we prefer new code to be written in Kotlin. We try to follow the [Kotlin Style Guide](https://android.github.io/kotlin-guides/style.html) and make use of the [Kotlin Android Extensions](https://kotlinlang.org/docs/tutorials/android-plugin.html).

### Java
Existing code in Java should follow the [Android Style Guide](https://source.android.com/source/code-style), which is what Android uses for their own source code. ```@Nullable``` and ```@NotNull``` annotations are really helpful for Kotlin interoperability.

### Visuals
There are three themes in the app, so any visual changes should be checked with each of them to ensure they look appropriate no matter which theme is selected. Usually, you can use existing color attributes like ```?attr/colorPrimary``` and ```?attr/textColorSecondary```. For icons and drawables, use a white drawable and tint it at runtime using ```ThemeUtils``` and specify an attribute that references different colours depending on the theme.

### Saving
Any time you get a good chunk of work done it's good to make a commit. You can either uses Android Studio's built-in UI for doing this or running the commands:
```
git add .
git commit -m "Describe the changes in this commit here."
```

## Submitting Your Changes
1. Make sure your branch is up-to-date with the ```master``` branch. Run:
```
git fetch
git rebase origin/master
```
It may refuse to start the rebase if there's changes that haven't been committed, so make sure you've added and committed everything. If there were changes on master to any of the parts of files you worked on, a conflict will arise when you rebase. [Resolving a merge conflict](https://help.github.com/articles/resolving-a-merge-conflict-using-the-command-line) is a good guide to help with this. After committing the resolution, you can run ```git rebase --continue``` to finish the rebase. If you want to cancel, like if you make some mistake in resolving the conflict, you can always do ```git rebase --abort```.

2. Push your local branch to your fork on GitHub by running ```git push origin your-change-name```.
3. Then, go to the original project page and make a pull request. Select your fork/branch and use ```master``` as the base branch.
4. Wait for feedback on your pull request and be ready to make some changes

If you have any questions, don't hesitate to open an issue or contact [Tusky@mastodon.social](https://mastodon.social/@Tusky). Please also ask before you start implementing a new big feature.
