# Veldt Wisp 🌾

### 🎉 Veldt is now **Veldt Wisp** — and it just hit **0.7.0**! 🎉

**The One UI-style "Now Bar" media pill for *any* Android 10+ device — no Samsung, no Google, no root. Now with configurable transport controls, twelve scrub-bar animations, and a tier of ridiculous, over-the-top *Premium* effects that take over the whole card.**

> **What changed?** The app formerly known as **Veldt** is now **Veldt Wisp**. Same
> beloved floating pill, sharper identity. "Wisp" says exactly what it is: a small,
> weightless thing that floats over everything and rides any app. The name also
> makes room for what's coming next — **Veldt**, a full local + self-hosted music
> player that will bundle this very pill as a built-in feature. This repo was
> formerly `kaislate/Veldt`; old links redirect.

---

Veldt Wisp floats a compact now-playing pill at the top of your screen. Tap it and it **morphs — in place, with spring physics —** into a full media card with album art, scrubbing, and playback controls. It reads the standard Android MediaSession, so it works with **any** media app: YouTube, NewPipe, Grayjay, VLC, AntennaPod, Spotify, podcasts — anything that shows a media notification.

Born to bring Samsung's One UI 8/9 *Now Bar* experience to a de-Googled LineageOS tablet — and to every other device Samsung and Google left behind.

## ✨ What's new in 0.7.0

- 🪄 **Rebrand to Veldt Wisp** — new name, new identity, same featherweight pill.
- 🎛️ **Transport controls everywhere** — full prev / play-pause / next on the expanded card, plus an **optional always-on control set right on the pill** (choose the button set and where it sits).
- 🖼️ **Knows who's playing** — the expanded card shows the source app's icon, and surfaces the app's own **custom actions** (shuffle, repeat, thumbs-up…) right alongside the transport buttons.
- 🌊 **Twelve scrub-bar animations** — a whole lab of motion styles for the played portion of the bar (see below), with a **Consume** mode that dissolves the already-played side as the playhead advances.
- 💎 **Premium whole-card effects** — ten cinematic, deliberately over-the-top effects that don't stay in the scrub bar: they spill across the entire card *and* the pill.
- 🎯 **Smarter foreground detection** — reworked to track each app's own lifecycle, so the pill no longer flickers on spurious background events.

## 🎵 Features

- **Universal now-playing pill** — round album art, bold title over artist (auto-scrolling), driven entirely by generic MediaSession data. No per-app integrations, no whitelist, no accounts.
- **In-place expand** — the pill morphs into a full media card right where it sits (no activity launch, no system transition). Tap anywhere outside, or swipe up, to collapse.
- **Real scrubbing** — tap or drag the wave bar to seek; every animation tapers politely around your thumb so nothing is ever cropped at the playhead.
- **Full playback controls** — previous / play-pause / next on the card, plus the app's own custom actions when it exposes them. Want them on the pill too? Flip one switch and pick a layout.
- **Album-art colors** — wave gradients and thumb glow are extracted live from the current track's artwork (with a vibrant fallback palette), and a contrast guard keeps auto colors from vanishing into near-white or near-black album art. Monochrome mode is one toggle away.
- **Stays out of the way** — hides while you're inside the app that's playing, reappears everywhere else; only the pill's own pixels are touchable — everything around it clicks straight through.
- **Six positions** — anchor the pill top or bottom, left / center / right; the morph, edge offset, and gestures all adapt to the anchor.
- **Swipe to stash** — flick the pill toward the screen edge to tuck it into a notification; tap to bring it back. Or enable **home-screen-only mode** so it lives on your launcher and nowhere else.
- **Built-in updates** — a manual "Check for updates" button against GitHub Releases. No background phone-home, ever.
- **Featherweight** — pure Kotlin / Jetpack Compose, **zero native code** (runs on 32-bit relics and modern arm64 alike), **zero Google Play Services**, **zero network access** — except the manual update check you trigger yourself.

## 🌊 Scrub-bar animations

Pick the vibe of the played portion of the bar. Twelve styles, each seamless-looping and thumb-aware:

| Style | What it does |
|-------|--------------|
| **Wisptrail** *(default)* | Drifting caustic filaments — the signature Veldt look |
| **Wisptrail X** | Wisptrail cranked into gorgeous chaos |
| **Hills** | The original One UI 9 overlapping translucent dunes |
| **Silk** / **Silk X** | A rippling satin ribbon with a raking sheen that sweeps toward the thumb |
| **Mercury** | Liquid-metal domes that merge and split with glinting crests |
| **Sparks** | A lit fuse — ember trail, hot tip, sparks arcing off under gravity |
| **Bubbles** | Spinning, reflective bubbles bloom from the playhead and drift away |
| **Choir** | A clean traveling wave of dots |
| **One UI** | Faithful layered-harmonic Now Bar wave |
| **Squiggly** | Fluid backward-trailing squiggle |
| **Loom** | A woven cloth with threads flowing back from the playhead |

Plus a **Consume bar** option that fades the already-played side so the animation lives in a trailing comet behind the playhead, and a set of **thumb shapes** (circle, bar, ring, square, diamond, triangle, glow, or none).

## 💎 Premium effects

Ten **whole-card** effects — they escape the scrub bar and choreograph the entire media card *and* the collapsed pill in one coordinated show. Ridiculous on purpose.

**Interference** (chromatic glitch & tearing) · **Cyberpunk** (neon grid + breathing frame) · **Caldera** (a re-striking lightning storm with arcs crawling the card border) · **Aurora** (northern-light curtains & stars) · **Prism** (spectral refraction beams) · **Warp** (hyperspace starfield jump) · **Embers** (coal glow & drifting fireflies) · **Eclipse** (a black sun with a breathing corona) · **Monsoon** (Blade-Runner neon rain) · **Pulse** (a bass-drop that physically thumps the whole card in time).

## 📋 Requirements

- Android 10+ (API 29)
- Three permissions, all grantable in the app's setup screen:
  - **Notification access** — to read the media session (what's playing)
  - **Display over other apps** — to draw the pill
  - **Usage access** — to hide the pill inside the playing app

No root. No Play Services. No account. No network — except the update check you trigger yourself.

## 📥 Install

Grab the APK from [Releases](../../releases), install it, open **Veldt Wisp**, and grant the three permissions it asks for. Play music. That's it.

## 🛠️ Build

```
git clone https://github.com/kaislate/veldt-wisp.git
cd veldt-wisp
./gradlew assembleDebug
```

Requires JDK 17+ and the Android SDK (compileSdk 36). Release builds are signed
with a local keystore via an untracked `key.properties`; without it,
`assembleRelease` produces an unsigned APK.

## 🗺️ Roadmap

- **Veldt** — a full local + self-hosted music player (local files first, then OpenSubsonic / Jellyfin) that bundles Veldt Wisp as its built-in pill.
- True audio-reactive motion (Visualizer API — make the waves dance to the bass).
- Per-app blocklist.

## 🤝 Contributing

Issues and pull requests are welcome. If you're reporting a bug, please include
your device, Android version, and the media app you were playing — behavior
varies a lot across OEM skins and MediaSession implementations. For changes,
keep the build clean (`./gradlew assembleDebug`) and match the existing
Compose/Kotlin style.

## 📄 Credits & license

Licensed under the [MIT License](LICENSE).
