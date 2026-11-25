# L9 — Launcher on 9 keys

A home screen for Android phones with a keypad and no touchscreen: the number keys launch your apps.

[![Build](https://github.com/NineTailsFlip/l9/actions/workflows/build.yml/badge.svg)](https://github.com/NineTailsFlip/l9/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/NineTailsFlip/l9)](https://github.com/NineTailsFlip/l9/releases/latest)
[![License: GPL-3.0-or-later](https://img.shields.io/badge/license-GPL--3.0--or--later-blue)](LICENSE)

L9 is [NineTailsFlip](https://github.com/NineTailsFlip)'s fork of L9 from Timo Furrer's
[Nine suite](https://nine.furrer.life). It keeps upstream's look — one column of plain text on
black, a numbered `*` menu, a hint bar — and adds updates from GitHub and a Recents screen.

## Features

- Nine slots on the number keys, each linked to an app, renamed and reordered as you like.
- `0` browses every installed app, with search and hidden apps.
- Left and Right launch two shortcut apps of your choice.
- Speed dial: hold a digit, then press Call.
- Clock, calendar events and notification counts on the home screen.
- **Recent apps** (our addition): a Nine-style list of recent tasks — OK returns to one,
  Left or Delete closes it, `*` → Close all. On the
  [thor2 image](https://github.com/kloploid/android_vendor_thor2) L9 is the system's Recents
  component and the Recents key opens this screen.
- **Updates from GitHub Releases** (our addition), and the look chosen in the phone's
  first-time setup.

## Installation

Download the APK from [Releases](https://github.com/NineTailsFlip/l9/releases/latest) and
install it, then choose L9 as the home app. After that L9 checks for a new release once a day
and offers it; Android asks you to confirm each update. Settings → Updates turns the check off
or runs it now.

Our builds are `io.github.ninetailsflip.l9`, so they install next to the official L9
(`life.furrer.l9`) instead of replacing it.

## Usage

| Key | Does |
|---|---|
| Up / Down | Move the selection |
| OK | Launch the selected slot's app |
| 1–9 | Launch that slot directly |
| 0 | Browse every installed app |
| Left / Right | Launch the shortcut app; hold to reassign it |
| Hold a digit, then Call | Call the contact on speed dial |
| `*` | Menu for the selected slot: link app, rename, reorder, settings |
| Hold `*` | Settings |
| Back | Cancel, or leave reorder mode |

The full key map is in the app: Settings → About → Key mapping.

## Building

Requirements: JDK 17+, Android SDK (platform 34).

```bash
git clone --recursive https://github.com/NineTailsFlip/l9.git
cd l9
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
./gradlew :app:assembleDebug
```

Tests and lint: `./gradlew :app:testDebugUnitTest :app:lintDebug`.
`scripts/run.sh` installs the app on a connected phone and follows its log (it expects
`ANDROID_HOME=/opt/android-sdk`; edit it if your SDK lives elsewhere).

Releases are built by GitHub Actions from a `v<versionName>` tag — see
[RELEASING.md](https://github.com/NineTailsFlip/.github/blob/main/RELEASING.md).

## Project structure

| Path | What's there |
|---|---|
| `app/` | L9 itself (Kotlin package `life.furrer.l9`, kept from upstream so its changes merge cleanly) |
| `nine-core/` | Git submodule: [nine-core](https://github.com/NineTailsFlip/nine-core), the Nine suite's shared `core/` plus our `update/` |
| `scripts/` | `sync-upstream.sh`, `run.sh` |
| `fdroid/` | Upstream's reference F-Droid metadata (describes upstream's builds, not ours) |

## Staying in sync with upstream

This repository holds upstream's full history of `l9/` (from `~timofurrer/nine` at
`ab73f97`) with our changes on top.

```bash
scripts/sync-upstream.sh
```

merges what changed upstream since the last sync. When upstream changes `core/`, update
[nine-core](https://github.com/NineTailsFlip/nine-core) first, then bump the submodule here.

## Contributing

Issues and pull requests are welcome; see
[CONTRIBUTING](https://github.com/NineTailsFlip/.github/blob/main/CONTRIBUTING.md). If a bug
also happens in the official L9, please report it upstream too:
<https://todo.sr.ht/~timofurrer/nine>.

## License

GPL-3.0-or-later — see [LICENSE](LICENSE). L9 is © Timo Furrer and the Nine contributors; our
changes are under the same license.
