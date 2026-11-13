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
  The right-aligned Watchlist toolbar reuses Library's ExpandableFaButton dimensions and spacing,
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
  Random focus must be transferred only after the target and available adjacent pages form one
  published window; publishing page P before P-1 visibly shifts its index and can restore the
  wrong card. The typed catalog identity, not the mutable local grid index, anchors detail return
  and in-flight prepend focus. A 20-item API page need not be a whole visual row: CardGrid uses
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

- Public guidance is versioned in the repository. Optional Wiki publication remains a separate
  operator action and cannot replace engineering authority.
- Current upstream Draft PR #106 is the human-controlled same-episode candidate. Reused-Draft
  reconciliation requires fresh exact-main hosted evidence and preserves the Draft remote head
  until the reviewed two-layer merge is ready; do not close, rewrite, or force-update it.

## Non-obvious invariants and negative knowledge

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
