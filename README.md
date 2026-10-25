# SkillVerse

A gamified, offline-first Android app that turns career learning for software and automation
engineers into short daily missions: coding exercises, quizzes, debugging puzzles, a career skill
tree, XP and levels, streaks, weekly bosses and achievements.

- **Platform:** Android 8.0+ (minSdk 26, target/compile SDK 35)
- **Stack:** Kotlin · Jetpack Compose · Material 3 · MVVM + repositories · Room · DataStore · WorkManager · Navigation Compose · kotlinx.serialization
- **Cost / network:** no backend, no accounts, no paid APIs, no network permission. Everything runs on the device.

The 100 bundled challenges are written from four study handbooks (Python Automation Engineer,
API & Protocol Automation, Linux/Bash/Embedded Debugging, Git/Jenkins/CI-CD). Every challenge
cites the handbook chapter it draws on.

---

## Features

| Area | What it does |
|---|---|
| **Home — Mission Control** | Avatar, level, XP bar (earned / remaining), daily streak, weekly goal, today's 3 missions with progress, *Continue learning*, recent achievements, weekly boss status. All values come from the database. |
| **Play — Challenge Arena** | 9 categories (Python, logic, debugging, test automation, API testing, Linux, Git, embedded/firmware, DevOps/CI-CD). Six challenge types: multiple choice, predict the output, fill in the blank, identify the bug, arrange the steps, small coding puzzles. Optional **timed mode**. Review queue and weekly boss entry points. |
| **Challenge player** | Description, difficulty, duration, instructions, code with line numbers, test cases, progressive hints, submission feedback, *Reveal the answer* (only after a submission), explanation with handbook source, retry, next. Completed challenges can be replayed (XP is never granted twice). |
| **Skills — Career Skill Tree** | 10 nodes from Python Foundations to Advanced Projects. Each node shows state, mastery %, prerequisites, its challenges and exactly how to unlock it. |
| **Progress — Learning Analytics** | Total XP/level, attempted vs completed, accuracy, best/current streak, weekly goal ring, 14-day activity chart, skill mastery bars, weak topics, recommended revisions, projects, badges. Zero-safe on a fresh install. |
| **Profile & settings** | Display name, emblem, daily and weekly goals, reminder on/off and time (with permission handling), Dark/Light/System theme, sound, haptics, reduced motion, privacy note, JSON export, reset with confirmation, diagnostics. |
| **Daily missions** | 1 coding exercise + 1 quiz + 1 debugging/scenario per local day (10–20 minutes), adapted to recent accuracy, avoiding questions seen in the last 7 days, generated deterministically and stored so they never change during the day. |
| **Weekly boss** | A 5-part run combining several concepts; one miss ends the run (retry any time). The +75 XP bonus is paid once per boss per week. |
| **Reminders** | Optional daily notification via WorkManager; one unique job, survives reboots, skipped when today's missions are done. |

## Progression system

All numbers live in one file: `app/src/main/java/com/skillverse/app/domain/progression/ProgressionConfig.kt`.

| Event | XP |
|---|---|
| Correct multiple-choice quiz | 10 |
| Coding challenge (predict output, fill blank, coding puzzle) | 25 |
| Debugging challenge (identify the bug, arrange the steps) | 30 |
| Weekly boss cleared | 75 (once per boss per week) |
| Skill reaches 100% mastery | 100 (once per skill) |

- **Levels:** going from level *n* to *n+1* costs `50n + 50` XP (100, 150, 200 …); reaching level *L* needs `25(L−1)(L+2)` XP in total. Max level 99.
- **No duplicate rewards:** every award is a row in `xp_transactions` with a **unique event key** (`challenge:<id>`, `boss:<week>:<id>`, `mastery:<skill>`). Re-submitting or reopening a completed challenge cannot add XP, even under concurrent taps. Mission completion, XP, mastery and achievements are written in one Room transaction.
- **Streaks:** a day counts when at least one challenge is answered correctly (device-local date). The current streak survives until the end of the next day; a missed day resets only the *current* streak. XP, levels, achievements and the best streak are never reduced.
- **Skill unlocks:** a node unlocks when every prerequisite reaches **60 %** mastery (configurable in `skills.json`). Mastery = distinct challenges in the node completed correctly ÷ challenges in the node.
- **Achievements:** First Mission, Python Explorer (10 Python), Debug Detective (10 debugging/logic), Five-Day Learner (5-day streak), Automation Apprentice (8 test-automation/API), Boss Challenger, Mastery Milestone. Granted only from persisted records, once.

## Curriculum content

Bundled in `app/src/main/assets/content/` as versioned JSON (`schemaVersion` 1, `contentVersion` 1):

| File | Contents |
|---|---|
| `manifest.json` | Lists the packs and support files |
| `pack_python.json` | 35 Python challenges |
| `pack_debugging_logic.json` | 20 debugging and programming-logic challenges |
| `pack_test_automation.json` | 15 pytest/test-automation challenges |
| `pack_api_testing.json` | 10 HTTP/Redfish API-testing challenges |
| `pack_linux_git.json` | 10 Linux and Git challenges |
| `pack_embedded_cicd.json` | 10 embedded/firmware and CI/CD challenges |
| `skills.json` | Skill-tree nodes, prerequisites, unlock threshold |
| `bosses.json` | 5 weekly bosses |
| `projects.json` | 5 projects mapped to the handbook projects |

Every challenge has a stable id, category, skill, type, difficulty, estimated duration (solving plus
reading the explanation), title, description, instructions, answer/validation spec, hints,
explanation, handbook source and prerequisites. XP is derived from the type via `ProgressionConfig`.

**Validation**
- `tools/validate_content.py` (run in CI) checks schema, ids, duplicates, references and category
  counts, and **executes every Python snippet that has a `verify` block** with a real Python
  interpreter (isolated mode, temp dir, timeout). 32 predict-the-output and coding answer keys are
  verified this way; every accepted answer must print the expected output.
- At startup the app validates content again (`ContentValidator.kt`) and rejects malformed items
  with diagnostics (shown under *Profile → About & diagnostics*) while keeping valid ones.

**Adding challenges:** edit the packs in `tools/curriculum/` and run
`python tools/curriculum/build.py app/src/main/assets/content`, or edit the JSON directly, then run
`python tools/validate_content.py`. The UI renders any item that follows the schema; no UI code changes are needed.

## Code execution and safety

The app **never executes user code**. `CodeExecutionService` is the abstraction (status, output,
errors, timeouts, `executedCode` flag). The shipped `LocalExerciseValidationService` compares answers
with the content's deterministic rules (`EXACT`, `TRIMMED`, `NORMALIZED`, `CASE_INSENSITIVE`, `CODE`)
and the UI says so on every coding challenge. A future remote Python backend can implement the same
interface; it must run server-side with process isolation, CPU/memory limits, timeouts, no network
and no access to secrets or host files.

## Project structure

```
SkillVerse/
├─ app/
│  ├─ build.gradle.kts            # Android, Compose, Room (KSP), tests
│  ├─ schemas/                    # Exported Room schemas (generated on build)
│  └─ src/
│     ├─ main/
│     │  ├─ assets/content/       # Versioned curriculum JSON
│     │  └─ java/com/skillverse/app/
│     │     ├─ SkillVerseApplication.kt, MainActivity.kt, AppContainer.kt (manual DI)
│     │     ├─ core/designsystem/ # Theme, colour tokens, typography, spacing, components, icons
│     │     ├─ core/time/         # TimeProvider (local-date boundary, timezone-safe)
│     │     ├─ data/content/      # JSON DTOs, loader, startup validator
│     │     ├─ data/db/           # Room entities, DAOs, database + migrations hook
│     │     ├─ data/progress/     # Transactional ProgressRepository, export
│     │     ├─ data/settings/     # DataStore preferences
│     │     ├─ domain/model/      # Challenge, Curriculum, enums
│     │     ├─ domain/missions/   # Deterministic daily mission + weekly boss selection
│     │     ├─ domain/progression/# XP config, levels, streaks, achievements
│     │     ├─ domain/skills/     # Skill tree states, mastery, unlock hints
│     │     ├─ domain/analytics/  # Accuracy, weak topics, activity series
│     │     ├─ domain/validation/ # AnswerValidator, CodeExecutionService
│     │     ├─ notifications/     # Reminder scheduler, worker, channel
│     │     └─ ui/                # navigation, home, play, challenge, boss, skills, progress, profile, onboarding
│     └─ test/                    # JVM + Robolectric unit, database and Compose UI tests
├─ tools/
│  ├─ validate_content.py         # Content validator (executes Python answer keys)
│  ├─ ci_report.py                # CI summary/APK collector
│  └─ curriculum/                 # Authoring sources for the JSON packs
├─ .github/workflows/android.yml  # Build + tests on every push
├─ gradle/libs.versions.toml      # Dependency versions
└─ gradlew, gradlew.bat
```

## Windows setup

**Prerequisites**
1. **Android Studio** Ladybug (2024.2) or newer: <https://developer.android.com/studio>. It bundles a suitable JDK (17+).
2. In Android Studio: *Settings → Languages & Frameworks → Android SDK* → install **Android 15 (API 35) SDK Platform** and **Android SDK Build-Tools 35**.
3. **Git for Windows**: <https://git-scm.com/download/win>
4. Optional, for the content validator: **Python 3.10+** from <https://www.python.org/downloads/windows/>

**Get the code**
```powershell
git clone https://github.com/basavarajkumbar-dev/SkillVerse.git
cd SkillVerse
```

**Open in Android Studio:** *File → Open →* select the `SkillVerse` folder and let Gradle sync (it downloads Gradle 8.14.3 and dependencies the first time).

**Command line (PowerShell)** — point Gradle at the JDK and SDK if they are not already on your path:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
```
Or create `local.properties` in the project root with:
```
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

## Build and test commands

| Task | Windows | macOS / Linux |
|---|---|---|
| Debug APK | `.\gradlew.bat assembleDebug` | `./gradlew assembleDebug` |
| Unit, database and Compose UI tests (JVM/Robolectric) | `.\gradlew.bat testDebugUnitTest` | `./gradlew testDebugUnitTest` |
| Build + tests together | `.\gradlew.bat assembleDebug testDebugUnitTest` | `./gradlew assembleDebug testDebugUnitTest` |
| Install on a connected phone | `.\gradlew.bat installDebug` | `./gradlew installDebug` |
| Release APK (needs signing, see below) | `.\gradlew.bat assembleRelease` | `./gradlew assembleRelease` |
| Validate curriculum | `python tools\validate_content.py app\src\main\assets\content` | `python3 tools/validate_content.py app/src/main/assets/content` |

- Debug APK output: `app\build\outputs\apk\debug\app-debug.apk`
- Test report: `app\build\reports\tests\testDebugUnitTest\index.html`

**Release signing:** create `keystore.properties` in the project root (it is git-ignored):
```
storeFile=C:/keys/skillverse-release.jks
storePassword=...
keyAlias=skillverse
keyPassword=...
```
Without it, `assembleRelease` produces an unsigned, minified APK.

## Continuous integration

`.github/workflows/android.yml` runs on every push to `main`: content validation, `assembleDebug`
and `testDebugUnitTest` on GitHub's Ubuntu runners. The debug APK and test results are uploaded as
the `skillverse-ci` artifact and also published to the `ci-reports` branch (`SUMMARY.md` has the
real counts parsed from the JUnit XML).

## Installing the APK on an Android phone

**Option A — USB with adb**
1. On the phone: *Settings → About phone →* tap **Build number** 7 times, then enable *Developer options → USB debugging*.
2. Connect the phone, accept the debugging prompt, then run `.\gradlew.bat installDebug` or
   `adb install -r app\build\outputs\apk\debug\app-debug.apk`
   (`adb.exe` is in `%LOCALAPPDATA%\Android\Sdk\platform-tools`).

**Option B — copy the file**
1. Copy `app-debug.apk` to the phone (USB file transfer, Google Drive, email to yourself).
2. Open it from *Files*; when asked, allow *Install unknown apps* for that app, then tap **Install**.
3. The app appears as **SkillVerse**. The debug build has the id `com.skillverse.app.debug`, so it can sit next to a future release build.

## Testing

`app/src/test` (runs on the JVM with Robolectric; no emulator needed):

- `ProgressionTest.kt` — level thresholds and progress, config values, streaks across missed days, month/year boundaries and future dates, week boundaries, achievement rules and no re-awards.
- `ValidationTest.kt` — every match mode, all six challenge types, timed-out answers, arrange-steps feedback, shuffled steps never start solved, **every bundled answer key validates**, `CodeExecutionService` statuses.
- `MissionsSkillsAnalyticsTest.kt` — balanced deterministic missions within 10–20 min, repeat avoidance, adaptive difficulty, boss eligibility; skill unlock thresholds and mastery; zero-safe analytics; reminder delay incl. daylight-saving gaps.
- `ContentRepositoryTest.kt` — the bundle loads cleanly with category targets; malformed items, broken packs, missing manifest and unknown schema versions are handled with diagnostics.
- `ProgressRepositoryTest.kt` (Room in memory) — XP once per challenge, revision queue, stable daily assignments, next-day rotation, mastery bonus once, level-ups, weekly boss paid once, streak and achievement persistence, timezone change, export and reset, category accuracy joins.
- `AppUiTest.kt` (Compose UI) — onboarding, bottom navigation, completing a mission, wrong answer + retry, level-up dialog, settings persistence, empty states, zero-safe analytics.

## Known limitations

- Coding challenges are validated by deterministic answer matching, not by running code. Puzzles state the expected form (variable names, idiom), and equivalent but differently written solutions may be marked incorrect.
- Reminders use WorkManager and are not exact-time; Android may defer them (Doze, battery saver, OEM restrictions).
- Compose UI tests run under Robolectric on the JVM. Instrumented tests on a real device/emulator are not part of CI.
- No cloud sync or account; progress moves between devices only via the JSON export (there is no import yet).
- Content is English-only. Release signing requires your own keystore.
- Room schema is at version 1; future schema changes must add a `Migration` in `SkillVerseDatabase.MIGRATIONS`.
