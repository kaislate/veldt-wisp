# Changelog

## Unreleased

- The codebase has been rewritten clean-room. Every file that carried
  DynamicIslandMusic expression was deleted or excised and reauthored from a
  behavioural specification, across two slices; the unit-test suite grew from
  32 to 305 tests in the process.
- A file-by-file comparison against the original import shows no shared
  comments and no shared non-boilerplate literals or code outside a small
  retained set of convergent boilerplate — one-line use-case wrappers, a Hilt
  module providing framework services, permission-check one-liners and a
  published lifecycle-owner recipe — all of which take the same form regardless
  of author.
- The upstream project's attribution requirement therefore no longer applies
  and has been removed from `LICENSE`. The upstream copyright notice and the
  MIT grant are retained, so that the convergent boilerplate kept above stays
  licensed.

## 0.7.0

- Renamed **Veldt → Veldt Wisp** (display name, notifications, repo). The
  `applicationId` is unchanged, so this updates in place over 0.6.0.
- The name "Veldt" now belongs to a forthcoming full music player; this app is the
  standalone pill.
- Update checker now targets the renamed `kaislate/veldt-wisp` repository.

## 0.6.0 — 2026-07-09

### Added
- **Album-art crossfade** — when the track changes, the pill and expanded panel now fade from the old artwork to the new one instead of cutting. On by default, with a toggle in Appearance and an adjustable fade duration slider (shown only while the crossfade is on). Default fade is 1000 ms.
- **Tap to open the playing app** — tapping anywhere above the scrub bar (artwork, title, or artist) in the expanded panel opens the app that's playing.
- **Reset to defaults** — a button at the bottom of Settings restores every setting to its default value, with a confirmation prompt.

### Changed
- **Compact placement control** — the device mockup at the top of Settings is now a fixed size (no longer balloons on tablets), with the notification toggle as a switch inside that area.
- **"Enable Veldt" moved to the top** of Settings, above Placement.
- **Output-device label is device-aware** — the built-in speaker now reads "Tablet speaker" on tablets and "Phone speaker" on phones.
- **New defaults** — edge offset 40 dp (max raised to 60 dp), pill width 160 dp, bar thumb shape, light-accent wave colour, vibrant wave off.

### Fixed
- **Missing album art on more apps/devices** — art is now resolved from the display-icon and album-art/art metadata keys, from art *URIs* (loaded via Coil) when an app provides only a URI, and from the media notification's large icon as a last resort. (Note: some patched YouTube builds on Android 11 expose no artwork through any channel a media reader — including the system's own lock-screen controls — can read; those still show the placeholder.)
- **Tap-to-open failing on Android 11+** — declared launcher-app visibility so the playing app resolves and opens (Android 11's package-visibility rules were silently blocking the launch).

## 0.5.8 — 2026-07-08

### Fixed
- **Panel blink when pressing pause** — two session-layer causes: some apps (VLC) briefly re-post null metadata around pause, swapping the album art to the placeholder and back (Veldt now keeps the last known metadata); and with multiple paused sessions the selector could hop to a different app the moment nothing was playing (it now sticks with the current session).
- **Prev/Next buttons grey out when the session doesn't support them** — apps advertise skip capabilities; pressing "next" in VLC with nothing queued used to just kill the session and the island with it. Unsupported directions are now disabled and dimmed.

## 0.5.7 — 2026-07-08

### Changed
- **Expansion on Android 10–12/15 redesigned as separate entities** (per feedback): instead of trying to impersonate the pill across two windows — which always leaked blinks — the pill now simply fades out while the panel fades and scale-grows in as its own surface, and the reverse on close. Clean, honest, and blink-proof by construction. Android 13/14 keeps its true in-window morph.

### Fixed
- The Download/Install button revealed by "Check for updates" no longer appears below the fold — the settings auto-scroll to keep the update controls in view.

## 0.5.6 — 2026-07-08

### Fixed
- **Updater no longer requires two full check/download rounds.** First-time installs bounce through Android's "install unknown apps" grant screen; Veldt now keeps the downloaded APK and an Install button alive through that round-trip (and proactively opens the grant screen when needed) — grant once, tap Install, done.
- **Pill blink on expand/collapse eliminated** (two-window mode): the real pill window is now never hidden — the panel simply covers it while open and reveals it on close. With no visibility toggle there is nothing left to blink.

## 0.5.5 — 2026-07-08

### Fixed
- **Blink when expanding/collapsing** (two-window mode, Android 10–12/15): removed the system window fade animation on the panel window and moved the pill/panel handoff to the panel's first actual draw — the swap is now pixel-matched in both directions.

## 0.5.4 — 2026-07-08

### Fixed
- **Pill sliding sideways on expand/collapse (Android 10–12 and 15)** — root-caused and eliminated: window resizes can never be frame-synced with their content, so the resizing-window mode always lurched. Expansion on these Android versions now uses a dedicated second window: the pill window never changes, and the morph plays inside a stable panel-sized window that exists only while expanded. No resizes, no slide, still no dead-zone. Android 13/14 keeps its existing (already smooth) single-window mechanism.

## 0.5.3 — 2026-07-08

### Fixed
- **Island dead after screen-off** on devices without a lock screen: the "unlocked" state now also recovers on screen-on (the unlock broadcast never fires without a keyguard).
- **Sideways lurch when expanding/collapsing** in the resizing-window mode (Android 10–12/15): content now anchors correctly inside the enlarged window for the whole animation.
- **Scrub bar no longer changes width** as the elapsed-time digits change (fixed-width time labels).
- **"Enable Veldt"** (renamed from "Enable island") now truly turns the program off — service stopped, notification removed — instead of only hiding the pill.

### Changed
- The persistent notification now opens Veldt settings when tapped (and says so), and uses the Veldt pill glyph as its status-bar icon.
- New **Hide persistent notification** setting (Behavior) — minimizes the notification and removes its status-bar icon; the warning notes that the Veldt app becomes the only way into settings.

## 0.5.2 — 2026-07-08

### Added
- **Pill width** and **Panel width** sliders (Appearance) — size the island to your screen.

### Fixed
- **Panel transparency on Android 10/11** — the frosted-glass background relied on an API-31+ blur and on apps providing album art; with neither (e.g. VLC on Android 11) the launcher showed through. The panel now has an opaque album-color base layer and a stronger glass tint — which also fixes barely-readable text on light artwork.
- **Smoother expand/collapse on Android 10–12 and 15** (the resizing-window mode): the morph now starts only after the window has grown (no more clipped first frames), and the window shrinks only after the collapse springs fully settle (no more end-of-animation snap).

## 0.5.1 — 2026-07-08

### Fixed
- **Android 15: touch dead-zone around the pill** (and the system's "isn't optimized / touches may be delayed" warning). Android 15 no longer reliably honors `setTouchableRegion` for overlay windows, so the fixed-window mode is now used only on Android 13/14 where it is proven; Android 15+ uses the content-sized resizing window (Google's documented overlay pattern), same as Android 10–12.

## 0.5.0 — 2026-07-07

### Added
- **Six-anchor positioning** — place the island at top or bottom × left/center/right; the expand morph, edge offset, and stash gesture all adapt to the anchor (bottom positions expand upward).
- **Edge offset** — the top-offset slider is now anchored-edge-relative (clears camera cutouts at the top, nav bars at the bottom).
- **Swipe-to-stash** — swipe the pill toward the screen edge to hide it behind a notification; tap the notification to bring it back.
- **Home-screen-only mode** — optionally show the island only on your launcher.
- **Thumb shapes** — circle / bar / none for the scrub-bar thumb.
- **Wave color modes** — auto (album accent with a new contrast guard so dark art never blends into the background) / white / light accent.
- **Manual update checker** — About → "Check for updates" queries GitHub Releases and installs in-app. This adds the INTERNET permission, used only when you tap the button.

### Fixed
- **Android 11/12: touch dead-zone around the pill** — the overlay window now resizes on pre-13 devices instead of relying on the Android 13 touchable-region API (the expand animation is simpler there by design).
- **Android 11/12: island not hiding inside the playing app** — foreground detection rewritten around usage events with sticky state; the island also no longer reappears if you idle in the playing app.
- Home-only mode can now actually identify launchers (Android 11+ package-visibility `<queries>` declaration).

## 0.4.0 — 2026-07-07

- Internal package fully renamed to `com.kaislate.veldt`; no upstream package remnants. **Updating from 0.3.0 requires re-granting Notification access** (the listener component changed).
- Removed the legacy activity-expansion path and the Lottie dependency; the expanded panel's soundwave is now the same hills animation as the pill.
- New settings: Top offset and Hide delay after pause (15/25/45/90 s).
- Settings screen rebuilt (General / Appearance / Behavior / Permissions / About); all strings in English.

## 0.3.0 — 2026-07-07

First public release. One UI-style now-playing pill for any Android 10+ device:
universal MediaSession support, One UI 9 overlapping-hills wave with album-art
colors, in-place spring morph pill ↔ media card, real scrubbing, hides inside
the playing app, no root / no Play Services / no network.
