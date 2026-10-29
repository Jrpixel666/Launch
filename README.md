# Pocket Shell

An Android launcher inspired by [CTLST Shell](https://github.com/ctlst/shell). It borrows the
shell's structure (Home pages, a dock, a searchable app drawer, Quick Settings and an in-launcher
Settings app) and rebuilds it for Android with plain framework APIs. There is no AndroidX and no
layout XML; the whole UI is built in code. Not affiliated with CTLST.

## Features

- **Home pages** with a 4 or 5 column grid, a page indicator and a dock.
- **Widgets page** left of Home: clock, date, live battery / free RAM / network chips and a month
  calendar (tap the title to change month or jump to today).
- **App drawer**: swipe up or tap the grid button. Search filters by name or package; Enter
  launches the first result, and no results means no launch.
- **Edit mode** in the drawer: hidden apps appear dimmed, and tapping an app hides or shows it.
  Hiding affects the drawer only, not an existing Home icon.
- **Long-press menu** on any app: add to or remove from Home, move to dock, move earlier or later,
  move between pages, hide, app info, uninstall.
- **Quick Settings**: swipe down. Tiles show current state and open the matching system panel.
- **Settings**: theme (dark, light, system), six accent colours, a Rounded/**Omarchy** style,
  Home and drawer columns, icon size, widgets page on or off, wallpaper, default-launcher
  shortcut, layout reset.
- First run puts your default phone, browser, messages and camera apps in the dock.
- **Omarchy style**: a flat, square-cornered, monospaced dark look (Settings → Appearance →
  Style, or ask the Agent to "switch to omarchy theme").
- **Agent bar**: swipe in from the left edge of Home (or long-press Home → Agent) for a command
  bar. It's a local, rule-based dispatcher — no network call, no model — that runs built-in
  actions ("install arch", "terminal", "linux window", "dark mode", "reset layout", …), ranks your
  most-used apps, and falls back to fuzzy app search for anything else.
- **Arch Linux ARM, no root**: Settings → Linux → Install downloads the official Arch Linux ARM
  rootfs and a static `proot` binary and unpacks them into the app's private storage. `proot`
  intercepts syscalls so the rootfs believes it's really rooted, with no device root required.
- **Arch terminal**: a line-buffered shell into the rootfs (no native PTY yet, so full-screen
  terminal apps like `vim` won't repaint correctly, but `pacman`, scripts and most CLI tools work).
- **Linux window**: runs an Xvfb + Openbox + x11vnc + websockify desktop inside the rootfs and
  shows it full-screen via a WebView running noVNC, so Linux GUI apps appear as a real window.
  Pinch to zoom; swipe down from the top edge to minimize back to Pocket Shell.

## Build the APK

### On GitHub (no local setup)

Push this folder to a repository. The workflow in `.github/workflows/build.yml` builds
`app-debug.apk` and attaches it to the run as the `pocketshell-debug-apk` artifact.

### Locally

Requires JDK 17 and the Android SDK (platform 34). Android Studio sets both up.

```sh
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is signed with the debug key, which is fine for sideloading. Sign your own release
build before distributing it.

## Use it

Install the APK, press Home, and choose **Pocket Shell** as your default home app (or open
Settings, then System, then Default home app).

## Known limits

- No drag and drop yet. Rearranging uses the long-press menu.
- Android doesn't let a launcher open the system Recents screen or toggle Wi-Fi, Bluetooth and
  similar radios, so Quick Settings tiles open system panels instead of switching directly.
- Work-profile apps and app shortcuts are not shown.
- Source compiles against the Android 34 API; it has not been run on a device or emulator.
  Visual spacing and gesture feel need checking on real hardware.
- The Arch Linux install needs network access on-device and a 64-bit ARM (`arm64-v8a`) CPU; other
  ABIs fall back to a proot binary name that may not exist. The rootfs download is ~600 MB over
  plain HTTP (the Arch Linux ARM project doesn't serve it over HTTPS), and the Linux window loads
  noVNC from a CDN at runtime rather than bundling it.
- The Arch session (proot, Xvfb, x11vnc, websockify) runs as plain child processes of the app, not
  a foreground service, so it is only guaranteed to survive while Pocket Shell's process is alive.
- The terminal has no real PTY, so curses-style full-screen apps won't render correctly.
