# Kuromaku Reader

Android personal reader for the Monogatari no Kuromaku label on Kaori Translation.

## Current version
- Opens the source label and individual chapters inside a WebView.
- Attempts to simplify the reading view using CSS, while retaining story text and inline images.
- Light/dark modes, font size adjustment, back navigation and last-opened chapter URL.
- Online only. Does not bundle or redistribute translations.

**Limitations:** This is an initial MVP, not a full chapter parser. Chapter/volume sorting, reading-position bookmarks, next/previous chapter navigation and offline downloads are not implemented. CSS may need adjustments if the source website changes. Ads or overlays outside the known selectors may still appear. Respect the original publisher's terms and copyright.

## Build via GitHub Actions
1. Upload the **contents** of this folder to `Shuya-sal/kuromaku-reader` (including the hidden `.github` directory).
2. Commit to `main`.
3. Open **Actions → Build Kuromaku Reader APK**.
4. Download the `kuromaku-reader-debug` artifact after a successful run.
5. Extract the ZIP artifact and install `app-debug.apk` on Android.

For a local build, use JDK 17, Android SDK 35 and Gradle 8.9, then run `gradle assembleDebug`.
