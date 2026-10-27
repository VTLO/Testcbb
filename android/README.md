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
  `subdomains`, `params`, `leaks`, `github`, `juicy`) plus `all` — **98 seed dorks**,
  matching `dork_runner.py` one-for-one.
- Same clickable dark-themed HTML report layout (`generate_html_report`).
- Same `.txt` export format (`# [category] / DORK: / URL:`).

## Beyond the script

### 1. Auto-check — which dorks actually return results

Tap **Auto-check** (or `✓ check` on a single dork) and the app runs each query and
flags the ones that return results (green = hit, dimmed = miss, with the top result
link shown inline).

This uses the **official Google Programmable Search (Custom Search JSON API)** with
**your own API key + Search-Engine id**, entered in **Settings** and stored locally —
*not* scraping of `google.com`, which violates Google's ToS and is CAPTCHA-blocked from
app clients. Create a key + engine at
<https://developers.google.com/custom-search/v1/overview>. Free tier is 100 queries/day,
so the app caps each auto-check run at 50 and spaces requests out. Without a key the app
still works as a clickable-link generator. **Only auto-check targets you are authorized
to test.**

### 2. Chaining — pivot from a hit into further investigation

When a dork returns a result, a `⛓ chain` action appears. It reads the host/path of the
top result and queues scoped follow-up dorks (dig into the discovered subdomain, enumerate
a discovered cloud bucket, probe parameterized URLs for SSRF/redirect/LFI leads). All
follow-ups stay within the original target's scope.

### 3. Generative dork engine with a feedback loop (the "learning" part)

Tap **New dorks** to synthesize brand-new, de-duplicated dork templates from a grammar of
search operators + keyword pools, and persist them to the on-device database
(`dorkrunner.db`) for reuse. It is **not** a neural network: it is a recombination
generator biased by a Laplace-smoothed hit-rate per category that the DB accumulates from
auto-check results — an online bandit that shifts generation toward the operator/keyword
patterns which have actually returned results on your runs. Generated dorks are tagged
`generated` and flow through the same pipeline (and future auto-checks feed the weights
back in).

### Local database

`dorkrunner.db` (plain SQLite, no network sync) stores every template with `runs`/`hits`
counters, plus a `findings` table of dorks that returned results. It is seeded with the 98
built-in dorks on first launch and grows as you generate.

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
