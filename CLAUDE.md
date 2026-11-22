# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

JavAfro plays West African drum rhythms written in YAML. There are **two independent players** that share data but no code:

- **Desktop**: Java 25, Swing, RSyntaxTextArea editor, Java Sound (mono PCM), SnakeYAML. Sources are in `src/main/java/picosoft/javafro`.
- **Web**: React 19 + TypeScript + Vite, CodeMirror 6, Web Audio API, an offline PWA with no backend. Sources are in `web/src`.

The two players share the rhythm library (`rhythms/`), the app config (`javafro.yaml`), the WAV samples, and the sample manifest `src/main/resources/samples/afroplayer-samples.yaml`. **The YAML parser, the pattern/timing logic and the note→sample mapping are implemented separately in Java and TypeScript.** Any change to the rhythm format, the note syntax, the manifest or the timing rules has to be made on both sides and tested on both sides.

All documentation, UI strings and task notes are in **Slovak**. Write new user-facing text and doc updates in Slovak. Commit messages are in **English** (older commits may be in Slovak). Detailed docs:
- `docs/TECHNICKA_DOKUMENTACIA.md`: architecture, class responsibilities, playback flow, and a table of "where to change X". Keep it in sync when the architecture changes.
- `README.md`: the user-facing YAML format, the note tables for each instrument, config keys, and packaging.
- `web/README.md`: web build, versioning, PWA/offline behavior, and phone install.
- `docs/tasks/TODO.md`: the task backlog. New task files follow `docs/tasks/_sablona.md`.

## Commands

Java (from the repo root; `JAVA_HOME` must point to JDK 25):

```powershell
mvn test                                        # all Java tests (JUnit 4)
mvn test -Dtest=RhythmLoaderTest                # single test class
mvn test -Dtest=RhythmLoaderTest#methodName     # single test method
mvn package                                     # target/JavAfro.jar (shaded) + target/JavAfro-obfuscated.jar (ProGuard)
java -jar target/JavAfro.jar "rhythms/basicRhythms.yaml"
java -Djava.awt.headless=true tools/ObfuscationSmokeCheck.java   # verify the obfuscated JAR
.\build-windows.ps1                             # jpackage portable app -> target/windows/JavAfro/
```

Web (run from `web/`; Node 20.19+ or 22.12+):

```powershell
npm ci
npm run dev                      # syncs assets, then starts Vite on :5173 (also bound to LAN)
npm test                         # vitest run (all)
npx vitest run src/timing.test.ts            # single file
npx vitest run -t "test name substring"      # single test by name
npm run build                    # tsc -b + vite build + offline service worker -> web/dist
npm run build:obfuscated         # the build that the GitHub Pages deploy uses
npm run phone                    # build + local HTTPS server for installing on an iPhone
```

Import-tool tests: `node --test tools/import-afroplayer.test.cjs`. They require `web/node_modules`, so run `npm ci` in `web` first.

CI (`.github/workflows`) runs `npm ci`, `npm test` and a build for the web app only. Java tests are not run in CI. The web build needs the full git history, because the 4th version number is `git rev-list --count HEAD`.

## Architecture notes that span multiple files

- **Shared assets are copied, not referenced.** `web/scripts/sync-assets.mjs` runs on `predev` and `prebuild`. It copies `rhythms/`, `src/main/resources/samples/` and the icons into the git-ignored `web/public/generated/`, and builds `rhythms.json`. Edit the source directories, never `web/public/generated`.
- **Versioning.** The version comes from `pom.xml` `<version>` for both apps. Java reads it through the Maven-filtered `app-info.properties`. The web app reads it through `web/scripts/build-version.ts` and appends the commit count. On a release, also bump `web/package.json` and `web/package-lock.json`.
- **Config.** `javafro.yaml` is validated strictly: unknown keys, duplicate keys and out-of-range values throw. Java validates it in `config/AppConfig.java`, the web app in `web/src/config-schema.ts`. A new setting needs both, plus tests. Java looks for the file in the working directory, then next to the JAR, then next to the jpackage launcher, then in the bundled resource. The web app picks it up at dev/build time.
- **Instrument mapping.** The YAML track names are mapped in Java `RhythmLoader.instrument()` (a switch statement) and in the web `instruments` list in `web/src/rhythm.ts`. Note letters are track-specific: for example `D/d` is valid only on dundun, and `x` is always the bell. Note validity is defined by each instrument's `supportsNote()` in Java and by `supportsNote()` in `web/src/sounds.ts`. Manifest-driven instruments extend `AfroSampleInstrument` (in `instruments/sample/matesamples`). The web note keyboard (`NotePad.tsx`) gets its notes from `notesForInstrument()` in `sounds.ts`; the desktop one (`ui/NotePad.java`) from `RhythmLoader.notesFor()` / `Instrument.notes()`, with the track under the caret from `RhythmLoader.instrumentAtCaret()` (a port of `note-context.ts`). Both list notes in manifest order, and the manifest map must stay ordered. Multi-character note instruments need special handling in `Editor.tsx` (`insertNote(..., activeInstrument === 'balafon')`) so that spaces are inserted automatically.
- **Instrument vs. voice.** The sequencer calls `instrument.voice(note, humanize)` once per hit. The voice fixes the sample, gain and rate for the whole decay. A `null` voice means a probabilistic note was skipped.
- **Pattern syntax.** A pattern without spaces uses one character per step. If the pattern contains any space, tokens are whitespace-separated, which allows multi-character notes such as balafon `c1`. `.` is a rest, and `|` is a bar separator that takes no time. A bar segment of exactly half the bar length is played twice (`TimeSignature.expandPattern()` in Java; the parser does it in the web version). Shorter tracks loop within the longest one.
- **Live playback.** Java `BeepSequencer.playContinuously()` blocks the calling thread and runs in a `SwingWorker`. `PlaybackSettings` (volatile snapshots plus a `ConcurrentHashMap` of track mixes) is shared with the EDT. `stop()` is final for that instance, so create a new sequencer for the next playback. Pressing Play while a rhythm is playing *queues* the new rhythm at the end of the current pattern with no gap. The web `AudioEngine` (`audio.ts`) schedules ahead with `planBeat()` (`timing.ts`) against the `AudioContext` clock, and uses generation counters to discard stale starts.
- **Mixer scale pitfall.** The UI and config track volume uses 0–100, but the engine uses 0–200 internally (UI value × 2, so 50% = gain 1). Master uses 0–100 directly (default 25). The mix is divided by the total track count, including muted tracks.
- **Testability hooks.** Java exposes `BeepSequencer.LineFactory` (fake audio line) and the `RhythmPlayerPanel.PlaybackSession` factory. The web timing functions accept injected randomness. Use these instead of real audio output in tests.
- **Desktop draft recovery** (`io/DesktopDraftStore`, `%USERPROFILE%\.javafro\desktop-draft.yaml`) is enabled only by the launcher (`enableDraftRecovery()`), so tests and standalone panels never touch user storage.
- **Saving** rewrites only the BPM and dialect of the selected rhythm, through YAML node models. Comments, other rhythms and `legacy` archive data are preserved (Java `io/RhythmExport`, web `rhythm-export.ts`). Mixer and humanize settings are never written to the YAML.
- **Desktop look.** FlatLaf Light themed in `ui/SandTheme.java` with the web palette from `web/src/style.css`. It needs native access (`Enable-Native-Access` in the shaded manifest, `--enable-native-access` in `build-windows.ps1`) and a `-keep class com.formdev.flatlaf.**` ProGuard rule. Tests run without FlatLaf installed, so theme helpers must not depend on it.
- **Obfuscation.** ProGuard rules are in `proguard.pro`. Library names are kept for reflection and resource loading. Keep `target/proguard-mapping.txt` private. Web obfuscation settings are in `web/scripts/obfuscate.ts`.

## Rhythm library conventions

- Each file in `rhythms/imported/` contains one rhythm, and its top-level key must exactly match the file name without `.yaml`. `ImportedRhythmsTest` and the web tests load every shared YAML file.
- Only `tracks` is required. A missing `bpm` falls back to `playback.tempo.initial`, `timeSignature` defaults to `"4/4"`, and `stepsPerBeat` defaults to 2 (3 for compound x/8 meters). `dialect` must have exactly `stepsPerBeat` values in the range −1..1.
- `legacy` holds archived source data from the original AfroPlayer (Ruby) import and does not affect playback. `tools/import-afroplayer.cjs` regenerates the imports and overwrites them.
