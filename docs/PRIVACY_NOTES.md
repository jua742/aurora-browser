# Aurora Browser — Privacy Notes (v1)

Honest, plain-language notes about what the app does and does not do with your data.
These statements are binding on the implementation: if the code ever contradicts them, the code is wrong.

## The short version

- **Everything stays on your device.** There is no backend, no account system, no sync, no analytics SDK, no crash reporter phoning home. Bookmarks, history, downloads metadata, tabs, settings, and per-site permission choices live in a local Room database and DataStore on the phone (spec B-11/B-12).
- **No secrets in the app.** v1 stores no passwords, tokens, or API keys, so there is nothing to encrypt on-device and nothing to leak (spec B-11, B-13 §6).

## What Incognito does

- While an incognito tab is active: no history rows are written.
- When the last incognito tab closes: cookies, cache, site data (WebStorage), and form data are wiped.
- Bookmarks you explicitly save are still kept (a bookmark is a deliberate user action).

## What Incognito does NOT do

- It does **not** make you anonymous. It does not hide you from your internet provider, your employer, or the websites you visit. The app must never claim otherwise (spec B-9 honesty constraints).
- It does **not** isolate cookies per tab: Android's CookieManager is process-global, so private tabs share the cookie jar while open — the wipe happens afterwards (spec assumption 6).
- **Files you download are still saved** to the shared Downloads folder, even from incognito tabs. The UI says this plainly (spec B-9).

## Downloads

- Downloads run through the system **DownloadManager** writing to the public **Downloads** folder. The app never requests storage permissions and never scans your files.
- To fetch files from logged-in sites, the app forwards the site's **Cookie** and **User-Agent** headers with the download request — nothing else.
- **POST_NOTIFICATIONS** (Android 13+) is requested once, just before the first download, so the system can show completion notifications. Declining changes nothing about downloading: progress stays visible in the in-app Downloads screen.
- blob: downloads (some sites' "download" buttons) are **not supported in v1** — the app shows "This download type isn't supported yet" instead of faking a success (spec A).

## Permissions

- **Normal (install-time):** INTERNET, ACCESS_NETWORK_STATE — the app cannot load pages without them.
- **Runtime, in-context only:** CAMERA / RECORD_AUDIO (only when a site requests capture), ACCESS_FINE/COARSE_LOCATION (only when a site requests geolocation, after a per-site allow dialog), POST_NOTIFICATIONS (before first download). Each shows a rationale dialog first when appropriate; "don't ask again" is respected and routes to app settings instead of re-asking in a loop.
- No storage permissions. No background location. Hardware features are declared `required="false"` so the Play Store never filters the app for missing hardware.

## WebView hardening (what keeps sites contained)

- JavaScript on (user-toggleable), Safe Browsing on, mixed content never allowed.
- `allowFileAccess=false`, no file-URL access to other files; **no `addJavascriptInterface`** in v1 (verified by repo grep, 2026-10-02).
- SSL errors: the load is **cancelled** and an error page is shown — there is deliberately **no "proceed anyway"** option in v1 (verified: no `handler.proceed()` in the repo).
- External schemes: only `tel:`, `mailto:`, `sms:` are opened, and only after a resolve check; everything else (including `intent:` URLs) is blocked.
- Database migrations must never be destructive in release (verified: no `fallbackToDestructiveMigration` in the repo) — an upgrade must not wipe user data.

## What the app will never do

- Claim anonymity, invisibility, or "military-grade privacy".
- Send browsing data, keystrokes (search suggestions are local history/bookmarks only), or telemetry anywhere.
- Support web push notifications from sites in v1 (a WebView limitation — not claimed).

## Verification log

- 2026-10-02 (Worker C, Phase 10): greps over the repo found **zero** occurrences of `addJavascriptInterface`, `onReceivedSslError`/`handler.proceed()`, `fallbackToDestructiveMigration`, hardcoded password/key/secret literals, and `javascript:`-URL loading. Re-run after all workers' code lands and before release.
