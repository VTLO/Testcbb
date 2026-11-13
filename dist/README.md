# VTLOgenerat0r — downloads

By **VTLO**. Ready-to-use builds of VTLOgenerat0r — a Google-dorking recon aid for
**authorized bug-bounty / pentest work only**.

## 📱 Android — `VTLOgenerat0r.apk`

Debug-signed APK, `minSdk 24` (Android 7) → `targetSdk 34` (Android 14).

```bash
adb install -r dist/VTLOgenerat0r.apk
```

Or copy the APK to a device and open it (enable "Install unknown apps" for your file
manager). A short **animated French guide** plays on first launch; long-press the title
to replay it.

## 💻 PC / web app — `web/`

A self-contained browser build (no install, no build step, no dependencies). It mirrors
the Android app: dork generation, auto-check, chaining, and the feedback-weighted
generative engine, with state saved in your browser's `localStorage`.

**Run it:**

```bash
# simplest — just open the file
open dist/web/index.html            # macOS      (or double-click it)
xdg-open dist/web/index.html        # Linux
start dist\web\index.html           # Windows

# recommended — serve over http so the Custom Search auto-check (fetch) works cleanly:
cd dist/web && python3 -m http.server 8080
#   then browse to http://localhost:8080
```

The same **animated French guide** shows on first visit (and via the **Guide** button).

### Auto-check (both builds)

"Which dorks return results" uses the **official Google Programmable Search (Custom
Search JSON API)** with **your own API key + Search-Engine id**, entered in **Settings**
and stored locally — not scraping. Create credentials at
<https://developers.google.com/custom-search/v1/overview> (free tier: 100 queries/day).
Without a key, both builds still work as clickable-link generators.

> Use only against targets you are explicitly authorized to test.

## Files

```
dist/
├── VTLOgenerat0r.apk     # Android build
└── web/
    ├── index.html        # PC web app (open or serve this)
    └── app.js            # app logic (dorks, auto-check, chaining, generator, guide)
```
