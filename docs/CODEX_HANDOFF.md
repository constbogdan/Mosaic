# Codex handoff: Mosaic

## Current checkpoint

**Baseline T0 is complete.** T0-1, T0-2, T0-3.0, and T0-3 have passed their accepted closure
criteria. Resume evidence-driven Mosaic product development from the canonical roadmap; do not
reopen completed infrastructure programs without new concrete evidence.

Current repository identity is exactly `constbogdan/Mosaic`; upstream remains
`damontecres/Wholphin`. Work begins from current protected `origin/main` on a purpose-specific
branch. Preserve unrelated work and inspect branch, remotes, status, and active Git operations
before editing.

## Start with canonical owners

- [Documentation navigation](README.md)
- [Application and engineering architecture](ARCHITECTURE.md)
- [Product roadmap](MOSAIC_ROADMAP.md)
- [Mosaic identity](MOSAIC_IDENTITY.md)
- [Pull-request preparation](PREPARE_PR.md)
- [Validation authority](VALIDATION.md)
- [Development publication](MOSAIC_DEVELOPMENT_RELEASE.md)
- [Signing authority](MOSAIC_SIGNING.md)
- [Stable Promotion and Hold](MOSAIC_STABLE.md)
- [Upstream integration](UPSTREAM_SYNC.md)
- [Historical engineering evidence](history/README.md)

The complete pre-D5 handoff is preserved byte-for-byte as
[`history/CODEX_HANDOFF_PRE_D5.txt`](history/CODEX_HANDOFF_PRE_D5.txt). It is historical continuity,
not current instruction.

## Immediate continuity

- Movie Quick Request Phase 1 is an uncommitted local candidate on
  `feat/movie-quick-request`, based on merged Watchlist main `accf48a1`. It overlays existing
  Seerr-backed movie grids; Jellyfin-local cards and TV remain untouched. Reuse
  `CurrentSeerr.canRequestMovie(movie, false)` on a fresh movie response, and recheck just before
  the shared normal-quality POST. The details action's combined standard/4K decision is not safe
  for this entry point. `RequestMovieDialog` needs explicit confirmation in single-quality
  configurations, where its existing details path otherwise auto-submits. A long OK release can
  arrive before asynchronous metadata resolves. The card host must observe that release but let it
  reach the originating TV Card before mounting the dialog: consuming it in the host's preview
  handler leaves the long-click gesture unfinished and loses the first later short OK. The dialog
  still gates the original hold/repeats and requires a separate full press for an action. Capture
  the originating Seerr session synchronously: resolving it
  after dispatch can turn an old card action into a new account's request. For standard-only
  profile loading, do not fetch the 4K Radarr configuration. The normal details Request action must
  use standard eligibility alone: combined standard/4K eligibility exposes a dead standard dialog
  for 4K-only titles. Each details-dialog opening clears prior profiles before display and binds
  its response to the opening account/client and epoch; closing invalidates that profile context,
  but must not release an already-started POST's single-flight guard. A Quick Request opening can
  abort before dialog state is published, so observe the initiating held OK through key-up and then
  restore ordinary card input; a null-to-null dialog-state effect cannot perform that cleanup.
  Fresh now uses the same Quick Request host; its movie-card action supplies the originating
  details callback so poster navigation and Back retain typed card focus. The host must not mount
  the early opening state until fetched MovieDetails passes standard-request eligibility:
  otherwise available or already-requested movies flash a
  loading dialog before policy dismissal. Publish the verified movie before profile loading so
  eligible dialogs still show loading promptly. Keep the early state for held-OK/session tracking
  and independent rating work; metadata failures and their subsequent Retry stay visible without
  reintroducing the initial flash. A stale Quick Request loader must return the current state from
  its atomic update when its opening token no longer matches; a nullable `takeIf(... )?.copy(...)`
  would instead clear a newer dialog after a rapid dismiss/reopen.

- Fresh uses Seerr's server-side 20-item pages and optional `category` parameter. Animation and
  Documentary are cross-media classifications: send `mediaType=all` with `category=animation` or
  `category=documentary`, never a movie/TV-only filter. The generated Kotlin client is derived
  from `app/src/main/seerr/seerr-api.yml`; generated files are not committed. A category or sort
  change cancels and resets paging; delayed responses are rejected by the ViewModel generation.
  Offset pages can shift as Fresh changes during browsing, so an append with changed total or
  duplicate typed identity restarts from page one once and retains typed focus where possible.

- Watchlist Phase 2 is an unpublished local candidate based on PR #120 and Seerr PR #57.
  Personal access requires `/auth/me.jellyfinUserId` to match the current Jellyfin user, plus
  the stored profile binding; shared API keys and unlinked users fail closed. A merged backend
  PR does not establish that an emulator's configured server has the new APIs deployed.
  Membership is session-scoped, typed, bounded and non-persistent. Generic 409/timeouts reconcile
  via the bulk endpoint rather than implying success or resubmitting. All list queries explicitly
  include `owner=me`, `sort=added_desc`; Shows sends `series`. Watchlist Owned/Watched filters
  default to `libraryStatus=any` / `watched=all` and persist in the existing AppPreferences
  DataStore under the complete personal Seerr/Jellyfin identity. Owned No deliberately uses
  `absent` (never physical, excluding archived/deleted history), per operator approval; Yes uses
  shared physical `active`, not Jellyfin access/playability. Watched No deliberately includes Unknown.
  Filter query keys include both selections; changes cancel/reset paging without clearing membership.
  Before the cross-page alignment candidate below, the right-aligned Watchlist toolbar reused
  Library's ExpandableFaButton dimensions and spacing,
  with film/check icons, neutral/blue/red tint and focus-only labels. OK cycles All/Yes/No;
  query completion must not steal focus from the toolbar while a filter is being cycled.
  Optional artwork.releaseDate (movies) / firstAirDate (series) feeds the existing shared card year
  through the tolerant catalog-date parser. Older backends omit these fields and still load;
  visible years require the separate Seerr enrichment update to be deployed. Dates reuse its
  bounded artwork lookup; do not invent years or add per-card requests.
  Both Discover and native Jellyfin movie/series actions share the page repository; local details
  require an existing positive TMDB identity. Global badges and ownership remain Phase 3.
  Grid key callbacks must capture the rendered screen-state snapshot, not live delegated state:
  category resets otherwise empty the key lookup before Compose removes the outgoing grid.
  B11 supersedes the Random grid-jump: retain uniform absolute-index selection over the current
  filtered total, reuse a complete cached page or fetch only the selected page without artwork,
  and open the typed item through the shared owned/Discover navigation resolver. Do not publish
  that lightweight response into the artwork-bearing grid cache or fetch neighboring pages for
  Random. Back restores the original grid/scroll and Random button focus; session/filter changes
  fence pending selection. The former adjacent-page window was necessary only for the removed
  grid-jump, where publishing page P before P-1 shifted card focus. Ordinary grid browsing still
  anchors detail return and in-flight prepend focus by typed catalog identity, not mutable index.
  A 20-item API page need not be a whole visual row: CardGrid uses
  its configured `GridCells.Fixed(columns)` count and the absolute window offset to insert
  non-focusable leading slots, preserving card columns across prepends without coupling paging
  to a particular screen width. Keep slot keys absolute so LazyGridState can anchor visible
  card keys when the window grows.
  Android TV Back also reaches the activity dispatcher; a grid key-up focus transfer alone can
  appear to work for a frame and then pop Watchlist. Handle grid Back through a focus-scoped
  Compose BackHandler, leaving toolbar Back to the ordinary navigation stack.
  Watchlist reuses CardGrid's focus/footer and poster layout; its denominator is the API total,
  not loaded rows. Prefetch is sequential, three grid rows ahead, and retains append failures for retry.
  Saved badges belong inside DiscoverItemCard's scaled poster, below top-corner status overlays
  and above the progress rail. The optional presentation flag stays off outside Watchlist (Phase 3).
  Detail bookmarks belong inside the existing native/Discover action rows and reuse ExpandablePlayButton,
  including focus expansion and row focus callbacks. Unknown membership is disabled, not an inferred add.

- Watchlist Library-style ribbon alignment is a local candidate on
  `feat/watchlist-library-alignment` from `28b0ce05`. It replaces the Watchlist tab row and
  separate toolbar with one ribbon: All, Movies, Shows, Animation on the left; View options,
  Owned, Watched, Random on the right. The normal populated entry hides the ribbon and focuses
  the first card; Up reveals it and focuses the selected category. Grid Back below the first
  visual row jumps to the first card and reveals the ribbon; first-row and ribbon Back use the
  normal navigation stack. Absolute-page leading slots matter for the first-row boundary.
  B11 Random's details return is separate: preserve the original grid and reveal/focus Random.
  Watchlist View options expose columns, spacing, title/year, backdrop and reset. Title/year
  controls the text beneath its shared poster; the backdrop uses the already-loaded selected
  Watchlist row artwork without another API request. The selected-item description option is
  deferred by operator decision: the Watchlist response contains no overview, and per-focus
  details lookups were declined. Image type and list layout remain inapplicable. Options are
  persisted per complete personal Watchlist owner in AppPreferences, independent of Jellyfin
  Library's Room-backed settings. The fixed 20-item API page is not a visual page: CardGrid
  measures the visible row demand and Watchlist sequentially loads enough pages to fill the
  selected column layout plus a three-row lead, even when the ribbon owns focus. Saved columns
  also start a bounded three-row minimum fill after the first page, before grid measurement;
  the measured-viewport lead remains authoritative afterward. Returning grid focus from the
  ribbon hides it immediately, including on the first row. Emulator and physical-TV acceptance
  are separate.

- Fresh ribbon alignment is part of the same candidate. It keeps Seerr's existing
  category, sort, 20-item page, quick-request, and page-shift recovery contracts while reusing
  CardGrid and View options for columns, spacing, title/year, backdrop, and reset. Its options are
  stored separately per Fresh session owner. Viewport-based prefetch fills wider grids without a
  D-pad move; the optional backdrop uses the focused Fresh row already in memory, with no detail
  request. The saved column count now starts a three-row minimum fill as soon as the first page
  confirms more results, before grid measurement; this eager trigger applies to initial entry and
  category/sort resets, not page-shift recovery or manual retry. The measured-viewport scroll-ahead
  path remains unchanged. Returning focus from the ribbon to any Fresh card hides the ribbon so
  the first row cannot remain clipped beneath it. Fresh synopsis is available in its existing item
  payload but no new synopsis header
  has been added. Android TV operator validation of the new presentation remains pending.

- Collections and Leaving Soon follow that same ribbon/grid presentation candidate.
  Collection completeness is a set of explicit category buttons; its existing server sort remains
  the right-hand action. Leaving Soon replaces its old tab wrapper with All/Movies/Shows buttons
  and retains Refresh. Both use the first-visual-row Up and deeper-grid Back handoff, and independently
  persisted columns/spacing/title settings. Collections index entries already carry a backdrop URL,
  so its optional selected-item backdrop needs no new request. Leaving Soon maps each response row
  to `DiscoverItem` with `backDropUrl = null`; therefore no backdrop toggle is exposed there. Adding
  one would require a separately reviewed Seerr data-contract change. The fixed 20-item API pages
  remain distinct from the visual grid; saved wide columns start a bounded three-row initial fill,
  measured viewport rows extend the lead, and Leaving Soon keeps its absolute-row window alignment
  and scroll-ahead loading. Neither the
  Collections ADMIN boundary nor Leaving Soon item navigation has changed. Emulator/TV acceptance
  of these two new layouts remains pending.

- Public guidance is versioned in the repository. Optional Wiki publication remains a separate
  operator action and cannot replace engineering authority.
- Current upstream Draft PR #106 is the human-controlled same-episode candidate. Reused-Draft
  reconciliation requires fresh exact-main hosted evidence and preserves the Draft remote head
  until the reviewed two-layer merge is ready; do not close, rewrite, or force-update it.

## Non-obvious invariants and negative knowledge

- The operator-accepted Collections Details Shows-style redesign (Phases 1-5) separates the
  collection overview from a focus-driven member/Recommended Collection preview. Both reserve the
  same header height, keeping the first member row stationary. The overview uses the shared
  CollectionCard, authoritative availability badge, description, and eligible collection Request;
  browsing uses loaded member/Recommended metadata and the shared backdrop service. The overview
  poster stays mounted until `CardGrid` actually acquires first-member focus; changing the preview
  selection on poster Down before that handoff removes the focused node and briefly sends focus to
  the drawer avatar. Recommended Collections' selected-card restoration must be an entry-only
  operation: treating every temporary loss of grid focus as a new entry immediately steals focus
  back from the active drawer item on first-card Left. Interior Similar Left/Right uses ordinary
  LazyRow focus movement. The 2026-11-26
  15:03 emulator ANR is preserved in `C:/Projects/mosaic-diagnostics/collections/phase2-anr-bugreport.zip`;
  its main thread waited in `HardwareRenderer.syncAndDrawFrame` while emulator EGL/QEMU pipes were
  active. The last recorded navigation before the ANR was to a Discover movie Details destination,
  not the Collections grid. This is not evidence of a Collections-specific application loop;
  attribution remains open.

- Collections Details backdrops can use the already-loaded `CollectionDetailsData.backdropUrl`,
  member `DiscoverItem.backDropUrl`, and Similar `CollectionDetailsData.backdropUrl`; the shared
  `BackdropService` owns image transition, color caching, and stale item-key protection. On a missing
  backdrop, clear it rather than stretching a portrait poster. `LaunchedEffect` tied to the active
  section cancels superseded submissions, and a returning Details destination must resubmit its
  saved section only when it is again the top navigation entry. The Seerr collection response has
  no native genres, runtime, watched progress, or logo; its member `MovieResult` carries optional
  date, genre IDs, vote average/count, and popularity. The redesign retains those optional
  member values in `DiscoverItem` without changing the server contract or using popularity for
  ranking/presentation. Multi-member collection genre and vote-count-weighted rating aggregates use
  distinct released members and show only at two-thirds metadata coverage; unknown release dates
  count against coverage but never contribute to either aggregate. Exactly one distinct member uses
  that movie's valid genres/rating directly; invalid or unrated values remain absent. Automated tests
  cover these single-member cases. The localized genre ID mapping needs one optional Seerr genre-list
  read per session, never a per-focus metadata read; failed mapping leaves names absent. Do not infer
  released counts from dates or Seerr availability: G29's authoritative count predicate is separate
  from generic collection parts and includes a physical-library override.

- The Collections Details entry-focus defect was a destination loading gap: the originating focus
  target left composition while the destination's loading content had no persistent focus owner, so
  the drawer avatar became eligible. Reuse `rememberCustomPageFocusOwner` on the collection page
  boundary through loading, gate handoff on active destination/resumed lifecycle and actual target
  readiness, and retain a focused Retry node invisibly during Retry-to-loading. Do not restore the
  old repeated poster-focus loop. The operator accepted entry from Collections browsing, Movie
  Details, and nested Recommended Collections. Temporary emulator tracing observed boundary-to-poster
  focus with no avatar callback on all three entry paths; the final uninstrumented Debug APK also
  passed bounded navigation/restoration checks. The cumulative candidate passed 1,239 JVM tests,
  Fast validation, Debug assembly and diff checks. This is not physical-TV acceptance. Live
  empty/error and missing-artwork acceptance remains limited. An earlier emulator ANR remains
  unattributed and was not reproduced during later bounded testing; the previously reported
  unspecified regression is still uncharacterized. No backend changes were made.

- G29/D-029 investigation on `origin/main` `2aceaedc` found two distinct Seerr TMDb collection populations. `/library/collections` counts distinct member IDs when separately fetched TMDb movie status is `Released` **or** Seerr's existing physical-library override applies; the numerator counts strict global Seerr `AVAILABLE`, not current-user Jellyfin playability or watched state. Generic `GET /collection/:id` returns all TMDb parts and does not include the per-member TMDb status used by the count rule; Mosaic deduplicates and sorts all returned movie parts. The operator accepts James Bond's 10/26 badge but reports 27 detail members, including an unwanted Planned title. No request-correlated James Bond payload or physical-override state was captured. Do not repair this by client-side date math, by treating `mediaInfo.status` as TMDb release status, or by changing canonical TMDb membership. A Seerr-owned shared eligibility/detail contract with snapshot provenance must precede a dependent Mosaic member-list change. Movie-link and Similar destinations currently pass null counts; null is not zero. Mosaic's badge currently shows `0/N` or `Count unavailable`; the approved presentation is positive known `available/total` only. Seerr's `stale` flag can mean a healthy active refresh over last-good data, so the normal-screen technical sentence is independently removable without disabling error/partial reporting. Collection member `UNKNOWN` maps to full-opacity artwork and is not proof of current-user absence; explicit PENDING/PROCESSING/BLOCKLISTED/DELETED already dim. G29 and G28 remain partial, B07 is not authorized. Full source/evidence boundaries are in `C:/Projects/mosaic-diagnostics/D029_G29_TMDB_COLLECTIONS_ASSESSMENT.md`.

- G29 follow-up: a second operator example, TMDb collection `1611146`, has a correct 1/1 badge but two detail parts; the extra `In Production` movie has a **nonempty** 2027 release date. The Bond Planned member has an empty one. `TmdbCollection.parts` is a summary type without movie production status; the existing detail response exposes only release date and optional Seerr media availability, not the count's production-status observation or physical-history class. The override includes both current `active` and retained `archived`, not just currently playable items. Therefore neither a Mosaic-only date filter nor `mediaInfo.status` can match the count. Reuse Seerr's existing predicate and expose an eligible-ID/detail contract while preserving canonical parts. Exact disputed IDs, deployed payloads and physical classes were not captured; no production URL was queried. G25 notice and G28 dimming remain separate.

- G29 Part B count-by-ID follow-up supersedes the earlier member-filtering proposal: operator-approved Mosaic presentation keeps every TMDb part visible; future/null/blank release-date members become focusable, non-actionable and last. Seerr's count denominator may intentionally differ from visible cards. Seerr's authoritative `/library/collections` snapshot already holds all count rows in a process-local, locale-keyed array **before pagination**, so a warm count-by-ID read needs no index-page scan or additional TMDb request. It is not persisted; the generic `/collection/:id` response and Seerr Library item banner have no equivalent count. The banner counts all distinct detail parts and can disagree with the status/physical-history index predicate. The read-only 2026-11-19 SQLite snapshot has an indexed canonical `(mediaType, tmdbCollectionId)` membership lookup but no materialized count; Bond ID 645 has 26 canonical movies and 10 matching Seerr `AVAILABLE` rows, numerically matching the badge but not proving predicate equivalence. Extending detail with nullable snapshot counts and provenance is the smallest warm-cache mechanism, but cold/missing counts require an explicit unknown state or separately approved bounded per-ID computation/persistence. The index route is ADMIN-only while detail is available to ordinary authenticated users; do not widen count visibility silently. Part B is CONDITIONAL GO for design only, and Mosaic G25/G28/G29 implementation remains postponed pending operator review. Full evidence: `C:/Projects/mosaic-diagnostics/D029_G29_TMDB_COLLECTIONS_ASSESSMENT.md`.

- G29/G28/G25 Mosaic implementation follows the later operator authorization, superseding the earlier investigation-only boundary, and Seerr's nullable authenticated detail-count contract. Detail counts override navigation counts only as a valid pair; known zero suppresses the badge, and unknown/invalid values never become a fabricated count. The card list keeps all valid distinct movie parts; parsed release dates after the device's local today or without a parseable date sort last, remain focusable, show grayscale artwork, and ignore click/long-click. The technical stale-snapshot sentence is removed while last-good state, partial errors and retry remain. Seerr backend deployment and live provider acceptance are separate from the Mosaic pull request.

- G28 poster follow-up: unreleased collection posters use a zero-saturation image filter at full opacity; the filter is applied to Coil's image only, leaving the card's focus border, labels and overlays colored. The operator subsequently approved **Seerr availability-based presentation**, superseding the earlier requirement for current-user ownership dimming on this screen. When at least one released member has per-movie Seerr `AVAILABLE`, released members not marked `AVAILABLE` use reduced poster opacity; otherwise released posters remain normal. This is not a Jellyfin ownership or playback claim: `CollectionsSessionSource.movie()` maps only Seerr's global media status, and absent `mediaInfo` becomes `UNKNOWN`, which may include an item accessible to the current Jellyfin user. Aggregate `availableMovies/totalMovies` is never used for member styling. Unreleased artwork stays grayscale and non-actionable regardless of Seerr status.

- G15 Stage 1's local Jellyfin identity index is an observation of the authenticated user's
  `/UserViews` and paged `/Items?fields=ProviderIds` response, including mixed user views. It is
  not proof of current access or exhaustive underlying UUIDs. Stock Jellyfin 10.10.7 can group
  by presentation key before returning a page, and the public query has no grouping-disable
  parameter. A completed scan
  therefore never turns an index miss into “unowned”; missing/malformed TMDb metadata and
  offset-page mutation remain unresolved. Room schema 39 replaces a user's complete snapshot
  transactionally only after all selected views pass consistency checks; failed or session-changed
  scans retain the last snapshot. The marker and candidate read is transactional too, so it cannot
  mix generations. An activity-scoped authenticated-session observer now starts one asynchronous
  scan when no matching successful Room snapshot exists or the previous snapshot is at least
  24 hours old; a genuine app resume also checks that age, while later app starts reuse a
  recent snapshot. Explicit `refresh()` remains available for a separately approved
  library-change policy. A stale refresh never blocks
  navigation and retains the previous snapshot until a complete replacement succeeds.
  `ServerRepository.changeUser` updates the shared API client before publishing `current`, so a
  scan checks both the `CurrentUser` instance and the API client's URL/token before and after
  provider calls. Checking `current` alone leaves a cross-account transition gap. Logout and
  account/server changes cancel the observer's scan. Logs contain only hashed user/server IDs,
  bounded counts/timing, and exception class. A skipped scan is not a live Jellyfin re-verification.
  G15 Stage 2 routes Seerr-backed Movie/Series card selections through one shared resolver:
  typed TMDb ID -> current server/user Room candidates -> one bounded current-user Jellyfin
  `/Items?ids=...` read requiring exact UUID, kind and explicit `ProviderIds.Tmdb` -> native
  Details only for a unique verified item. Missing, failed, timed-out or ambiguous matches
  use Discover; a session change suppresses pending navigation. Repeated OK events do not
  push duplicate destinations. Discover Details `Go To` uses the same verification path,
  never Seerr's Jellyfin ID. Completed Downloads retain their pre-existing acquisition-readiness
  destination, which is separate from this G15 resolver and was not changed in Stage 2.
  The 2026-11-19 in-place Debug emulator pass observed native Movie Details
  from Leaving Soon for The Mighty Quinn, native Series Details for Inspector Morse, and
  a Discover fallback for The Odyssey; a recent Room snapshot was reused without a scan.
  The operator additionally accepted Watchlist and Collections navigation on the emulator.
  G15 is approved for publication for this routing scope. Account switching, permission
  revocation, selection timeout and the 24-hour age transition have focused test coverage
  but were not manually exercised; other Seerr entry surfaces were not all traversed on-device.
  Event-driven index refresh on Jellyfin library changes is deferred. The 24-hour
  session/resume check is deliberately simple and does not continuously rescan while the
  application stays foregrounded. Jellyfin presentation-key grouping, missing explicit
  provider metadata and offset-page library mutation can still yield a safe Discover
  fallback rather than exhaustive owned-item discovery.
  Q08's former standard/4K selection policy was superseded by the confirmed
  one-managed-version, one-language-variant-per-user architecture.
  Publication is separate from acceptance: G15 is in Draft Mosaic PR #136
  targeting `main`, not merged or released. Its first hosted Full run exposed
  three legacy card-navigation tests that still expected a direct Discover
  push instead of delegation to the shared G15 resolver. Only those tests were
  corrected; the full local Default Debug graph passed 1,143 JVM tests,
  Kotlin compilation and Debug assembly. Incremental identity indexing and
  event-driven refresh are deferred enhancements, not routing blockers.
  PR #136 subsequently passed hosted Full validation and merged into `main`
  as `0d4ff42ed98881ed981ed99feec59a744097abee`; the preceding Draft
  publication status is historical. This merge does not establish a release.
  G23/D-023 Series action-state safety remains a separate investigation gate,
  not covered by G15 routing acceptance.
  The 2026-11-19 G23 controlled fixture on this merged baseline reproduces the
  intermediate Request label: owned seasons disable it, while an eligible missing/4K
  or editable pending season can enable it. Selecting Request opens a season dialog;
  seven delayed-state fixtures made zero automatic request submissions. Focused
  tests (27/27) and targeted Fast validation passed. A separate foreign-pending
  fixture found that the dialog preselects another user's noneditable pending
  season when a different missing season makes Request eligible; an explicit
  Submit could carry the wrong season, although inspected Seerr source filters
  existing pending seasons. B05 is only conditional on a separately approved
  default-selection correction. Rendered D-pad focus and deployed payload
  behavior remain unverified.
  A separately authorized B05 slice now makes the season dialog's
  checked switches match submitted IDs: foreign/noneditable pending seasons are
  neither selected nor focusable, while current-user editable pending requests
  remain selected for the existing edit path and missing/4K seasons still require
  explicit selection. The controlled Compose D-pad fixture uses only a local
  submit callback, never Seerr transport. The existing `androidTest` source set
  cannot currently compile (missing test dependencies and stale test signatures),
  so an emulator app-start smoke is not proof of the foreign-pending dialog
  interaction. On 2026-11-19 the operator accepted the source-level finding and
  approved retaining this narrow correction. Final focused Fast validation passed
  (five relevant JVM/Compose test filters, changed-scope pre-commit and whitespace
  check). The installed Debug 1.0.117 APK was identified on `emulator-5554`, but
  no existing series-dialog fixture can create a controlled foreign-pending case;
  the Home fixture is unrelated and the existing `androidTest` source set does
  not compile. Thus this is not controlled emulator dialog acceptance. The
  transient Request label issue was deliberately not changed.
  See `C:\Projects\mosaic-diagnostics\D023_G23_SERIES_ACTION_ASSESSMENT.md`.

- G04/B10 has a **superseding operator authorization** for local implementation
  of lightweight acquisition polling. In the isolated G04 worktree, Seerr
  discovery is one newest-25 request-list page per interested cycle, with
  targeted reads for already-known off-window acquisitions; one shared tracker
  polls only while Home, Downloads or native Series Details is visible.
  Downloads explicitly refreshes on entry and retains its fast cadence;
  changing cadence alone no longer triggers a duplicate entry cycle. A first
  emulator pass measured one list GET per Home cycle (25 records), a 7.352 s
  cold/0.923 s warm cycle and zero acquisition GETs over 71 seconds on
  Settings. After correction, Fast validation passed 586 focused JVM tests;
  the rebuilt Debug APK showed one immediate Downloads cycle followed by
  roughly 8.5-second fast cycles, then zero calls during 30 seconds in the
  background and one immediate cycle on return. A controlled completion or
  import transition was not observed. Older never-seen acquisitions beyond
  the 25-row window are not discoverable, and off-page nav state is last-known.
  G04 remains PARTIAL; publication, merge and release must be verified separately.
  A final lifecycle review passed 132 current focused tracker/readiness/transport/
  Downloads JVM tests and 45 model tests in addition to the earlier 586-test
  Fast run. Existing fixtures cover phase classification and reconciliation;
  no controlled live import-to-Jellyfin-ready or off-window targeted transition
  occurred on the emulator. The operator accepted the newest-25, unseen-older,
  last-known off-screen and no-continuous-general-discovery policy limits and
  separately authorized Draft PR publication, not merge or release. No B10
  production change was justified by the final review.
  A future foreground top-right progress indicator can lease the same singleton,
  but `queueingRequests` loses local-origin identity after reconciliation and
  expires after 15 minutes; `requests` has no local-origin marker. A local-only
  indicator needs a small session-scoped request-ID projection. The current
  poll loop always fetches newest-25 even with only a header lease, so avoiding
  continuous general discovery for that future consumer also needs a targeted-
  only interest mode in the same loop. Do not add a second loop or background
  poller, and do not mistake Seerr progress for Jellyfin readiness.
  See the current D-004/G04 assessment in mosaic-diagnostics. Earlier
  investigation-only notes below are historical.
- G04's demand-driven follow-up is **investigation only**, on integrated main
  `20302997`. The current acquisition tracker is one foreground-wide loop;
  Downloads leaving only reverts its cadence from eight to thirty seconds after
  a completed cycle. Home Acquiring, the global Downloads nav indicator, request
  presentation and contextual Series integrity read its shared snapshot. A
  Downloads-only visibility gate therefore either leaves the always-visible
  indicator as a permanent polling demand or changes its off-page freshness
  contract. Jellyfin Recently Added uses `getLatestMedia` independently but
  still needs a normal Home/Jellyfin refresh to appear. Local Seerr request
  filters classify request/media status, not all live queue entries or verified
  Jellyfin readiness; neither the 25 locally retained records nor a newest-page
  limit proves complete acquisition coverage. B10 has only a conditional
  visibility-only design boundary using the existing full-history query, subject
  to operator badge/Home freshness decisions and runtime/lifecycle proof. Do not
  start a second poller, infer readiness from Seerr status, or treat an unobserved
  completion timestamp as a precise import time. See the current D-004/G04
  assessment in `C:\Projects\mosaic-diagnostics`.

- G04 independent source review found a client-only cost candidate that does
  not change the live Home/nav contract: the local Seerr request list accepts
  `take` without a defined maximum. For the observed 477 requests, a bounded
  `take=250` would require two full-history pages rather than ten at 50, but
  response bytes, server DB/profile work, tail latency and deployed support
  are not measured. Benchmark before selecting the page size; never truncate
  old active/completed requests. `MediaRequest.id` is generated at insertion,
  not a change sequence. Existing requests can change status, media/season
  state or transient queue progress without a new ID; request `updatedAt`
  likewise cannot capture all media/queue changes. Do not build an ID-only or
  request-timestamp-only incremental poller. A later demand-driven lease
  remains separate and requires explicit off-page nav-badge/Home freshness
  acceptance. G04 is PARTIAL; B10 has design/validation candidates only, no
  implementation authorization. See the independent section in the D-004/G04
  assessment.

- Movie Details B02 keeps a full Seerr movie GET on genuine later lifecycle resumes because
  the bundled client has no lighter authoritative per-movie availability read. The first
  `LifecycleResumeEffect` must only join the ViewModel's constructor-owned load; request and
  cancellation retain their separate targeted status reads. A later resume supersedes even an
  unfinished entry read, while same-session status and optional rating/related metadata have
  separate work ownership. Never treat acquisition progress as proof of availability. Movie,
  people, trailer, rating and related-result publication must all remain fenced by the current
  Seerr client/account and entry generation. The diagnostic register owns operator acceptance.

- Collections focus follow-up (2026-11-24): a saved `CardGrid` scroll position
  must not be replaced by `scrollToItem(focusAnchor)` when that card is already visible;
  doing so pins a formerly middle row to the top after Details Back. A known owner with
  a null or `PENDING` index response is still loading, not an empty-state ribbon focus
  target. Initial grid focus and nested Collection Details focus must be confirmed from
  observed focus state, not the boolean return of `tryRequestFocus`; the latter only
  reports that a request was issued. The nested entry waits for its navigation lifecycle
  to resume, then makes bounded focus attempts. The partial-count warning is distinct
  from the removed technical older-snapshot notice and still reflects backend status.
  The nested Similar Collections focus path is separate from the four-page browser
  correction below; this earlier candidate did not claim to correct either path.

- Four custom catalog pages (Watchlist, Fresh, Collections, Leaving Soon) now have a
  local shared-focus candidate on `fix/shared-custom-page-focus` from merged main
  `d2ae71c5`. A persistent page focus boundary owns focus while loading; the grid
  handoff is tied to a composed target, not a timed retry loop. A currently visible
  ribbon is an explicit Right-entry target, while Watchlist Random's existing Back
  restoration keeps priority. `CardGrid` routes Left only from a window's first
  card or an absolute leftmost column to the active drawer item supplied by
  `NavDrawer`; native Library callers retain their defaults. Leaving Soon keeps
  `initializing` distinct from a completed empty result, so preferences suspension
  does not reveal the ribbon prematurely. Emulator/physical-TV acceptance remains
  distinct from focused JVM tests. Temporary emulator logging observed a residual
  7-30 ms avatar-focus event before the page boundary takes focus on each custom
  destination; the earlier multi-second avatar hold during loading was not seen.
  Do not claim zero avatar-focus events or generalize this browser contract to
  the nested Similar Collections row without separate evidence.
  This is an incremental shared custom-page focus adapter, not universal page
  orchestration or a replacement for native Movies/Shows focus handling. A future
  custom browser must keep its page boundary composed through loading, provide a
  stable readiness key and call the grid-ready callback only after its first
  focusable target is composed, supply the active drawer item requester for
  left-edge grid navigation, and identify its ribbon focus target. The page
  still owns its data loading, non-grid Back cases, selected-card/scroll restoration,
  and nested-detail focus. Leaving Soon also defers empty-state focus until
  initialization has completed. Bounded emulator checks covered native Movies
  and Shows Library entry, first/deep-row drawer navigation and Details Back;
  Watchlist Random opened Details and returned focus to Random. Synthetic UI
  tests cover a page-5 Watchlist Random return and empty/error focus states.
  Physical-TV validation remains outstanding; the 7-30 ms avatar callback has
  not been shown to produce a rendered highlight. The reported
  boundary-to-first-card intervals
  (Watchlist 9.542 s, Fresh 5.287 s, Collections 2.491 s, Leaving Soon 2.569 s)
  are focus-acquisition measurements, not isolated API or rendering timings,
  and belong to a separate page-initialization investigation.

- Shared CardGrid Back candidate (2026-11-25, uncommitted on
  `fix/shared-cardgrid-back`): the four custom browsers now use the native
  grid's absolute-first-card Back rule instead of page-level first-row
  thresholds and ribbon/epoch jumps. Android TV may dispatch Back after the
  grid loses focus, so an opt-in `CardGrid` dispatcher fallback handles that
  path without changing native Library callers. A first request can reenter
  the grid on its saved card; the custom-page path checks actual card focus
  and makes one frame-bounded second request only if card zero did not acquire
  focus. Watchlist and Leaving Soon reload page one when a bounded window has
  evicted it, clearing the old focus key. A future custom paged browser must
  provide the absolute window offset and a page-one callback when applicable.
  This candidate does not change B11 Random return, modal Back, nested
  Collections Details, or long-press behavior. Emulator checks are not
  physical-TV acceptance.

- Physical-TV follow-up (2026-11-25): the operator accepted Back-to-first-card
  on Watchlist, Fresh, Leaving Soon and Collections, and B11 Random return to
  its button. Ordinary Details return exposed a separate avatar/drawer focus
  defect on Watchlist, Fresh and Leaving Soon; Collections already restored its
  card after a brief avatar focus. The uncommitted correction shares the
  Collections ON_RESUME focus epoch across these browsers, using each saved
  card key and CardGrid's existing focus anchor. Watchlist suppresses this
  handoff while Random's ribbon owns return focus. Preserve exact-card/scroll
  restoration and the accepted Back rule independently. Emulator checks are
  not physical-TV acceptance of this new correction.

- Collection Details B06 passed the operator's final 2026-11-18 emulator acceptance for
  R1/R2/R3 and two-stage Back-to-Request; the earlier failed pass remains historical evidence.
  Immediate Back after cold entry must exit; Back from a card only
  becomes the poster/Request shortcut after deliberate in-page navigation. A Similar card's
  horizontal index is not used to choose a member on Up: the saved member key does that.
  The source-supported focus hazard is that member focus clears `similarFocusedId`, while the nested
  `LazyRow` may still be scrolled to a later Similar card. A single movable `FocusRequester`
  can then target an uncomposed first card, producing a footer scroll without focus transfer.
  Retain a visit-local Similar return target, prepare the nested row after the parent footer is
  visible, and reserve selected-card auto-focus for entry restoration; normal Up/Down uses
  explicit epochs. On poster Back, the Collections browser can otherwise spatially focus the
  drawer avatar; its lifecycle-resume focus epoch reselects the saved collection card, without
  stealing toolbar focus. The member key fallback must tolerate an empty collection. A focusable
  card's separate title-text semantics node is not the focused
  target in Compose tests. The diagnostic register owns operator/runtime evidence; automated
  tests and emulator smoke did not themselves establish acceptance; the separate operator pass did.

- Similar Collections C4.2 consumes Seerr PR #63's lightweight authenticated
  `GET /collection/{id}/similar` separately from the full collection-details response.
  Polling the full details endpoint would repeatedly map constituent media and requests;
  its optional `similarCollections` field is not a substitute for the new read path.
  `discoveryActive=false` only means no unexpired origin claim exists, not that all
  eligible seeds succeeded or are exhausted. An empty successful read is valid, while
  a failed read must remain an error rather than an empty list. POST's `started`
  reports claim acquisition, not completion. Existing Android clients must not fall
  back to local recommendation-page discovery when this endpoint is unavailable.
  The screen's entry UUID is stable across child Back restoration; only a new
  navigation entry may submit a new discovery trigger. Related-card hydration
  remains capped at three concurrent requests and is independent of discovery polling.
  Main details, the first lightweight read, and the visit trigger start independently:
  an inactive read before POST settles cannot end observation. The single-read-at-a-time
  observer polls at 500 ms inside a 24-second total window (including initial and final
  reads), while each resolved card publishes without waiting for the other metadata.
  A separate observation generation is needed even within one entry: a cancelled
  child-navigation read may finish after the parent resumes, and its cleanup must
  not clear the newer observation's loading state.

- Request actions check current capabilities again at submission. Standard has no separate
  public backend enabled flag; 4K requires its public enabled flag and matching permission.
  Acquisition conversion now reads selected-quality media/season availability from `media`, never
  request-season lifecycle status or the opposite quality. Missing physical seasons stay Unknown;
  their timestamps must not come from request seasons. The shared generated `Season` DTO is still
  used in both payload contexts, so field ownership matters. Queue progress and Jellyfin readiness
  remain independent. Acquisition readiness now uses source-level Jellyfin video dimensions for
  4K (width at least 3840, positive height; cropped UHD/DCI sources qualify). Standard keeps its
  existing playable-source behavior. Item-level streams, names/HDR, Seerr `status4k`, request
  completion and queue disappearance are not quality proof. `MEDIA_SOURCES` includes those
  per-source streams; readiness queries do not need the unused top-level `MEDIA_STREAMS` field.
  Ordinary requests keep the chosen Servarr profile and standard service role even for UHD;
  3596-wide or dimensionless playable sources qualify without a new upgrade request. Current
  public 4K capability flags gate new submissions, not historical 4K tracking. The dedicated-4K
  3840-width limitation remains a separate follow-up, not a generic readiness restriction.
  Seerr's scanner uses width >2000,
  which can classify 2K as 4K; its Library resolution summaries are neither per-version Jellyfin
  identities nor part of the request contract. Movie fulfillment and generic playback IDs are
  separate; TV counts only exact verified episode numbers for 4K, never duplicate versions.
  Same-context last-good proof survives refresh omissions, but changed target/quality/creation
  identity or an identifiable new transfer requires a new observation. Account/server reset
  still clears readiness and ledger state; cancellation must propagate out of readiness lookups.
  Jellyfin metadata is not a live filesystem probe: stale server metadata, missing/reused transfer IDs
  within continuously present queues,
  and matching alternate versions outside the bounded 20-result lookup cannot be resolved with
  these APIs. Missing dimensions remain unknown; do not substitute Seerr's status or provider
  profile labels. Separate duplicate series are not silently merged across parent identities.
  Explicit queue phase/health is projected once for Home and Downloads. Blocked/failed evidence
  overrides byte/time completion; a mixed failed/healthy episode scope is Warning, not globally
  Failed. Explicit evidence uses the existing two-missed-poll grace and never promotes queue
  disappearance to import success. Fields absent retain legacy fallback. Reasons are allowlisted
  backend codes mapped to local text, never provider errors. Internal backend observation IDs are
  not serialized: reused transfer hashes without distinct evidence cannot prove a new generation.
  Seerr-custom's request-list query does not explicitly join `media.seasons`; omitted physical
  seasons therefore remain Unknown, not completed merely because a request completed.
  TV PUT selection is owner-, media-, quality- and season-overlap-specific. Disjoint seasons
  use POST; multiple overlapping editable requests fail closed rather than choosing one.
  The generic Discover Cancel action is offered only for one authorized target across qualities;
  multiple targets need a separately reviewed chooser. Owner cancellation needs pending status,
  not a request-creation permission; managers may delete any status under the backend contract.
- TV request POST/PUT may return a message-only 202 when no seasons remain requestable.
  This is not an asynchronous download acknowledgement and carries no request identity.
  `TvRequestSubmission` reuses generated configs/transport but decodes that outcome before
  `MediaRequest`; the generated `WithHttpInfo` methods still deserialize too early. Both TV
  callers refresh eligibility without Queueing or resubmission on 202. Movie submission,
  quality targeting and acquisition mappings are deliberately unchanged by this correction.
- Stage C combines Mosaic schema 36 with upstream track persistence as Mosaic schema 37,
  not upstream schema 40 (which lacks the integrity table). Keep every older schema fixture
  and the 2-to-3 migration. Legacy track-choice conversion belongs in the 36-to-37 Room
  transaction: Mosaic app versions cannot safely use the upstream 1.0.10 gate, and a
  global non-empty destination check would skip partially converted data. Retain the legacy
  table, disabled subtitles with no language, and newer choices on repeated conversion.
  Blank legacy languages must stay absent so ordinary profile defaults still apply.
- Stage B's session/HDR preference migrations cannot use upstream Wholphin version thresholds:
  Mosaic versions advance independently. Migrate from the legacy fields idempotently, retaining
  a newer remembered session and explicit HDR override, and preserve server-only restoration.
  The new user-switch deduplication must include server identity, not only user UUID/language;
  cancelled Seerr authentication and old-server metadata must not publish into a replacement
  session. Keep Mosaic's exact signed updater asset contract rather than upstream APK aliases.
- Home refresh uses timestamp-independent `ServerUserConfig`; valid same-user/server refreshes
  retain loaded rows and Acquiring instead of resetting the entire state. Ordinary rows reset on
  invalid context or user/server changes, including a reused user UUID on another server.
  Acquiring still follows its authoritative provider's session clearing, not Home's row lifecycle.
  See the [PR #111 resolution decision](UPSTREAM_RESOLUTION_DECISIONS.md).
- A newer `waiting_on_existing_pr` observation is not the older Draft's episode. The resolver can
  bind its exact blocker identity to independently verified original blocked-context evidence and
  current main, but must still resolve only the original upstream target. Missing fresh hosted
  proof or mismatched identity remains a refusal; see [Upstream integration](UPSTREAM_SYNC.md).
- Resolver dependency scope is deliberately limited to the existing request-dialog caller, its
  private permission producer, and the next Mosaic Room export, proved from immutable authenticated
  sources. Caller permission alone cannot satisfy a required dialog argument when its state lives
  in a private ViewModel repository. The producer exception admits only the upstream-equivalent
  fail-closed standard-TV flow addition, not other edits in that file. Use the remaining native
  range, not the original episode's full backlog, to justify a producer change: otherwise already
  accepted Stage B/C work could incorrectly authorize a new adaptation. This does not grant an
  editable handoff, neighboring source, or arbitrary schema export any publication authority.
- Local Fast feedback is not hosted validation authority. Every PR receives complete offline
  tooling; uncertain Android/build scope selects `ANDROID_FULL`. Protected main reuses evidence only
  for an exactly authenticated tested tree and otherwise runs the complete conservative fallback.
- A Kotlin test helper is not a runnable class merely because its file changed. The shared selector
  omits only positively proved top-level helpers; uncertain targets remain subject to matching.
  The resolver validates its derived floor before a semantic handoff, so that handoff cannot repair
  a bad derived target. Resolver/publisher entry points bind to their own checkout. A tooling PR
  advancing main does not rewrite a preserved integration, but makes an older B's exact-main parent
  stale; fresh evidence and separately authorized reconciliation are still required.
- Validation class and Development release relevance are independent. High-risk tooling can require
  Android validation without requiring an APK.
- Development Release assembly belongs to authenticated final main. Signing is isolated from build
  and publication. Stable promotes exact authenticated Development bytes and never rebuilds or
  re-signs them.
- Release recovery is forward-only. A bad Stable may be held, preserving its tag/assets/provenance,
  while a higher-version Development fix is prepared and promoted. Do not invent rollback, repoint,
  unhold, or mutable-release recovery.
- `wholphin-pr-policy-v1-*` and `wholphin-upstream-*` are stable machine protocols, not unfinished
  branding. Kotlin `com.github.damontecres.wholphin` namespaces may remain inherited source identity.
- Upstream REVIEW/conflict Drafts are human-controlled. Automation may observe, authenticate,
  prepare, and validate; it does not silently resolve semantics, mark Ready, force push, or merge.
- `waiting_on_existing_pr` is green only for one fully authenticated managed blocker and cannot mint
  the publication token or mutate GitHub state. Ambiguity, drift, malformed evidence, API failure,
  or integrity uncertainty remains red.
- GitHub Release API `name` remains `v1.0.N` because updater parsing consumes it. Development uses
  fixed canonical asset names; Stable uses versioned assets. Stable bytes equal the authenticated
  Development candidate.
- Jellyfin is authoritative for local playability. Seerr availability/request state and acquisition
  lifecycle do not prove playback readiness. Persist expectations, not live integrity conclusions.
- Normal Movies and Series grids remain Jellyfin-local. Acquiring/non-local content belongs on
  explicit transient, watchlist, or discovery surfaces.
- Missing-season new-request eligibility is separate from editability of an owned pending request:
  bulk selection must not remove existing pending seasons. Seerr metadata loss must invalidate
  request dialogs and pending profile reads without replacing Mosaic's cached-expectation/fresh
  Jellyfin integrity evaluation with the upstream contribution's simpler fallback. The PR #1919
  Request badge is deliberately excluded from Mosaic.
- Historical names, repository URLs, assets, workflows, SHAs, failures, and rejected approaches are
  intentionally preserved in the D4 history boundary. Do not modernize evidence.

## Publication and validation boundary

Implementation completion is not publication authority. Run `scripts/prepare-pr.ps1` only after an
explicit user instruction to publish. It owns reviewed scope, cheap local feedback, exact staging,
commit-tree identity, safe no-force push, PR reuse/creation, exact PR-head authentication, and
eligible merge-commit auto-merge. GitHub owns required CI, protection, and merge execution.

Use `scripts/validate-local.ps1 -Level Fast` for ordinary feedback. Standard and Full are explicit
broader diagnostics, not routine gates. Pre-commit autofixes can change files; inspect and rerun
rather than silently publishing a rewritten snapshot.

## Open risks and questions

- **External/manual responsibility:** GitHub settings remain manually administered. Baseline
  closure reverified the current ruleset, merge settings, Environments, Actions defaults, Sync Bot
  credential names, and repository identity; no configuration doctor or scheduled monitor is
  justified without repeated drift evidence.
- **Known non-blocking limitation:** Android TV visual/focus/device behavior is not fully proven by
  hosted compilation and JVM tests; future UI changes still require proportionate device checks.
- **Future work:** Cross-repository tooling standardization remains evidence-driven. Do not copy
  Mosaic-specific Gradle, PowerShell, branch, artifact, or release assumptions into another
  repository.
- **Future product work:** Watchlist, collections/franchises, Discover, suggestions, next-season
  automation, quality upgrades, playback improvements, and optional integrations remain roadmap
  work rather than baseline infrastructure debt.

## Handoff maintenance

Keep this file bounded to the active checkpoint, unfinished work, risks, invariants, and negative
knowledge that cannot be inferred safely from code. Promote stable facts to their canonical owner
and completed chronology to the history boundary. Do not rebuild another permanent activity ledger.
