# Stage B local upstream integration

## Authenticated scope

- Mosaic protected main: `8c8000027fa92a4e4b6acb6c842325936356d00f`.
- Previous upstream anchor: `65954a99eb0aa318d9f88813d49768bd95cf37ee`.
- Exact Stage B target: `55960c24625ef03bc5ac71b58806efdd613ffcc7`.
- Observed upstream main: `bd46ff3e03c9a9f100fefcc2554be7c245a08c68`.
- Local branch: `chore/upstream-stage-b`; isolated worktree:
  `C:\Projects\Wholphin\mosaic-upstream-stage-b`.
- Native merge remains pending, with parents [protected main, Stage B target].
  No final merge commit, push, PR update, or publication is authorized.
- PR #114 remains the independent Stage D Draft, head
  `b2c90b95a86031d60ef19a10e39215d9e909a887`; its branch and evidence are untouched.
- Stage C track persistence/image tags and Stage D are excluded.

## Exact incoming inventory (37 commits)

- `a577ae9f1a0eea7403213bb6acdbccf3e7e812e0` — Add option to prefer Dolby Vision over HDR10+ on hybrid videos (#1876)
- `cf74736579f44c7d9e8715b6189454e503757ee9` — Listen for user config changes and improved websocket error handling (#2012)
- `b0d270380ddea2022e8ca18ecbf448ac6c5692ba` — Better home page header for Seasons (#2016)
- `559abe2458eb6c5e2b1ca732f943f0a6807a42bf` — Add setting to force transcoding above a max resolution (#1955)
- `f5dedeab815e5c8324b4215a06e540153e6dea70` — Fix baseline profile never being installed (#1984)
- `a38eb3e3d88d0d9ebb7b6200d0e9cd809a4ed45a` — Fix possible crash on a few pages due to color space (#2036)
- `b99f21efa8ab08a254edfb13170974f616cd631f` — Various bug fixes (#2010)
- `e23c446bb7a4d20c0f82a5f9d7d6fca9d6c3fc21` — Fix issue when scrolling on collection details page (#2048)
- `d6523ce856f7066c35bc9df8b514a8d6b876f06c` — Fixes restoring focus on the correct episode (#2051)
- `0e11b8ff6f3273b23714ea04337228f040c70126` — Translated using Weblate (Slovak)
- `22254bac326407e0e0994550c2813529a62e6aeb` — Translated using Weblate (Chinese (Traditional Han script))
- `c0eceba16c768914fa03ba89ee8c968ecffc773a` — Translated using Weblate (German)
- `29e235ffa5ce1528a3606e31852aa262feedb02b` — Translated using Weblate (Estonian)
- `0e0d50753bb8a1c1de8128603ec8df830362f1af` — Translated using Weblate (Portuguese)
- `121208e021b420eca0dca263722db48e54d96b9e` — Translated using Weblate (Turkish)
- `26d3e5b6fcb954373a3c1795db9316064d06e1c2` — Translated using Weblate (Russian)
- `e4c3257c13f72a71282ad43467e01558d137304b` — Translated using Weblate (Croatian)
- `b03325accb416b0388f2e4dde07df182c04b534e` — Translated using Weblate (English (United Kingdom))
- `4502a12dcb3f0f03b5b337cd343ae9a4e51c997a` — Translated using Weblate (Swedish)
- `ba5351f8dcb104149bf375a59f51b4717466a7b5` — Translated using Weblate (Swedish)
- `6308ad85a9ccb97794ae8027375bb9aea7ef6f28` — Translated using Weblate (Russian)
- `c879a5713f6df3eccfe4b76c68fe0cb5828a5d10` — Translated using Weblate (Chinese (Simplified Han script))
- `68c48f154cc7b8991c64914d6b2d4bb42477264b` — Translated using Weblate (Spanish)
- `f14e5a575697f47055108c95f31bba2716c1ba93` — Translated using Weblate (Spanish)
- `55d3254b1ea519bf35a6d4d196e307cf7354da78` — Translated using Weblate (Swedish)
- `db77aaa3a41785e25524f713927a5b0ee96296db` — Translated using Weblate (Polish)
- `1571591a1256c4776425e36aa9d1e01b3ee92732` — Translated using Weblate (Swedish)
- `9b028040754acc58253b414c2c33a3ef4ca92d00` — Translations 2026-10 -10 (#2054)
- `5bfbba193aa017c3e44f53e9ffea10ed5b4baa51` — Fix issue with home reloading on user switch (#2064)
- `2d87d526dbfabd94c5033e16d14c215b206e8b10` — Release v1.0.9
- `d0640f148c523c27ea936a4278df3a64af5e361b` — Update Kotlin to v2.4.21 (#2057)
- `56836f62035fc372272f2721778fcfb509704ecf` — Update actions/upload-artifact digest to cf430e0 (#2056)
- `26659b3fafdc0463d12856d10472b9a68184cace` — Update dependency org.openapi.generator to v7.26.0 (#2058)
- `a11cd9fb74c99c63c484e25508ccde501f1fa5f1` — Update Gradle to v9.8.1 (#1818)
- `f01c5d367cb61ab0aa0cba048a07b46df975ecf0` — Updates to session restoration (#1905)
- `9f557416ea56012c3e48d007f263159dc24772a2` — New user preference to force enable or disable common HDR and Dolby Vision formats (#1644)
- `55960c24625ef03bc5ac71b58806efdd613ffcc7` — Inherit parent view options and sort for sub-folders (#2045)

## Actual conflicts and decisions

Six paths conflicted, independently of the larger PR #114 conflict inventory:

| Path | Decision |
| --- | --- |
| `.github/workflows/main.yml` | Preserve downstream-owned absence; do not restore upstream publisher. |
| `.github/workflows/release.yml` | Preserve downstream-owned absence; do not restore upstream publisher. |
| `services/IntentService.kt` | Keep Mosaic application-ID playback action plus legacy compatibility, with upstream session preparation and testing annotation. |
| `services/UpdateChecker.kt` | Accept callback execution changes; preserve Mosaic canonical signed release/versioned Stable assets and Development matching. Reject upstream generic/ABI release fallback. |
| `test/TestUpdateChecker.kt` | Retain all Mosaic channel/source/asset regressions and adapt the incoming regular-release fixture to assert rejection of foreign aliases. |
| `gradle/wrapper/gradle-wrapper.properties` | Accept Gradle 9.8.1 with official distribution SHA-256 `dce76f55f8e251a3a1f130eb120f30b3d271de2b76c9b0729d316b5a1b6dc01f`. |

The checksum was read from
https://services.gradle.org/distributions/gradle-9.8.1-bin.zip.sha256;
the wrapper downloaded and verified that distribution.

## Clean semantic review and adaptations

- MainActivity accepts upstream upgrade-before-intent handling, hot restoration,
  explicit restoration outcomes and protected-user picker routing. Mosaic foreground feature
  controller start/stop, updater source selection and navigation/playback identities remain.
- ServerRepository accepts upstream remembered-session provider, distinct user observations and
  current-user websocket metadata updates. Metadata reads are fenced to the originating session;
  explicit foreign-server metadata is rejected. Stable user configuration retains the session's
  authoritative server ID even when the DTO omits it.
- UserSwitchListener accepts collectLatest and suppresses timestamp-only work, but deduplicates by
  server plus user plus language. Cancelled old-user Seerr authentication propagates cancellation
  instead of publishing an old-session failure. Genuine failures retain existing error handling.
- Workers using the new restore-last-session API must reject a restored user/server different
  from their scheduled input. Otherwise an old worker could query/cache using old IDs against a
  newly remembered session. Apply the exact cold-restoration guard to Suggestions, Latest Next Up
  and TV Provider; preserve their warm-path behavior and scheduling.
  Correct the existing currentUser test fixture to pass both identities to user(): it previously
  treated the requested user UUID as a server UUID and generated a random user UUID, concealing
  identity mismatches. Keep the worker guard rather than weakening it for that malformed fixture.
- AppUpgradeHandler migrates legacy remembered-session and Dolby Vision fields idempotently,
  without upstream version gates (Mosaic has an independent version stream). A newer remembered
  session and explicit HDR override win. Server-only sessions and newly selected servers are
  remembered without retaining an old user's credentials. A migration failure does not clear
  legacy identifiers first.
- Home's Stage A stable configuration/reload/Acquiring collector remains unchanged. Upstream's
  season-aware header/title/logo refinement does not replace Acquiring presentation or focus keys.
- SeriesViewModel accepts only the upstream initial-fetch error/cancellation handling. Missing
  Seasons request eligibility/editability, inactive-Seerr cleanup, persisted expectations, fresh
  Jellyfin integrity and shared acquisition projection remain intact. Episode focus restoration
  accepts the upstream specific episode-row focus target.
- Preferences add resolution limits, HDR force overrides and the opt-in Dolby Vision/HDR10+
  bitstream preference. Existing enhanced-feature and update-channel protobuf field numbers,
  defaults and persistence remain intact; no Room schema, migration or database-module change.
- Manifest retains explicit WorkManager ownership while restoring AndroidX Startup/profile
  initialization. WholphinApplication retains Mosaic initialization and accepts removing explicit
  OkHttp initialization. Build adds profileinstaller and accepts Kotlin/OpenAPI/Gradle updates;
  Mosaic package, version derivation, output naming and signing configuration remain unchanged.
- Collection/folder ancestor display/sort inheritance, empty collection-row suppression,
  music metadata/context actions, playback next/previous wiring, backdrop color corrections and
  websocket handling remain inherited. The incoming Home Videos expression accidentally returned
  only PHOTO_ALBUM; combine VIDEO, PHOTO and PHOTO_ALBUM in one list to preserve ordinary videos.
- Retain playback disconnect cancellation for the newly separate websocket job; moving it out of
  the original jobs list must not leave commands subscribed after release/backend replacement.
- Keep the updated MainActivity tests in their existing Mosaic test package/path, so the unchanged
  focused validation policy continues to select them; upstream's package move is not required
  for its internal testing hooks.
- Localized changes are additive/corrective. Mosaic resources remain. Updater test DI accepts
  the upstream provider but retains Mosaic database/test bindings. Fix its in-memory provider's
  clearUser assignment so tests exercise real clearing semantics.

The 29 baseline-overlap paths were inspected: two retired workflows; app build/manifest;
MainActivity; WholphinApplication; AppPreference; AppPreferencesSerializer; IntentService;
UpdateChecker; SeriesViewModel; HomePage; PreferencesContent; protobuf; eleven localized string
files (de/es/et/hr/pl/pt/sk/sv/tr/zh-rCN/zh-rTW); default strings; TestUpdateChecker; TestModule;
Gradle wrapper. Textual cleanliness was not treated as semantic acceptance.

## Regression coverage and validation

Local validation (2026-11-06):

- Kotlin production/test compilation passed with Gradle 9.8.1 and Kotlin 2.4.21.
- Focused JVM run: 183 tests, 18 suites; zero failures/errors/skips. Its XML evidence is
  retained under `.logs/stage-b/focused/`. This preceded the final worker guard/fixture
  correction; the complete run below covers those changes.
- Complete default-debug JVM run: 652 tests, 74 suites; zero failures/errors/skips.
  Command: `gradlew.bat :app:compileDefaultDebugKotlin :app:testDefaultDebugUnitTest`.
  Retain `.logs/stage-b/complete-jvm-final.log` and `.logs/stage-b/complete/` XML reports.
- Applicable offline tooling: 50 passed (repository identity 3, validation policy 29,
  version derivation 11, APK identity/verifier 7). These tools and workflows are unchanged.
- The first complete run exposed one malformed shared currentUser fixture (651 passed,
  one failed); the corrected final complete run above is green. Intermediate nullable-access
  and playback-test constructor compile errors were corrected, not waived or skipped.
- Final Fast validation runs changed-scope pre-commit, the focused classes below and whitespace
  checks. Its authoritative local result and stage logs are retained under `.logs/validation/`.
  No local Full, APK assembly, signing, install or publication is part of this validation.

```powershell
.\scripts\validate-local.ps1 -Level Fast -TestFilter @('*AppUpgradeHandlerTest', '*UserSwitchListenerTest', '*ServerRepositoryTest', '*SuggestionsWorkerTest', '*TestMainActivityViewModel', '*PlaybackViewModelTests', '*HomeViewModelTest', '*TestUpdateChecker', '*CollectionFolderViewModelTest')
```

New local coverage extends ServerRepositoryTest, IntentServiceTest, SuggestionsWorkerTest,
CollectionFolderViewModelTest and PlaybackViewModelTests. AppUpgradeHandlerTest and
UserSwitchListenerTest cover distinct previously untested migration/listener boundaries.
Existing Home, acquisition, Missing Seasons,
season integrity, request, feature-gate and updater regressions are retained. Incoming HDR,
bitstream and profile tests remain.

## Acceptance limitations

No device installation, APK signing, publication or production interaction occurs here.
Android TV cold/hot startup, protected profile/user/server switching, foreground/background
acquisition, deep links/Play Next, focus restoration, folder sort inheritance and HDR playback
still require device acceptance. Optional native FFmpeg/AV1/MPV components are unavailable locally;
the repository's MPV stub is used. Passing JVM/compilation is not device-level proof.

## Complete changed-file inventory against protected main

Status is the staged native integration delta, not the upstream-only 81-path delta.
The two retired workflows are absent on both sides and therefore do not appear here.

```text
M	app/build.gradle.kts
M	app/src/main/AndroidManifest.xml
M	app/src/main/java/com/github/damontecres/wholphin/MainActivity.kt
M	app/src/main/java/com/github/damontecres/wholphin/WholphinApplication.kt
M	app/src/main/java/com/github/damontecres/wholphin/WholphinDreamService.kt
A	app/src/main/java/com/github/damontecres/wholphin/data/MostRecentServer.kt
M	app/src/main/java/com/github/damontecres/wholphin/data/ServerRepository.kt
M	app/src/main/java/com/github/damontecres/wholphin/data/model/AudioItem.kt
M	app/src/main/java/com/github/damontecres/wholphin/data/model/BaseItem.kt
M	app/src/main/java/com/github/damontecres/wholphin/preferences/AppPreference.kt
M	app/src/main/java/com/github/damontecres/wholphin/preferences/AppPreferencesSerializer.kt
M	app/src/main/java/com/github/damontecres/wholphin/preferences/ExperimentalPreference.kt
A	app/src/main/java/com/github/damontecres/wholphin/preferences/HdrFormat.kt
A	app/src/main/java/com/github/damontecres/wholphin/preferences/MaxResolution.kt
M	app/src/main/java/com/github/damontecres/wholphin/services/AppUpgradeHandler.kt
M	app/src/main/java/com/github/damontecres/wholphin/services/BackdropService.kt
M	app/src/main/java/com/github/damontecres/wholphin/services/DeviceProfileService.kt
M	app/src/main/java/com/github/damontecres/wholphin/services/HomeSettingsService.kt
M	app/src/main/java/com/github/damontecres/wholphin/services/IntentService.kt
M	app/src/main/java/com/github/damontecres/wholphin/services/LatestNextUpWorker.kt
M	app/src/main/java/com/github/damontecres/wholphin/services/PlayerFactory.kt
M	app/src/main/java/com/github/damontecres/wholphin/services/ServerEventListener.kt
M	app/src/main/java/com/github/damontecres/wholphin/services/SuggestionsWorker.kt
M	app/src/main/java/com/github/damontecres/wholphin/services/UpdateChecker.kt
M	app/src/main/java/com/github/damontecres/wholphin/services/UserSwitchListener.kt
M	app/src/main/java/com/github/damontecres/wholphin/services/hilt/AppModule.kt
M	app/src/main/java/com/github/damontecres/wholphin/services/tvprovider/TvProviderWorker.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/components/CollectionFolderGrid.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/components/CollectionFolderView.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/components/ContextMenu.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/components/FocusableItemRow.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/components/TitleOrLogo.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/detail/collection/CollectionViewModel.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/detail/music/AlbumDetailsPage.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/detail/music/ArtistDetailsPage.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/detail/music/NowPlayingPage.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/detail/music/NowPlayingViewModel.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/detail/series/SeriesOverviewContent.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/detail/series/SeriesViewModel.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/main/HomePage.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/playback/PlaybackKeyHandler.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/playback/PlaybackPage.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/playback/PlaybackViewModel.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/preferences/PreferenceUtils.kt
M	app/src/main/java/com/github/damontecres/wholphin/ui/preferences/PreferencesContent.kt
A	app/src/main/java/com/github/damontecres/wholphin/util/BitstreamFilteringCodecAdapterFactory.kt
M	app/src/main/java/com/github/damontecres/wholphin/util/CrashReportSenderFactory.kt
A	app/src/main/java/com/github/damontecres/wholphin/util/Hdr10PlusSeiFilter.kt
M	app/src/main/java/com/github/damontecres/wholphin/util/profile/DeviceProfileUtils.kt
A	app/src/main/java/com/github/damontecres/wholphin/util/profile/WholphinVideoRangeType.kt
M	app/src/main/proto/WholphinDataStore.proto
M	app/src/main/res/values-de/strings.xml
M	app/src/main/res/values-en-rGB/strings.xml
M	app/src/main/res/values-es/strings.xml
M	app/src/main/res/values-et/strings.xml
M	app/src/main/res/values-hr/strings.xml
M	app/src/main/res/values-pl/strings.xml
M	app/src/main/res/values-pt/strings.xml
M	app/src/main/res/values-ru/strings.xml
M	app/src/main/res/values-sk/strings.xml
M	app/src/main/res/values-sv/strings.xml
M	app/src/main/res/values-tr/strings.xml
M	app/src/main/res/values-zh-rCN/strings.xml
M	app/src/main/res/values-zh-rTW/strings.xml
M	app/src/main/res/values/strings.xml
A	app/src/test/java/com/github/damontecres/wholphin/services/AppUpgradeHandlerTest.kt
M	app/src/test/java/com/github/damontecres/wholphin/services/IntentServiceTest.kt
M	app/src/test/java/com/github/damontecres/wholphin/services/ServerRepositoryTest.kt
M	app/src/test/java/com/github/damontecres/wholphin/services/SuggestionsWorkerTest.kt
A	app/src/test/java/com/github/damontecres/wholphin/services/UserSwitchListenerTest.kt
A	app/src/test/java/com/github/damontecres/wholphin/test/TestHdrFormat.kt
M	app/src/test/java/com/github/damontecres/wholphin/test/TestMainActivityViewModel.kt
M	app/src/test/java/com/github/damontecres/wholphin/test/TestUpdateChecker.kt
M	app/src/test/java/com/github/damontecres/wholphin/test/TestUtils.kt
M	app/src/test/java/com/github/damontecres/wholphin/ui/TestModule.kt
M	app/src/test/java/com/github/damontecres/wholphin/ui/components/CollectionFolderViewModelTest.kt
M	app/src/test/java/com/github/damontecres/wholphin/ui/playback/PlaybackViewModelTests.kt
A	app/src/test/java/com/github/damontecres/wholphin/util/BitstreamFilterChainTest.kt
A	app/src/test/java/com/github/damontecres/wholphin/util/BitstreamFilteringCodecAdapterTest.kt
A	app/src/test/java/com/github/damontecres/wholphin/util/Hdr10PlusSeiFilterTest.kt
A	app/src/test/java/com/github/damontecres/wholphin/util/profile/TestDeviceProfileUtils.kt
A	app/src/test/resources/release_v108.json
M	docs/CODEX_HANDOFF.md
M	docs/UPSTREAM_RESOLUTION_DECISIONS.md
A	docs/history/UPSTREAM_STAGE_B.md
M	gradle/libs.versions.toml
M	gradle/wrapper/gradle-wrapper.properties
```
