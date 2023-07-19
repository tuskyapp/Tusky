# Getting network data to debug Tusky

## Synopsis

Tusky expects Mastodon servers -- or servers that claim they are compatible -- to behave according to the Mastodon API specification.

If they do not Tusky may not work correctly. That might manifest as a crash, or as incorrect or missing data appearing in the timelines.

Debugging this can be difficult. This document explains how you can perform the same API requests that Tusky performs and report the results so the developers have a better chance of fixing the problem.

After reading this document you will know:

- How to find your account's bearer token
- How to send API requests to your Mastodon server
- How to safely report that data to the Tusky developers

Before reading this document you should have:

- Instructions from a Tusky developer about the API requests they want you to make.

## Finding your bearer token

A bearer token is like a second password to your account.

When you first signed in to your account with Tusky your server sent back a bearer token. Tusky saved this to your device, and uses it instead of your password.

> **IMPORTANT**: These instructions show you how to find a bearer token.
>
> Treat it like a password. You should keep it secret, and not share it with anyone else.
>
> Do not give it to anyone else -- the Tusky developers will never ask you for it.
>
> If you have shared your bearer token with anybody you should immediately revoke it.
>
> To revoke your bearer token:
> - Log in to your Mastodon instance
> - Open your account settings
> - Choose "Account > Authorized apps"
> - Click "Revoke" next to the entry or entries for Tusky.

### Using Tusky

If you can build and run Tusky locally in an emulator you can modify the code to log your bearer token. Then you can copy it from the log.

To do this:

1. Edit `MainActivity.onCreate()` around line 168, immediately after the code that checks if `activeAccount`is `null`:

```kotlin
Log.d(TAG, "Bearer Token: ${activeAccount.accessToken})
```

2. Rebuild and run Tusky in the emulator, and copy the token from the log.

### Using a web browser

> Instructions tested with Firefox and Chrome. Instructions may not work for other browsers.

1. Log in to your server using a web browser.
2. Open the browser's developer tools in the same tab that contains your Mastodon timeline (instructions for [Firefox](https://firefox-source-docs.mozilla.org/devtools-user/page_inspector/how_to/open_the_inspector/index.html) and [Chrome](https://developer.chrome.com/docs/devtools/open/))
3. Navigate to the "Network" tab in the developer tools.
4. Set the request type toggle to "XHR"
5. Reload the Mastodon page

   You should see a number of entries appear for the different API requests the browser is making.
6. Find the entry for the "home" request.
7. Right-click on the "home" entry, and choose "Copy Value > Copy as cURL (POSIX)".
8. Open your preferred text editor, and create a new, blank file
9. Paste in to the new file.
   You will see multiple lines of text, starting with `curl`
10. Find the bearer token. It should be in the first half of the text, and look like `... -H 'Authorization: Bearer ABCD12345...'`.

   The bearer token is the `ABCD12345...` part, up to, but not including, the closing single-quote character. The `Bearer ` text before, and any text afterwards is **not** part of the token.

## Sending API requests

There are many tools you can use for sending API requests. You may already be familiar with some, and/or have a preference. If so you can use them. Use the rest of this section as a guide to use when constructing the API requests using the tool of your choice.

If not, we recommend using the https://restfox.dev/ service. This is free, open source ([GitHub](https://github.com/flawiddsouza/Restfox)), does not save any of your data, and does not require you to log in.

### Using restfox.dev

1. Open https://restfox.dev/
2. If this is the first time you have used it you will need to click "Add Workspace" in the menu. Enter a name of your choice and click the "Create" button.
3. Create a new request. Right-click in the left pane of the display and choose "New Request" from the menu. Enter a name of your choice and click the "Create" button.
4. The request should be listed in the left pane, and the middle pane should show information about the request.
5. Set up authentication:
   1. In the middle pane click "Auth".
   2. Change the drop-down item from "No Auth" to "Bearer Token"
   3. Copy/paste the bearer token you found earlier in to the "Token" field.
   4. Leave the "Prefix" field empty
6. Above the "Auth" tab is a request field, labelled "Enter request URL". The Tusky developer will have told you what to put in this field
7. Send the request by clicking the "Send" button.
8. If you see a "The access token is invalid" message you have not copied the bearer token correctly. Double check that you have copied the token correctly and adjust as necessary.
9. The results of the request will appear in the right pane in three tabs, "Preview", "Header", and "Request".

## Reporting the data

You now have data that you can report to the Tusky developers.

> **DO NOT SHARE YOUR BEARER TOKEN**

Depending on the nature of the problem they may need a copy of some or all of the contents from the "Preview", "Header", or "Request" tabs in the right pane.

Before you do this you may have to manually redact some of the data.

For example, normally the accounts you follow, and the accounts that follow you, are public information. But you may have set that to private in your Mastodon profile settings. If that is the case you may want to redact all of the account names in the results before you pass the data on.

