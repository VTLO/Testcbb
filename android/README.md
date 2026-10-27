# VTLOgenerat0r — Android app

A native Android (Kotlin) port of [`scripts/dork_runner.py`](../scripts/dork_runner.py)
(*Google Dork Runner by Shuvonsec*). It takes an **in-scope** target domain, builds the
same Google search ("dork") URLs the Python script generates, and renders them as a
clickable report. Tapping a dork opens that Google search in the device browser; the
**Share .txt** button exports the same plain-text list the Python `-o` flag produces.

Like the script, this is a recon aid for **authorized bug-bounty / pentest work only** —
it generates search links; it does not scrape, automate, or exploit anything.

## Features (parity with the Python script)

- All 10 dork categories (`credentials`, `pii`, `admin`, `errors`, `cloud`,
  `subdomains`, `params`, `leaks`, `github`, `juicy`) plus `all` — **98 dorks**, matching
  `dork_runner.py` one-for-one.
- Same clickable dark-themed HTML report layout (`generate_html_report`).
- Same `.txt` export format (`# [category] / DORK: / URL:`).

## Prebuilt APK

A debug-signed APK is checked in at **[`VTLOgenerat0r.apk`](VTLOgenerat0r.apk)**.

```
Package:   com.shuvonsec.dorkrunner
Label:     VTLOgenerat0r
minSdk:    24 (Android 7.0)   targetSdk: 34 (Android 14)
Signing:   Android debug key
```

Install on a device/emulator:

```bash
adb install -r android/VTLOgenerat0r.apk
```

(Debug APKs install directly; for sideloading outside `adb`, enable "Install unknown apps".)

## Build from source

Requires a JDK 17 and the Android SDK (platform-34, build-tools 34.0.0).

```bash
cd android
echo "sdk.dir=/path/to/Android/sdk" > local.properties
./gradlew :app:assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

For a release build, add a signing config and run `./gradlew :app:assembleRelease`.

## Project layout

```
android/
├── settings.gradle, build.gradle, gradle.properties
├── VTLOgenerat0r.apk                 # prebuilt debug APK
└── app/
    ├── build.gradle
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/shuvonsec/dorkrunner/
        │   ├── Dorks.kt              # dork templates + report generation (ported 1:1)
        │   └── MainActivity.kt       # UI, WebView report, share/export
        └── res/                      # layout, strings, theme, launcher icon
```
