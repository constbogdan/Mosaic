# Stage C local upstream integration

## Authenticated scope and boundary

- Protected Mosaic main: `b8cf05293dbbddc3d6c8473426213b9a696a5f59`.
- Stage B upstream boundary: `55960c24625ef03bc5ac71b58806efdd613ffcc7`.
- Exact Stage C target: `621686d8e2785b0944f3dc730f84f8c0fa7d7356`.
- Observed upstream main: `bd46ff3e03c9a9f100fefcc2554be7c245a08c68`.
- Branch `chore/upstream-stage-c`, worktree `C:\Projects\Wholphin\mosaic-upstream-stage-c`.
- Pending native parents, in order: [protected main, Stage C target]. No final merge commit,
  push, PR update or publication is authorized by this task.
- Stage B is merged through PR #115. Its protected-main CI completed Full validation:
  <https://github.com/constbogdan/Mosaic/actions/runs/42231256780>.
- PR #114 remains the independent Draft for the later range, with head
  `b2c90b95a86031d60ef19a10e39215d9e909a887`. Its branch and evidence are untouched.
- The primary local main checkout stays at `8c800002`; the Stage B worktree stays at
  `32b37b83`. Fetching authenticated main does not authorize switching either checkout.

Exactly two incoming commits:

1. `3ef221bf910ddf00351df04592b0b066a4ecdffd` — Improve track persistence for series & episodes (PR 1802).
2. `621686d8e2785b0944f3dc730f84f8c0fa7d7356` — Include image tags in item image URLs (PR 2031).

Stage C is an ancestor of the observed upstream main. No later upstream commit is merged.
The original incoming delta is 38 paths, 3,016 additions and 278 deletions.

## Actual conflicts and semantic review

Three paths conflicted:

| Path | Combined resolution |
| --- | --- |
| `data/AppDatabase.kt` | Preserve integrity entity/DAO/converters and old migrations; add track entity/DAO and Mosaic version 37. |
| `services/hilt/DatabaseModule.kt` | Preserve `mosaic` database and manual migration; provide both DAOs. |
| `test/ui/TestModule.kt` | Preserve in-memory Mosaic test wiring and provide both DAOs. |

The remaining 35 incoming paths were also reviewed, not accepted solely because Git merged
them cleanly. Repository, stream selection, upgrade handling, playback/view-model consumers,
DI/test helpers and image-owner/tag forwarding are the relevant semantic overlaps. Shared
resources add the upstream selection explanation strings without replacing Mosaic resources.

## Schema and upgrade contract

Mosaic schema 36 already contains `season_integrity_expectations`; upstream's schema 40 does
not. Installed Mosaic databases must not be opened using upstream's incomplete schema export.
Stage C uses the next Mosaic version, **37**, and replaces the incoming 40 export with KSP's
generated 37 export. All previously exported Mosaic schemas remain unchanged.

The supported migration graph remains:

```text
2 --manual--> 3 -> 4 -> 5 -> 6 -> 7 -> 8 -> 9 -> 10 -> 11 -> 12
  -> 20 -> 30 -> 31 -> 32 -> 33 -> 34 -> 35 -> 36 -> 37
```

Every edge after 2-to-3 is Room-generated. The 36-to-37 edge adds
`SeriesTrackChoice` with primary key `(userId, parentId, type)` and a cascading foreign key
to `users.rowId`. Its remaining columns are `parentType`, `itemId`, `language`, `activation`,
`trackFlags`, `codec`, `relativeTrackIndex`, `title` and `audioChannels`. UUIDs retain Mosaic's
dashless converter; Room's enum conversion stores the upstream enum names. No new app-wide
converter or destructive fallback is introduced. All 12 old entity definitions, including
integrity indexes and converters, remain identical in the generated export.

Schema identity hashes:

- 36: `ec1d2505bdedd948527ea4dadc0010ef`.
- 37: `57cc12dbab9f37cb933a256611377430`.

`Migrations.Migrate36to37` is the auto-migration post-step. It copies legacy audio/subtitle
language preferences as `DEPRECATED` parent choices inside Room's schema-upgrade transaction,
retains source item IDs and user ownership, and preserves disabled subtitles even without a
language. Nullable/empty preferences create no unsupported new choice. `INSERT OR IGNORE`
makes the conversion repeat-safe and does not replace a newer choice. The old
`PlaybackLanguageChoice` table and all other existing tables remain.

The incoming AppUpgradeHandler conversion was rejected: its upstream 1.0.10 threshold would
skip independent Mosaic versions, and a global non-empty count could silently skip rows after
an interrupted batch. Room migration, rather than app startup/version allocation, owns this
conversion. Stage B's independent session/HDR upgrade handling remains unchanged.

Fresh installs create both new track persistence and existing integrity persistence directly.
No production database is read, copied, reset or upgraded during this task.

## Track and image behavior

Track choices are scoped to a persisted Jellyfin user row and stored independently for audio
and subtitles at season/series level. Existing item choices take precedence; season choices
precede series choices; ordinary profile/default selection remains the fallback. Relative
indexes, language, forced/default/SDH/external/signs flags, title, codec and channel metadata
are retained for matching across episodes. Disabled/only-forced subtitle choices and selection
explanations are integrated. Clearing selected streams uses the new persistence while keeping
per-item playback and track modifications intact.

Two incoming swapped-type lookups were reproduced and corrected: StreamChoiceService's audio
fallback queried SUBTITLE choices, while PlaybackViewModel loaded AUDIO choices for subtitles.
Regressions in the existing track-choice and playback suites fail before those two-line fixes.
The incoming test named for audio season-to-series fallback called the subtitle method;
it now exercises the real audio method instead. Blank legacy languages remain profile fallback,
not an activated wildcard choice, matching Mosaic's previous selection behavior.

Image URLs include the correct owning item's cache tag for own/series/parent primary, logo,
thumb and backdrop images, plus music, people, Live TV and studio callers. Missing or
mismatched parent tags are omitted; existing image fallback and Mosaic UI remain.

Home Acquiring/reload, session restoration, acquisition/Downloads, Missing Seasons request
selection and inactive-Seerr cleanup, cached expectations, playability, feature gates and
Mosaic signing/update identities remain in place. No Request badge or Stage D UI is added.

## Complete changed-file inventory against protected main

The staged integration has 43 paths (the 38-path upstream delta plus reviewed Mosaic
schema/testing/documentation adaptations, with AppUpgradeHandler unchanged from main).

```text
M	app/build.gradle.kts
A	app/schemas/com.github.damontecres.wholphin.data.AppDatabase/37.json
M	app/src/main/java/com/github/damontecres/wholphin/data/AppDatabase.kt
M	app/src/main/java/com/github/damontecres/wholphin/data/ItemPlaybackRepository.kt
M	app/src/main/java/com/github/damontecres/wholphin/data/PlaybackLanguageChoiceDao.kt
A	app/src/main/java/com/github/damontecres/wholphin/data/SeriesTrackChoiceDao.kt
M	app/src/main/java/com/github/damontecres/wholphin/data/model/Person.kt
M	app/src/main/java/com/github/damontecres/wholphin/data/model/PlaybackLanguageChoice.kt
A	app/src/main/java/com/github/damontecres/wholphin/data/model/RelativeMediaStream.kt
A	app/src/main/java/com/github/damontecres/wholphin/data/model/SeriesTrackChoice.kt
M	app/src/main/java/com/github/damontecres/wholphin/services/HomeSettingsService.kt
M	app/src/main/java/com/github/damontecres/wholphin/services/ImageUrlService.kt
M	app/src/main/java/com/github/damontecres/wholphin/services/MusicService.kt
M	app/src/main/java/com/github/damontecres/wholphin/services/StreamChoiceService.kt
M	app/src/main/java/com/github/damontecres/wholphin/services/hilt/DatabaseModule.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/components/ContextMenu.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/components/Dialogs.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/components/StudioCardGrid.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/components/TitleOrLogo.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/components/VideoStreamDetails.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/detail/DebugPage.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/detail/PersonPage.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/detail/episode/EpisodeViewModel.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/detail/livetv/LiveTvViewModel.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/detail/movie/MovieViewModel.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/detail/series/SeriesViewModel.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/playback/PlayExternalPage.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/playback/PlaybackViewModel.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/slideshow/SlideshowViewModel.kt
M	app/src/main/res/values/strings.xml
A	app/src/test/java/com/github/damontecres/wholphin/data/AppDatabaseMigrationTest.kt
A	app/src/test/java/com/github/damontecres/wholphin/data/model/RelativeMediaStreamTest.kt
A	app/src/test/java/com/github/damontecres/wholphin/services/ImageUrlServiceTest.kt
M	app/src/test/java/com/github/damontecres/wholphin/services/StreamChoiceServiceTest.kt
A	app/src/test/java/com/github/damontecres/wholphin/test/TestSeriesTrackChoice.kt
A	app/src/test/java/com/github/damontecres/wholphin/test/TestTrackFlag.kt
M	app/src/test/java/com/github/damontecres/wholphin/test/TestUtils.kt
M	app/src/test/java/com/github/damontecres/wholphin/ui/TestModule.kt
M	app/src/test/java/com/github/damontecres/wholphin/ui/playback/PlaybackViewModelTests.kt
M	docs/CODEX_HANDOFF.md
M	docs/UPSTREAM_RESOLUTION_DECISIONS.md
M	docs/history/README.md
A	docs/history/UPSTREAM_STAGE_C.md
```

## Validation evidence and limitations

Local validation on 2026-11-06 (not hosted integration acceptance):

- Production/test Kotlin compilation passed with Gradle 9.8.1 and Kotlin 2.4.21.
- Focused regressions: **127 tests in 15 suites**, zero failures/errors/skips. XML reports
  are retained in `.logs/stage-c/focused/`; command output in `focused-verified.log`.
- Complete default-debug JVM suite: **695 tests in 79 suites**, zero failures/errors/skips,
  compared with Stage B's recorded 652 tests. Command:
  `gradlew.bat :app:compileDefaultDebugKotlin :app:testDefaultDebugUnitTest`.
  Evidence: `.logs/stage-c/complete-jvm.log` and `.logs/stage-c/complete/` XML reports.
- Seven new native-SQLite Room tests cover fresh creation, all 19 exported historical schema
  versions (2 through 36), latest/35 upgrades, exact old-table data retention, disabled/null/blank
  settings, repeat-safe conversion, full new-choice metadata, season precedence, per-user
  isolation and foreign-key cascades. Each opened database passes `integrity_check` and
  `foreign_key_check`. The latest-schema test snapshots every existing table, including saved
  user preferences/authentication, playback/index/delay choices, integrity expectations,
  remembered tabs, display settings and Seerr relationships.
- Existing track/playback suites add three local regressions for disabled-language conversion
  and the two incoming swapped-type lookups. The inherited audio fallback test now calls the
  audio method. Incoming relative-stream, flag, scoring and image-owner/tag tests remain.
- Applicable offline tooling: **68 passed** (identity 3, validation policy 29, version 11,
  delivery 18, APK verifier 7). The initial combined command used the wrong verifier module
  name: its 61 actual tests passed; the corrected verifier invocation passed all seven.
  Evidence: `.logs/stage-c/offline.log` and `offline-apk-verifier.log`.
- Changed-file pre-commit/ktlint and whitespace checks passed. Initial hook autofixes were
  reviewed before final compilation/testing. Intermediate fixture-construction errors were
  corrected, not skipped; the final suites above are green.
- Repository Fast **passed**, including changed-scope pre-commit, the same 127-test focused
  graph (eligible cached results reused) and whitespace checks. Evidence:
  `.logs/validation/20261106-175747-16468/summary.txt` and its adjacent stage logs.
  An initial Fast launch lacked ANDROID_HOME and stopped before tests; setting the existing
  SDK path for that process corrected it without source/tooling or persistent environment edits.
  No local Full command or APK assembly is part of this validation.

```powershell
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
.\scripts\validate-local.ps1 -Level Fast -TestFilter @('*AppDatabaseMigrationTest', '*TestSeriesTrackChoice', '*TestTrackFlag', '*RelativeMediaStreamTest', '*StreamChoiceServiceTest', '*ImageUrlServiceTest', '*SeasonIntegrityTest', '*SeasonIntegrityExpectationTest', '*PlaybackViewModelTests', '*AppUpgradeHandlerTest', '*ServerRepositoryTest', '*UserSwitchListenerTest', '*HomeViewModelTest', '*SeriesRequestStateTest', '*RequestSeasonRulesTest')
```

No connected device or running emulator was reported by `adb devices -l`. A configured TV
AVD is not a running test target. No APK is built, signed, installed or published in this task.
JVM migration coverage uses synthetic exported-schema databases with real Room and native
SQLite under Robolectric; it is not Android device instrumentation acceptance. Physical-device
upgrade, multi-user/server track persistence, forced-subtitle playback, focus/dialog rendering
and live image-cache invalidation still require operator acceptance. Optional FFmpeg/AV1/MPV
native libraries are unavailable locally; compilation uses the repository's MPV stub.

Upstream's use of `BaseItemDto.parentId` for parent-level track preferences is retained. It
does not require an Episode kind: a non-episode DTO supplying a parent ID can participate in
parent-level choices. Live Jellyfin metadata and non-episode parent scope have not been accepted
on a device; do not infer that all parent IDs are season IDs from the DAO/model names alone.
