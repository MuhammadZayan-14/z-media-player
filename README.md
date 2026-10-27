# Resonance

A local music and video player for Android: Kotlin, Jetpack Compose (Material 3), Media3, Room,
DataStore and Hilt, in a modular Clean Architecture. Everything stays on the device; the app has
no internet permission.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for module boundaries, the playback lifecycle,
data flow, and the decisions behind them.

## Features

**Library.** Songs, albums, artists, genres, folders and videos, indexed from MediaStore and kept
in sync incrementally (generation-based on Android 11+). Full-text search across title, artist
and album with filters. Five song sort orders and a letter fast-scroll. Pull to refresh. Hides
duplicates, very short clips and non-music audio by default (all configurable). Missing tags,
missing artwork, deleted and corrupt files are handled.

**Audio.** Background playback through a `MediaSessionService`, with notification, lock-screen,
Bluetooth and wired-headset controls. Audio focus and pause-on-unplug. Shuffle, repeat
off/all/one, speed 0.5×–2×, skip silence. Queue view with play next, add, remove and reorder.
The last session (queue, position, modes) is restored on launch, and headset play resumes it
after a reboot.

**Video.** Full-screen immersive player: seek, ±10 s double-tap, brightness and volume drags,
speed, fit/zoom/stretch, rotation, picture-in-picture with a play/pause action. Resumes where you
left off. Opens `content://` videos shared from other apps.

**Collections.** Playlists (create, rename, delete, add, remove, reorder), favourites, recently
played, most played, recently added. A home dashboard with continue listening/watching.

**Privacy and access.** Requests only audio/video read access, per Android version, including
Android 14's "selected videos only". Single files can be played with no permission at all via
"Open a file".

## Requirements

| Tool | Version |
|---|---|
| JDK | 21 (Gradle runs on it; bytecode targets 17) |
| Android SDK | Platform 37 (`platforms;android-37.0`), Build-Tools 37.0.0 |
| Device | Android 10 (API 29) or later |

The Gradle wrapper (9.8.2) downloads itself. Library versions are in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Build and run

```bash
# Point the build at your SDK (or set ANDROID_HOME)
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

./gradlew assembleDebug              # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug               # install on a connected device or emulator
./gradlew :app:assembleRelease       # minified with R8; unsigned unless configured below
```

The debug build installs as `app.resonance.debug`, so it can sit beside a release build.

### Release signing

Signing material is never committed. Provide it in an untracked `keystore.properties` at the
repository root:

```properties
storeFile=/absolute/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

or through the environment variables `RESONANCE_KEYSTORE`, `RESONANCE_KEYSTORE_PASSWORD`,
`RESONANCE_KEY_ALIAS` and `RESONANCE_KEY_PASSWORD` (for CI secrets).

## Tests and checks

```bash
./gradlew testDebugUnitTest :core:domain:test   # unit, Robolectric and Compose UI tests (JVM)
./gradlew :app:connectedDebugAndroidTest        # instrumented tests on a device/emulator
./gradlew lintDebug                             # Android Lint
./gradlew ktlintCheck                           # formatting (ktlintFormat to fix)
```

| Layer | What is tested | Where |
|---|---|---|
| Use cases | session restore with missing files, playlist name rules, search filters, home feed, shuffle | `core/domain/src/test` |
| Room | visibility rules, aggregation, FTS, playlists, history ranking and pruning, session tables, exported-schema validation | `core/database/src/test` |
| Data | incremental/full sync, deletions, provider failure, revoked access, MediaStore rebuilds; repository filtering, search, ordering, playlist reorder with missing files, video-progress thresholds | `core/data/src/test` |
| Preferences | defaults, round trips, unknown values, sync bookkeeping | `core/datastore/src/test` |
| Permissions | per-API permission sets, partial and revoked access | `core/permissions/src/test` |
| Playback | Player → state mapping on a real ExoPlayer, error categories, session snapshot, play counting, error-skip policy | `core/media/src/test` |
| ViewModels | every feature ViewModel against in-memory fakes from `core:testing` | `feature/*/src/test` |
| Compose UI | mini and full player, playlists and name dialog, search states, music tabs, permission gate, home | `feature/*/src/test` (Robolectric) |
| Integration | real controller → MediaSession → service → ExoPlayer (play, pause, progress, queue edits, modes, skip); app navigation, search, playlist creation | `app/src/androidTest` |

Robolectric tests run against API 35 (`core/testing/src/main/resources/robolectric.properties`),
because Robolectric doesn't support API 37 yet.

## Static analysis and conventions

- Kotlin warnings fail the build (`allWarningsAsErrors`); `-Xjsr305=strict`.
- Android Lint runs with `abortOnError`; dependency-version checks are disabled because upgrades
  are deliberate catalog changes.
- ktlint 1.8 (Android Studio style) via the `org.jlleitschuh.gradle.ktlint` plugin; settings in
  `.editorconfig`.
- Debug builds enable StrictMode (thread and VM policies, logged).
- CI (`.github/workflows/ci.yml`): ktlint, lint, unit tests, debug and release builds, then the
  instrumented suite on an API 35 emulator.

## Project layout

```
app/                     Application, MainActivity, navigation host, app shell
build-logic/convention/  Gradle convention plugins (one per module kind)
core/
  common/                dispatchers, app scope, clock, logging, formatting
  model/                 domain models (pure Kotlin)
  domain/                repository + controller contracts, use cases (pure Kotlin)
  database/              Room entities, DAOs, FTS, schemas/ (exported, committed)
  datastore/             Preferences DataStore
  permissions/           permission sets and access evaluation
  data/                  repositories, MediaStore source, library sync
  media/                 PlaybackService, MediaController wrapper, video player factory
  designsystem/          theme, artwork loading, shared components
  ui/                    model-aware components, permission UI, file picker
  testing/               fakes, test data, rules, Hilt runner
feature/
  home/ music/ video/ player/ playlists/ search/ settings/
docs/ARCHITECTURE.md
```

## Known limitations

- **Shuffle order is not persisted.** A restored session keeps the shuffle flag, but the order is
  re-randomised.
- **Picker-opened files are not restored** after a restart (their URI grant doesn't survive), and
  they can't be favourited or added to playlists, since those reference MediaStore ids.
- **Library data is device-specific.** Playlists reference MediaStore ids, so backup/restore to
  another device is disabled rather than silently pointing at the wrong files.
- **Queue reordering uses move up/down actions**, not drag-and-drop. This was chosen for
  accessibility; drag handles could be added alongside.
- **No Android Auto browsing or Assistant voice search.** The service is a `MediaSessionService`,
  not a `MediaLibraryService`, so external controllers can control playback but not browse the
  library.
- **Genres need Android 11+.** MediaStore exposes the genre column from API 30. On Android 10 the
  Genres tab is empty.
- **No equaliser, crossfade, lyrics or ReplayGain.**
"# z-media-player" 
