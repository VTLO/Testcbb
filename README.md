# VTLOgenerat0r

**A native Google-dorking recon tool for authorized bug-bounty & pentest work.**

By **VTLO**.

Ported from [`scripts/dork_runner.py`](scripts/dork_runner.py) → native Android app (Kotlin) + PC web app (vanilla JS).

## 📱 Builds

- **Android APK** (`dist/VTLOgenerat0r.apk`) — minSdk 24 (Android 7) → targetSdk 34 (Android 14)
- **PC / web app** (`dist/web/index.html`) — no install, no dependencies

[**→ Download and run**](dist/README.md)

## Features

- **98 seed dorks** across 10 categories (`credentials`, `pii`, `admin`, `errors`, `cloud`, `subdomains`, `params`, `leaks`, `github`, `juicy`)
- **Auto-check** — which dorks return results (via Google Programmable Search API)
- **Chaining** — pivot from a hit into scoped follow-up dorks
- **Generative engine** — synthesize new dorks with feedback-weighted learning
- **Animated French guide** — first-launch walkthrough on both builds
- **Click & export** — view results in-app, export as `.txt`

Both builds use your own **Google Programmable Search (Custom Search JSON API)** key for auto-check — not scraping, stored locally.

## Project Structure

```
VTLOgenerat0r/
├── dist/                    # Downloadable builds
│   ├── VTLOgenerat0r.apk   # Android
│   └── web/                 # PC web app
│       ├── index.html
│       └── app.js
├── android/                 # Kotlin source
│   └── src/main/java/com/shuvonsec/dorkrunner/
└── scripts/dork_runner.py   # Original Python dork generator
```

[Android source details](android/README.md)

## Use Only for Authorized Targets

This tool generates search links for recon. Use only on targets you are explicitly authorized to test (bug-bounty programs, authorized pentests, etc.).

---

**Free tier:** 100 Custom Search queries/day. Auto-check caps each run at 50 queries and spaces requests to fit within limits.
