# Wholphin Roadmap

> Living roadmap for the Wholphin fork: completed work, current direction, and future backlog.

## Current engineering program: Baseline T0

**I06 — COMPLETE / LIVE VALIDATED.** Native upstream synchronization and Git ancestry are
authoritative; the obsolete Issue/journal/finalizer lifecycle is removed.

**I07 — COMPLETE / LIVE VALIDATED.** Development uses forward-fix recovery. Stable supports the
minimal emergency Hold Release followed by forward-fix and zero-input Stable Promotion.

**Baseline T0 — IN PROGRESS. T0-1 is COMPLETE. CP4B.1 exact-tree reuse contract repair is COMPLETE / HOSTED
VALIDATED. CP4B.2 — Authoritative PR Validation Model — is COMPLETE / HOSTED VALIDATED for both
`ANDROID_FULL` and `NON_ANDROID`. CP4B.3 — Local / prepare-pr simplification — is COMPLETE / HOSTED
VALIDATED. P04 — prepare-pr native auto-merge — is COMPLETE / HOSTED VALIDATED. Validation-plan
visibility and prepare-pr terminal UX are COMPLETE / HOSTED VALIDATED. The Operator UX batch is
COMPLETE / HOSTED VALIDATED. Release / Diagnostic UX is COMPLETE / HOSTED VALIDATED (PR #74).
V01 is KEEP / COMPLETE. CP5 is COMPLETE / NO MIGRATION JUSTIFIED. CP7 is COMPLETE / HOSTED
VALIDATED (PR #77). The upstream waiting-state correction is COMPLETE / HOSTED VALIDATED (PR #79;
scheduled run #47). CP8 final consistency is COMPLETE. The T0-2 audit is COMPLETE / SCOPE APPROVED;
C1 is COMPLETE / LIVE VERIFIED; C2 is IMPLEMENTED / LOCAL VALIDATED with hosted acceptance pending.** Baseline T0 is an
engineering baseline, not an application release. The ordered program is:

The CP7 performance phase is **COMPLETE / HOSTED VALIDATED**. CP7.1 replaced the prepare-pr test
harness's fake Git transport with private real bare origins, retained fake GitHub state, restored
Fast to bounded local feedback, and retained the complete offline suite as authoritative PR coverage.
Its measured lifecycle, closure decisions, and hosted PR #77 evidence are in
[the CP7 performance audit](T0_1_CP7_PERFORMANCE_AUDIT.md#final-hosted-acceptance-and-closure).

The authenticated upstream waiting state is also complete and hosted validated. Protected-main run
#150 reused PR #79 `ANDROID_FULL` evidence, required no Development delivery, and completed in about
46 seconds. Natural scheduled run #47 then completed green in about 35 seconds while waiting on the
older authenticated Draft PR #58: Observe retained the complete newer range and blocker head;
Publish reauthenticated both, minted no App token, performed no branch/PR mutation, and reported
`Waiting on PR #58`. Expected authenticated human-review waiting is green; ambiguity, identity,
integrity, infrastructure, and API failures remain red. PR #58 is intentionally not rewritten to
adopt a presentation format created after it. No functional blocker remains before CP8.

Checkpoint sequence: **CP1 COMPLETE; CP2 COMPLETE / HOSTED VALIDATED; CP3 COMPLETE / HOSTED
VALIDATED; CP4A COMPLETE — BEHAVIORAL EXECUTION AUDITED; CP4B.1 COMPLETE / HOSTED VALIDATED;
CP4B.2 COMPLETE / HOSTED VALIDATED; CP4B.3 COMPLETE / HOSTED VALIDATED; P04 COMPLETE /
HOSTED VALIDATED; Validation-plan visibility COMPLETE / HOSTED VALIDATED; prepare-pr terminal UX
COMPLETE / HOSTED VALIDATED; Operator UX batch COMPLETE / HOSTED VALIDATED; Release / Diagnostic UX
COMPLETE / HOSTED VALIDATED; V01 KEEP / COMPLETE; CP5 COMPLETE / NO MIGRATION JUSTIFIED; CP7
COMPLETE / HOSTED VALIDATED; upstream waiting-state correction COMPLETE / HOSTED VALIDATED; CP8
COMPLETE. T0-1 COMPLETE.**

CP4B.2 evidence classes: **`ANDROID_FULL` — HOSTED VALIDATED; `NON_ANDROID` — HOSTED VALIDATED.**

1. **T0-1 — Cleanup & Operator Experience:** inventory every human-facing surface and machine
   contract before simplifying validation duplication, workflow/operator presentation, obsolete
   surfaces, performance bottlenecks, and PR/release navigation.
2. **T0-2 — Engineering / Process Audit and bounded corrections:** the audit is complete and its
   implementation scope is exactly C1 merge-commit-only settings, C2 explicit build bootstrap, and
   C3 sanitized upstream failure diagnostics.
3. **T0-3 — Documentation, Wiki & Roadmap:** consolidate current sources of truth separately from
   historical evidence and make the architecture navigable from `docs/README.md`.

Declare **BASELINE T0** only after all three are complete and the repository can state: **No known
issue blocks Baseline T0.** Then resume Wholphin feature development. The detailed checkpoint,
backlog, operating principles, audit scope, and acceptance criteria follow below.

The authoritative [T0-1 inventory and implementation ledger](T0_1_OPERATOR_UX_INVENTORY.md)
maps all current workflows, presentation/output/timing/conditional surfaces, machine-contract
risks, 40 implementation items, and the bounded CP2–CP8 sequence. Its
[CP4A behavioral audit](T0_1_OPERATOR_UX_INVENTORY.md#t0-1-cp4a-behavioral-execution-audit)
adds the measured local-edit-to-Development lifecycle, exact command/task/evidence matrices,
duplicate-work counts, exact-tree reuse rules, targeted-validation safety map, and CP4B blockers.

### T0-1 scope and operator presentation principles

Start with a read-only inventory of every human-facing workflow/run/job/step label, PR and Release
title or heading, Development/Stable presentation, Environment/deployment label, supported Issue
surface, artifact label, generated comment, summary and CLI output. Record its producer, whether it
is a machine contract, rename risk, when the information is available versus displayed, and whether
multiline output should remain visible, collapse, move to logs/artifacts, be redesigned, or be
removed as duplicate. Do not rename or collapse before this inventory.

Human-facing text should read like product UI, using natural capitalization and spaces instead of
unnecessary underscores, hyphens, lowercase identifiers, classifier jargon, or redundant
`Mosaic —` prefixes. Machine identifiers remain machine-safe. The completed CP5 audit retained
`Full validation`: migrating it to `Prepare` would require a coordinated ruleset, provenance,
PR-evidence, upstream-evidence, test, and documentation change without a remaining UX benefit.

Every operation must state intent immediately and every completed stage its main result in one
concise line. Explain why conditional work runs, skips, reuses evidence, waits, refuses, or needs
more validation. Use `run identity/intent → progress → concise result → collapsed evidence`; move
technical detail to logs/artifacts by default, and explain minutes-long work before the wait.

T0-1 resolved that backlog with authoritative two-class PR validation, exact-tree main reuse,
bounded local Fast feedback, zero-input Signing Diagnostic, compact PR/Actions output, authenticated
Compare Changes links, and measured CP7 decisions. CP2 removed the obsolete inherited Development
Build and Create release surfaces; Baseline T0 deliberately does not own Appstore or Fire TV AAB
distribution. CP5 retained the established version/job/check identities after consumer audits, and
CP8 verified the resulting documentation and contracts. The retired `mosaic-release-signing`
Environment remains external operator cleanup only; no active repository consumer references it.

CP4A established that the clearest removable duplication is an explicit local Full immediately
followed by prepare-pr Full against the same mutable snapshot. Hosted PR validation remains the
authoritative committed/synthetic-merge gate; exact-tree reuse on protected main is already proven.
CP4B.1 repaired the case-sensitive `Run Full validation` versus `Run full validation` reuse-step
contract and added independent workflow-YAML-to-consumer coverage. PR #60 and required PR Full run
`34778154476` attempt `1` live-proved exact-tree evidence reuse: protected main reported that the
exact tree had passed, skipped fallback Gradle Full, classified the range tooling-only, and skipped
Sign/Publish. CP4B.2 now defines complete `NON_ANDROID` and `ANDROID_FULL` policy evidence, and every
PR runs the complete offline suite rather than a mapped subset.
Release assembly, isolated signing, Stable/Hold reauthentication, and fail-closed publication checks
remain distinct authority boundaries rather than duplicate validation.

The implemented CP4B.2 model is broad hosted authority with coarse gates: every PR runs
changed-range hygiene and the complete offline suite; Android/build/unknown scope also runs Full
Debug; successful PRs emit versioned exact tested-tree policy evidence as `NON_ANDROID` or
`ANDROID_FULL`; protected main reuses an exact authenticated match and otherwise runs the complete
conservative fallback. Prefer deleting duplicate stages and
state over making them smarter. The observed hosted offline suite is commonly tens of seconds
(approximately 18–24 seconds for about 232 tests), so an authoritative fine-grained test catalog is
not currently justified; retain Android as the materially expensive coarse gate.

PR #61 and protected-main run `34784224595`, attempt `1`, live-validated `ANDROID_FULL`. The exact
PR policy artifact from run `34783794209`, attempt `1`, authenticated the final main tree; main
skipped repository pre-commit, complete offline tooling, Android setup, and Gradle Full. Development
eligibility remained independent, classified the scope non-release/tooling-only, and produced no
APK. Full validation took about 11 seconds, Development eligibility about 12 seconds, and the full
protected-main run about 28 seconds. At that point, `NON_ANDROID` remained the only CP4B.2 hosted
acceptance gap.

PR #62 naturally closed that gap with a four-file documentation-only change. Its approximately
45-second PR CI passed changed-range pre-commit and the complete offline suite, correctly omitted
Android validation, and emitted reusable `NON_ANDROID` evidence. After the normal two-parent merge,
protected-main CI authenticated `pr-policy-v1` from PR run `34787725082`, attempt `1`, and identical
tested/final tree `e8fa31e0b771b98e136bdd85ee6c9ffa68b069b6e`. It reported that the exact tree had
passed `NON_ANDROID`, skipped pre-commit, offline tooling, Android setup, and Gradle, completed in
about 26 seconds, and independently skipped Development Sign/Publish. Both CP4B.2 classes are now
hosted validated.

Migration order is fixed: complete PR evidence, authoritative PR CI, and main reuse are implemented;
both evidence classes are live-proven; CP4B.3 removes routine broad local/prepare-pr duplication
while retaining explicit Full and exact scope/tree/publication integrity; simplify Signing Diagnostic separately. Preserve exact-tree
and required-CI trust, main fallback, final Release Build, version/sign/artifact/publication
boundaries, Stable/Hold authorization, upstream native Git/human authority, and native failed-job
recovery. Performance incidents and optimization remain CP7/T0-2 evidence, not CP4B.2 scope.

CP4B.3 makes Fast local feedback the prepare-pr default. Normal preparation performs changed-scope
hygiene, a narrow existing offline mapping and meaningful focused JVM filters when known, plus
whitespace checking; it does not fall back to complete offline or Android Full on high-risk/unknown
scope. Required PR CI owns authoritative `NON_ANDROID`/`ANDROID_FULL` coverage. Resolved upstream
candidates keep one meaningful focused pass and forced hosted Full instead of local Standard then
Full. Explicit `validate-local.ps1 -Level Full` remains supported for diagnosis/on-demand evidence.
The scope/snapshot/staged-tree/commit identity state remains because Git alone cannot reconstruct
the operator's reviewed dirty-tree intent; validation-result history and the unused root
`prepare-pr.log` compatibility surface are removed. PR #63 completed hosted acceptance: Fast local
feedback took about 4 seconds; its publication stage took about 8 seconds without routine broad
local Full; final PR CI passed changed-range pre-commit and the complete offline suite in about
1m08s, correctly omitted Android validation, and emitted `NON_ANDROID` evidence. Protected main
authenticated that exact-tree evidence, skipped repeated validation, independently reported no
Development build required, and completed in about 26 seconds. The two earlier PR attempts remain
useful evidence that authoritative hosted CI caught PowerShell diagnostic-presentation differences
without requiring any production prepare-pr safety change.

PR #64 then live-validated the committed-only publication follow-up. A clean branch ahead of
`origin/main` is audited and published using its complete existing commit scope and exact
`HEAD`/tree without manufacturing or amending a commit; clean/equal still refuses, dirty scope still
uses the normal Fast/stage/commit path, and native upstream identity safeguards remain mandatory.
Hosted PowerShell exposed three more presentation-sensitive refusal assertions, but the underlying
nothing-to-publish, remote-divergence/no-force, and missing native-merge-identity guards all behaved
correctly. One test-side normalization contract now removes the observed ANSI, wrapping, standalone
column-marker, and whitespace presentation before semantic assertions. The corrected PR passed
authoritative hosted validation and merged as `063ba3a26c7d93e5c90a6f0651ba11d273121b05`.

P04 is complete and hosted validated with native GitHub state rather than a custom scheduler.
Prepare-pr authenticates
the unique ordinary non-Draft PR's repository, base, branch and exact reviewed head, rereads it before
mutation, and requests merge-commit auto-merge with GitHub's expected-head guard. Existing matching
auto-merge is idempotent. Draft/upstream attention, stale, foreign, ambiguous and incompatible states
remain excluded or fail closed; GitHub protection and required CI remain merge authority. PR #65
verified `allow_auto_merge=true` and `allow_merge_commit=true`, armed exact head A but did not merge it
after required CI failed, then reused the same PR and armed corrected head B, which GitHub merged only
after authoritative CI passed. Protected main authenticated exact `ANDROID_FULL` `pr-policy-v1`
evidence and reused Full in approximately 11 seconds. Merge-commit/two-parent integration remained
intact, and prepare-pr used no administrative/direct bypass, force push, readiness change, or settings
mutation.

The tooling-support classification gap was corrected in PR #67 without weakening conservative
unknown-path behavior. PR #68 then made the already-computed PR Validation plan visible near the
start of authoritative CI and is **COMPLETE / HOSTED VALIDATED**. Its workflow-sensitive scope
truthfully selected conservative `ANDROID_FULL`; the signal appeared early, required CI passed,
native auto-merge waited for protection, protected main reused exact-tree evidence, and Development
eligibility independently determined release relevance. Existing job, step, evidence, reuse,
permission, and Release Build/Sign/Publish contracts did not change.

PR #70 completed and hosted-validated the prepare-pr terminal UX cleanup. Guided output now presents
six concise stages, places one clickable/fallback stage-log link on each RUN line, avoids duplicate
PASS/FAIL links, and applies the same rule to nested validation. Scope/classification, concise
diffstat, commit title, PR identity/link, auto-merge, required CI, expected hosted path, actionable
refusals, and the final run-log location remain visible; detailed identities and inventories stay in
escape-sequence-free forensic logs. During acceptance, an older-base branch correctly refused with
`Current branch does not descend from validated origin/main.` Rebasing onto current `origin/main`
resolved it without weakening ancestry protection. Required CI passed, native auto-merge merged PR
#70, and protected main preserved exact-tree evidence reuse.

The **Operator UX batch is complete and hosted validated**. Ordinary PR #72 presented compact scope,
classification, implications, prominent review-sensitive paths, collapsed exhaustive paths, and the
expected hosted path. Hosted CI selected `NON_ANDROID`; native auto-merge waited for required CI;
protected main authenticated exact PR evidence, reused it in about 10 seconds, independently
reported `No build required`, and skipped Development delivery.

A natural manual Upstream check against existing review candidate PR #58 proved the single-outcome
model. Observe showed only the authenticated handoff. Publish reported `29 upstream changes · review
candidate already open`, led with REVIEW and the exact PR #58 link, reused rather than duplicated
the candidate, and provided exact incoming navigation for `.github/workflows/pr.yml` while truthfully
reporting current Mosaic absent. Semantic REVIEW and Git textual conflict were distinct, and the
resolver remained the authenticated handoff entry point. The 1 attention / 26 FOLLOW / 2 preserved
context stayed visible but secondary. `Upstream check · Scheduled` and `Upstream check · Manual`
need no further change because GitHub supplies run number/time metadata.

This closes the bounded upstream outcome/candidate-body and ordinary generated PR-body items without
changing lifecycle, permissions, evidence, validation, ancestry, auto-merge, or release authority.
At the Operator UX checkpoint, the next grouped work was **Release / Diagnostic UX**. It would challenge Signing Diagnostic's manual
SHA and improve Development/Stable, Compare Changes, immutable archive emphasis, release wording and
redundant human-facing provenance without changing updater/tag/asset identities. CP5's optional
machine-contract renames remained a later explicit value decision at that checkpoint; the completed
audit below has since closed them as KEEP. CP7 performance and CP8 final consistency follow.

The **Release / Diagnostic UX batch is COMPLETE / HOSTED VALIDATED (PR #74)**. Signing Diagnostic
uses zero operator inputs and remains bound to the exact protected-main dispatch SHA and
`release-sign` approval. New Development and Stable bodies explain their channel roles, expose only
validated immutable comparisons, and keep provenance secondary. Immutable `downstream-build-N`
pages now identify themselves as permanent build/provenance records rather than update channels.
Stable approval and Hold summaries state the pending authorization and resulting updater-visible
state without changing any mutation rule. Current numeric Release names, tags, assets, updater
endpoints, workflow/job names, Environments, evidence and permissions remain exact.

P05/V02/V03/V04 are closed. Corrected PR run `34930521117` passed Android setup and actual Full
`defaultDebug` validation; native auto-merge merged only after required CI succeeded. Protected-main
run `34931217453` authenticated/reused the exact `ANDROID_FULL` evidence and independently reported
tooling-only/high, `releaseRequired=false`; no Development APK was required. The first head's setup
failure was inherited toolchain drift, repaired by removing only obsolete standalone SDK `tools`
while retaining `platform-tools`, Build Tools `36.0.0`, and NDK `29.0.14206865`, with an exact
regression contract. See the
[PR #74 acceptance ledger](T0_1_OPERATOR_UX_INVENTORY.md#pr-74-hosted-acceptance-and-android-setup-correction).

This closes implementation/integration acceptance, not artificial release mutations. Zero-input
Diagnostic and changed Development/Stable/archive/Hold visual surfaces await their next natural
operation; no mutation is needed merely to close the batch. Existing bodies remain immutable on
retry.

The completed V01/CP5 contract-and-consumer audit closed **V01 — KEEP / COMPLETE** and **CP5 —
COMPLETE / NO MIGRATION JUSTIFIED**. KEEP is the audited decision, not deferred implementation.
Channel-first presentation distinguishes Development from Stable while retaining the aligned
`1.0.N` APK/manifest/Release identity, `develop`, `downstream-build-N`, and `mosaic-v1.0.N`.
Development-specific version identity would complicate updater/history/recovery contracts and would
conflict with Stable exact-byte promotion; presentation-only identity would disagree with the APK.

H01-H06 are likewise closed as KEEP decisions: retain `CI / Full validation`; the clearer
Development-qualified Build/Sign/Publish job names; authenticated artifact names; current
release/tag/asset identities; downstream-owned absence of inherited publishers; and least-privilege
Upstream Sync workflow-file handling. These identities have live ruleset, evidence, provenance,
updater, ownership, or permission consumers. Any broader workflow-file publication authority belongs
to T0-2, not cosmetic migration.

CP7 measured offline tooling, setup, Gradle/cache, validation, release, queue, and Environment wait
separately. PR #77 hosted-validated the bounded fixture optimization: the PR took about 7m06s, with
88s of offline tooling, 29s of Android setup, and 4m42s of Gradle Full. Protected main reused exact
PR evidence in 11s, evaluated Development eligibility in 10s, required no build, and completed in
27s. One faster Gradle sample does not justify a Gradle-contract change, and natural upstream runs
are already seconds-class. No remaining candidate met CP7's high GO bar. **CP8 — Final consistency**
is complete; T0-2 is next.

Hosted CP2 evidence informed CP7's separation of runner queue, setup, cache/tool installation,
test/build execution, Environment wait, and publication timing. T0-2 owns recurrence-based
clean-host dependency reproducibility and the authority decision for genuine upstream workflow-file
candidates. The preferred default is manual handling rather than broader App permission unless a
concrete automation requirement is demonstrated. Sanitized actionable push diagnostics are deferred
to T0-2 security/operational review; the current generic publication error is insufficient because
`git push --porcelain` rejection detail may arrive on stdout as well as stderr.

CP3 removes redundant prefixes from the presentation-only Stable, Signing Diagnostic, and Upstream
workflow names and gives CI, Development eligibility/publication, Stable, Hold, Signing Diagnostic,
and Upstream operator-first summaries. Main outcomes, approval/refusal states, and next actions stay
visible; raw classifier paths and SHA/tree/run/artifact/provenance evidence are collapsed without
changing their machine forms. `CI`, `Full validation`, and the three Development job/check names
remain unchanged contracts. Sanitized Git push rejection capture was reclassified as operational
error-handling work for CP6 or T0-2 security review, not folded into cosmetic CP3 changes.

### T0-2 audit contract

The comprehensive architecture, correctness, security, build, delivery, upstream, test, and
application-boundary audit is **complete** and the implementation scope is **approved**. This
section is its durable decision and negative-knowledge record rather than a duplicate operating
manual. T0-2 now contains exactly:

1. **C1 — GitHub merge-commit only — COMPLETE / LIVE VERIFIED:** merge commits are enabled and
   squash/rebase are disabled, so human-reviewed upstream native ancestry cannot be discarded.
   Do not build a configuration doctor; T0-3 will document an operator settings checklist.
2. **C2 — Explicit build bootstrap — IMPLEMENTED / LOCAL VALIDATED; HOSTED ACCEPTANCE PENDING:**
   add the official SHA-256 for Gradle 9.6.1 and explicitly provision the default-channel base
   package `platforms;android-37.0` for `compileSdk = 37`.
3. **C3 — Sanitized upstream failure diagnostics:** retain a bounded actionable publication failure
   reason without changing authority, permissions, identity, mutation, retry, or lifecycle state.

C1 was verified live on 2026-09-17. Repository settings expose merge commits as the only enabled
merge method and retain native auto-merge. The unchanged active `main protection` ruleset still
requires pull requests, resolved conversations, and `Full validation`; blocks deletion and
non-fast-forward updates; and has no bypass actors. Its allowed-method list remains the pre-C1
`merge`, `squash`, `rebase` set, but repository-level settings make only `merge` available. Existing
prepare-pr, exact-tree reuse, and upstream-resolution contracts already enforce and test the native
two-parent shape, exact head/tree, Draft authority, and no-force behavior, so C1 required no
repository file change beyond this continuity record. These external settings remain manually
administered and belong in the T0-3 operator checklist.

C2 pins the official Gradle `9.6.1-bin` distribution SHA-256
`9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14` and adds
`platforms;android-37.0` to the shared Android setup. The first hosted C2 attempt proved that the
unversioned `platforms;android-37` identifier does not exist in the SDK Manager catalogue;
earlier successful builds used the same runner image's preinstalled `android-37.0` platform. Gradle
version, distribution type/URL and wrapper JAR remain unchanged; Build Tools remain `36.0.0`, NDK
remains `29.0.14206865`, and standalone `tools` remains absent. Focused setup/bootstrap checks and
Fast pass locally. Clean-runner `ANDROID_FULL` acceptance of the corrected package remains required
before C2 is complete; C3 remains next afterward.

After required acceptance for C1, C2, and C3, mark T0-2 complete and begin T0-3. Do not add another
T0-2 checkpoint without new concrete evidence and explicit approval. The audit's KEEP, WATCH,
FEATURE, and FUTURE conclusions remain closed; in particular, T0-2 does not authorize application,
provenance, CI, performance, dependency-hardening, monitoring, or documentation redesign.

The accepted disposition is:

- **KEEP:** current application boundaries; exact-tree validation and conservative fallback;
  release, signing, Stable, Hold, provenance, native-rerun, upstream-ownership and semantic-review
  authority; `waiting_on_existing_pr`; realistic Git fixtures; compatibility; and least privilege.
- **WATCH:** the one-off clean-host dependency-resolution incident, measured Gradle/Android costs,
  and acceptance cases requiring a naturally occurring real-world condition. New evidence or
  recurrence is required before reopening them.
- **FEATURE:** product-specific architecture, behavior, and tests belong to resumed feature work,
  not baseline infrastructure redesign.
- **FUTURE:** byte-for-byte build reproducibility, generalized security tooling, and automated
  settings monitoring require demonstrated need and separate approval. T0-3 owns documentation
  navigation and the concise external-GitHub-settings operator checklist.

There is no T0-2 application refactor, provenance or CI redesign, further performance work,
dependency-hardening program, configuration doctor, scheduled settings monitor, documentation
consolidation, or knowledge graph. The existing architecture was challenged rather than retained by
default; the surviving mechanisms each protect a reviewed consumer or authority boundary.

### T0-3 documentation contract

After T0-2, create a navigable `docs/README.md` hierarchy separating current source of truth from
historical evidence. Classify every document as current, historical, duplicate, stale/superseded,
agent-specific, or roadmap/backlog, and give unique material a destination before deletion. Make
`CODEX_HANDOFF.md` concise current-work continuity. Each major workflow page must describe purpose,
triggers, happy/alternate/failure paths, run/skip/reuse/refusal conditions, human actions,
recovery, terminology and related workflows, with Mermaid diagrams. Cover PRs, validation/evidence
reuse, Development, signing, Stable, Hold, failed-job recovery, Upstream Synchronization and
FOLLOW/REVIEW/conflicts, channels, version/provenance and updater behavior. Include whole-system,
per-action, upstream-decision and trust-boundary diagrams. Links supplement—never replace—the
immediate explanation in Actions, PRs and Releases.

### Baseline T0 acceptance

```text
T0-1 Cleanup & Operator Experience        COMPLETE
T0-2 Engineering / Process Audit          COMPLETE
T0-2 blocking findings                    RESOLVED / ACCEPTED
T0-3 Documentation / Wiki / Roadmap       COMPLETE
I06                                       COMPLETE / LIVE VALIDATED
I07                                       COMPLETE / LIVE VALIDATED
No known issue blocks Baseline T0
Current architecture navigable from docs/README.md
```

Only then declare **BASELINE T0** and resume feature development. Subsequent infrastructure work is
demand-driven by real feature needs or demonstrated defects rather than speculative redesign.

## Product Direction

The goal is to make Wholphin feel like one integrated media application rather than a Jellyfin client with isolated add-ons.

Jellyfin remains the source of truth for the local/playable library, while Seerr extends Wholphin with discovery, requests, watchlists, acquisition state, and unavailable-content awareness.

Longer term, features such as Discover, Watchlist, Collections, acquisition status, library availability, and external metadata should share a common product model and UI language. When the extended feature set is disabled, normal Wholphin/Jellyfin behavior should remain unaffected.

---

# What We've Done So Far

## Seerr / Acquisition Integration

- Added Seerr-backed acquisition tracking for movies, TV series, seasons, and episodes.
- Added acquisition/download lifecycle information to Wholphin.
- Added a Downloads page for active and recently completed acquisitions.
- Added Jellyfin readiness detection so an acquisition is not considered usable merely because Seerr reports it as available.
- Track playable Jellyfin episode IDs for TV acquisitions.
- Added Jellyfin season item IDs to TV readiness data.
- Implemented direct navigation from an Available TV season on Downloads to the corresponding Jellyfin season instead of only opening the series.
- Preserved normal failure warnings while cleaning up temporary acquisition tracing/debugging.
- Extracted TV acquisition calculations/projection logic from UI/service code into a dedicated data-model layer.
- Improved separation between Seerr acquisition state and Jellyfin playback readiness.

## Series / Season Availability Direction

The series experience is being expanded beyond the contents currently present in Jellyfin.

The intended model is:

- Available seasons appear normally.
- Missing/unavailable seasons can still be represented.
- Seerr supplies request/availability information for content that is not yet in Jellyfin.
- Partially available series can expose their missing seasons directly instead of forcing the user through a separate Discover flow.

## Development / Architecture

- Wholphin fork and Android/Kotlin development environment established.
- Feature work is being designed as one coherent optional product capability rather than permanently independent feature islands.
- Cross-feature state is expected to be reusable throughout Wholphin: library availability, watchlist state, acquisition state, collection membership, and similar metadata should eventually be available wherever a media item is rendered.

---

# Current Product Architecture Direction

## Unified Extended Experience

Eventually the Seerr/discovery-related work should be exposed through a single high-level feature toggle.

When enabled, Wholphin gains an extended media experience.

When disabled, the base Jellyfin experience should continue normally without dependencies on Seerr-specific functionality.

The extended layer should provide reusable state such as:

- In Jellyfin library
- Partially available
- Missing/incomplete
- Requested
- Pending
- Downloading
- Available but waiting for Jellyfin
- On watchlist
- Collection/franchise membership
- Upgrade available

This information should not belong exclusively to one screen.

### Enhanced-feature boundary and gating

The high-level toggle applies only to capabilities added by this fork on top of upstream Wholphin. It is not a Seerr enable/disable switch. Upstream Wholphin already provides Seerr-backed Discover, search results, request history, request submission/cancellation, Discover details, availability indicators, and similar-title/person enrichment; those remain governed by the existing Seerr configuration and reachability model.

The initial master switch controls the fork's Downloads/acquisition tracking, immediate local Queueing, acquisition progress and problem presentation, merged Jellyfin + Seerr season placeholders and request-more-seasons behavior on Jellyfin Series details, the added series availability header, and contextual incomplete-season diagnostics. Independent fixes such as numeric season ordering, exact-ID navigation, refresh corrections, and request-dialog focus/presentation improvements remain active when the master is off.

User intent is stored in protobuf DataStore and resolved through semantic capabilities rather than raw Boolean checks at every consumer. Initially all capabilities follow one master value; later Downloads, acquisition progress, missing seasons, integrity, and related capabilities may gain subordinate overrides without changing consumers. Seerr configuration, temporary reachability, and enhanced-feature enablement remain separate facts.

Disabling the feature family stops enhanced background work, hides Downloads, removes enhanced Series projections and presentation, and prevents acquisition-only request side effects without deleting server configuration or durable expectation caches. Re-enabling resumes eligible foreground work and enrichment. A future shared media-product-state coordinator should consume only enabled enhanced sources while keeping library/playability, request/availability, acquisition, integrity, watchlist, and quality as independent dimensions.

For example:

- A Jellyfin library card can indicate that an item is on the watchlist.
- A Discover card can indicate that an item is already available locally.
- A Collection can contain available and unavailable movies.
- A Series page can show available and unavailable seasons together.
- Suggestions can expose availability/request state without navigating elsewhere first.

---

# Roadmap

## Phase 1 — Acquisition Foundation

**Status: implemented; remaining work is UX and extended real-world hardening**

Build the common acquisition model connecting Seerr state to Jellyfin readiness.

Includes:

- Seerr acquisition tracking
- Downloads page
- Movie/TV acquisition lifecycle
- Jellyfin readiness
- Episode readiness
- Season-aware navigation
- Recently completed acquisition history
- Seven-day rolling Recently Completed retention
- Cold-start Jellyfin readiness and navigation rehydration
- Shared TV acquisition projection/model

Remaining work in this phase mainly concerns extended real-world resilience testing and UI polish.

---

## Phase 2 — Complete Series Experience

Turn the Jellyfin series page into a complete representation of the show rather than only the seasons currently downloaded.

Planned experience:

- Show all known seasons.
- Render available Jellyfin seasons normally.
- Render unavailable seasons as placeholders/greyed cards.
- Allow unavailable seasons to be requested directly.
- Show series-level Seerr availability such as **Partially Available**.
- Show production status such as **Continuing** or **Ended**.
- Make partially available seasons actionable.
- Avoid unnecessary navigation through Discover simply to request another season.

### Enhanced Request Popup

Improve the existing request flow:

- Add/clean up **More Seasons** handling.
- Exclude fully available/non-actionable seasons.
- Keep partially available seasons when additional episodes are still requestable.
- Preserve pending/requested semantics.
- Keep the initially selected season visible separately.
- Do not duplicate the selected season under More Seasons.

---

## Phase 3 — Persistent Expectations and Live Integrity

**Status: implemented as contextual diagnostics**

Acquisition history and library integrity are different concepts.

A download disappearing from Downloads history must not cause Wholphin to forget trusted released-episode expectations.

Persist only the released-episode expectation needed for later evaluation. Calculate the current result from that expectation, a fresh Jellyfin playable-episode inventory, and current acquisition coverage.

Example:

> **Incomplete — 3 episodes missing**

Persist:

- Exact expected released episode numbers
- User, series, season, and TMDB identity
- Expectation update time

Do not persist live playable counts, missing numbers, or complete/incomplete conclusions. A season is shown as incomplete only when a live evaluation finds expected released episodes missing from Jellyfin and current acquisition evidence does not account for them.

### Contextual Live Evaluation

When a series is opened or explicitly refreshed, evaluate cached expectations against fresh Jellyfin state. Fetch detailed Seerr season metadata only when an expectation is absent or deliberately refreshed.

Supported triggers:

- Opening a series
- Explicitly refreshing the series

When missing episodes become playable, the next live evaluation clears the warning. Wholphin does not continuously crawl the library or repair backend state.

---

## Phase 4 — Acquisition UX

Make downloading feel native to Wholphin rather than like a remote request submitted to another application.

### Acquisition visibility outside the local library

Normal **Movies** and **Series** library grids remain Jellyfin-local: requested or acquiring catalog items must not be injected as synthetic library entries before Jellyfin discovers them. Active acquisition instead appears on separate transient surfaces:

- **Home — Acquiring:** the primary lightweight view of current Queueing, Queued, In progress, and Finishing items. The row should appear above existing Home content such as Recently Released and Recently Added, and disappear completely when empty so ordinary content shifts upward naturally.
- **Downloads:** the operational source for all active acquisition plus the existing rolling recent-history window, including detailed lifecycle, reliable progress, and timing.
- **Watchlist:** the future persistent catalog surface for items the user cares about, whether local or non-local.
- **Discover:** the exploration and request surface.
- **Movies / Series:** may later add their own distinct Acquiring rows if useful, but those rows must remain separate from the normal Jellyfin-local grids.

Movie cards may expose exact lifecycle or reliable progress. Home Acquiring represents TV as one logical card per active season, using canonical season progress without aggregating across seasons. Other series-poster surfaces may use only the coarse `SeriesAcquisitionSummary`; Series Details and Downloads retain richer exact per-season state.

The intended transition is **Discover / Watchlist → Acquiring → Jellyfin discovery → Recently Added / normal library**. Once Jellyfin discovers an item, it joins the local library naturally while shared media identity/state continues following the same logical item. The transient rows require no new persistence and remain hidden when enhanced acquisition tracking is disabled or no acquisition is active.

### Download Progress

- Show acquisition/download progress in Wholphin.
- Add a compact progress bubble/overlay.
- Display percentage where reliable progress information is available.
- Allow the user to inspect active downloads.

### Completion Feedback

- In-app notification when requested content becomes available.
- Clearly distinguish:
  - Requested
  - Pending
  - Downloading
  - Importing/processing
  - Available in Seerr
  - Ready to play in Jellyfin

### Disk Space Awareness

Before requesting/downloading content:

- Detect available storage where possible.
- Warn when free space is low.
- Potentially estimate whether the requested content is likely to fit.
- Avoid blocking requests unnecessarily when size cannot be estimated reliably.

---

## Phase 5 — Watchlist

Add a dedicated Seerr/Jellyseerr watchlist experience.

Planned features:

- Dedicated navigation destination
- Movie/Series filtering
- Sort by date added
- Sort by release year
- Genre grouping/filtering
- Collection/franchise grouping
- Persist preferred view locally, or server-side where supported

Watchlist membership should become reusable metadata throughout Wholphin.

Examples:

- Show a watchlist indicator on Jellyfin library cards.
- Show watchlist state in Collections.
- Show watchlist state in Discover and suggestions.

---

## Phase 6 — Collections & Franchises

Create a richer collection experience that includes content outside the current Jellyfin library.

### Movie Collections

Show:

- Available collection items
- Unavailable collection items
- Request state
- Download state

Optionally include completely unavailable collections when at least one item or related title is on the user's watchlist.

Jellyfin and Seerr/TMDB collection identifiers can be used to correlate collection membership where appropriate.

### Franchise Relationships

Go beyond strict TMDB box sets.

Examples:

- Alien → Aliens → Predator → AVP → Prometheus
- Iron Man → Avengers → wider MCU
- Batman/Superman → Justice League → wider DC universe

This introduces the concept of related media universes rather than relying only on formal movie collections.

---

## Phase 7 — Discover

Build a richer Discover landing page.

Proposed primary sections/tabs:

- Movies
- Series
- Animation / Anime
- Franchises

### Fresh

**Status: configurable application-managed backend, Discovery Sources settings, Discover row, and opt-in card indicator implemented locally in Seerr; Wholphin consumption deferred.**

Fresh is the transient intersection of recent Seerr Discover movies/series and retained releases belonging to a selected autobrr filter. It signals that recent media appeared in the user's chosen release feed; it does not imply Watchlist intent, a request, acquisition, playback readiness, permanent availability, or a particular quality level.

A shared rebuildable in-memory projection uses typed TMDB identity and supports an authenticated ordered media API plus cheap membership indicators. Autobrr owns release policy/history; Seerr owns recent candidate selection. No permanent release-observation or tracker-availability database is introduced. Current matching is conservative title/alias matching with movie year discrimination over a rolling 90-day window. Ordering uses the earliest retained qualifying observation within that window.

Seerr now persists write-only-token configuration, runs one non-overlapping scheduled coordinator, retains the last good snapshot as stale during transient failures, and exposes admin status/test/refresh controls plus `/api/v1/fresh`. The fixed Discover Fresh row consumes that API with normal movie/series cards and preserves backend ordering. A shared typed frontend membership index also supplies an opt-in Fresh indicator to ordinary Discover media sliders without per-card requests. Administrators can configure the autobrr connection/filter and normalized rolling movie/TV Discover candidate policies under Discovery Sources. Remaining work is Wholphin consumption. Fresh stays optional; ordinary Discover works when its source is disabled or unavailable.

### Browse By

Possible discovery dimensions:

- Genre
- Decade
- Year range
- Popularity
- Recently released
- Highly rated
- Franchise
- Collection
- Studio
- Network
- Streaming origin
- Country
- Language

Example decade/year-range presentation:

- 1970–1979
- 1980–1989
- 1990–1999
- 2000–2009
- 2010–2019
- 2020–present

### Viewing Order

Franchises can expose alternative viewing orders:

- Release order
- Chronological order

This is especially useful for franchises such as Star Wars and large cinematic universes.

---

## Phase 8 — Smart Suggestions

Use the combined Jellyfin + Seerr + watchlist + collection model to make suggestions more useful.

Potential examples:

- Continue a franchise
- Next unavailable movie in a collection
- Next season of a series
- Related franchise
- Movies connected to something recently watched
- Watchlisted titles that have recently become available
- Missing entries between already-owned franchise titles

Suggestions should understand whether content is:

- Playable now
- Missing
- Requested
- Downloading
- On the watchlist

---

## Phase 9 — Automatic Next-Season Acquisition

Optional automation for users who want Wholphin to keep a currently watched series ready.

Potential behavior:

1. User approaches the end of the current season.
2. Wholphin determines that a later season exists.
3. The next season is not locally available.
4. Wholphin optionally requests it through Seerr.

Needs safeguards:

- Explicit opt-in
- Configurable completion threshold
- Never duplicate an existing request
- Respect pending/downloading state
- Do not request specials accidentally
- Handle continuing versus ended series
- Allow per-series disable/override

---

## Phase 10 — Quality Upgrades

Allow users to request a better version of content they already own when Seerr/Sonarr/Radarr supports a higher quality profile.

Examples:

- 720p → 1080p
- 1080p → 4K

Potential UI:

> **Upgrade available**

The action should clearly distinguish upgrading an existing item from requesting missing content.

---

## Phase 11 — External Player Improvements

Continue validating external-player integration, particularly for devices such as the Zidoo Z9X 8K.

Observed behavior already indicates that playback/resume position can return to Jellyfin even though Jellyfin may not display an active playback session in the dashboard.

Future work:

- Verify resume reporting consistently.
- Verify watched/completed state.
- Investigate richer playback progress reporting where the external player permits it.
- Investigate chapter information/thumbnails exposed through Jellyfin APIs.
- Preserve compatibility with native/external player choices.

---

## Phase 12 — External Lists & Metadata

Investigate optional list integration with services such as:

- Trakt
- IMDb
- TMDB
- Other compatible list providers

Potential uses:

- Import a list into Watchlist
- Browse external lists in Discover
- Turn lists into request queues
- Use curated lists as recommendation sources
- Cross-reference library availability

Integrations should remain optional and should not become requirements for normal Wholphin operation.

---

# TODO

## Acquisition / Downloads

- [x] Implement Seerr acquisition model.
- [x] Implement Downloads page.
- [x] Track Jellyfin readiness separately from Seerr availability.
- [x] Track playable Jellyfin episodes for TV acquisitions.
- [x] Retain Jellyfin season IDs in TV readiness data.
- [x] Navigate Available TV season Downloads entries directly to the Jellyfin season.
- [x] Extract shared TV acquisition projection logic.
- [x] Preserve acquisition/readiness orthogonality in Downloads: active upgrade/replacement work wins over recent-ready history, Seerr availability alone does not prove readiness, and operational lifecycle/progress matches the shared card projection.
  - [x] External Standard validation passed for the Downloads lifecycle checkpoint (targeted JVM tests, production Kotlin compile, acquisition model/tracker, Seerr pagination, Downloads page, and whitespace checks; `00:04:53.6766272`).
- [x] Remove temporary acquisition tracing/debug surface.
- [ ] Review acquisition lifecycle edge cases after extended real-world testing.
- [ ] Add in-app download/acquisition progress indicator.
  - [x] Manually evaluate the provisional Series season-card bottom-edge progress rail; Android TV lifecycle validation passed across Queueing, Queued, In progress, temporary queue absence, resumed progress, Finishing, simultaneous seasons, and placeholder-to-Jellyfin transition.
- [x] Add a transient Home **Acquiring** row above existing Home content.
  - [x] Add the reusable Home acquisition projection/source without migrating Home UI.
  - [x] Checkpoint 1 passed external Standard validation (targeted JVM tests, production Kotlin compile, acquisition model/tracker, Seerr pagination, Downloads page, and whitespace checks; `00:06:27.2001194`).
  - [x] Integrate the transient row as a separately keyed leading Home item without changing configured Home rows or settings.
  - [x] Migrate Home TV acquisition from coarse series posters to exact season-scoped cards without additional network work.
  - [x] Complete Android TV focus, lifecycle, and navigation validation; mixed/movie/TV and focus-removal fixture scenarios passed.
  - [x] Keep normal Movies and Series grids Jellyfin-local; do not inject synthetic catalog entries.
  - [x] Hide the row completely when no qualifying acquisition is active or enhanced acquisition is disabled.
  - [x] Use exact lifecycle/progress for movies where safe and exact canonical season lifecycle/progress for TV without cross-season aggregation.
  - [x] Append a stable end-of-row arrow that opens the existing Downloads operational surface; navigation and return-to-Home behavior passed Android TV validation.
  - [x] Consolidated acquisition/Home foundation passed external Full validation on 2026-09-07: production Kotlin compile, complete default-debug JVM unit suite, default-debug APK assembly, and Git whitespace check (`00:01:21.5762407`).
- [ ] Consider separate Movies / Series **Acquiring** rows after evaluating the Home surface.
- [ ] Add completion notification.
- [ ] Investigate disk-space warning before acquisition.

## Library Integrity

- [x] Persist released-episode expectations independently of Downloads history.
- [x] Read current playable episode inventory from Jellyfin during contextual evaluation.
- [x] Calculate missing episode numbers ephemerally rather than persisting a conclusion.
- [x] Exclude episodes covered by current authoritative acquisition evidence.
- [x] Evaluate integrity when a series is opened or explicitly refreshed.
- [x] Clear the warning naturally when a later contextual evaluation finds all expected episodes playable.
- [x] Reject persistent complete/incomplete snapshots and continuous whole-library auditing; they become stale without an authoritative event stream.

## Series

- [x] Show all known seasons on the series page.
- [x] Add unavailable/placeholder season cards.
- [x] Request a missing season directly from its placeholder.
- [x] Add **Partially Available** series badge.
- [x] Order season tabs deterministically by numeric Jellyfin season number.
- [x] Refresh the materialized Jellyfin season list while preserving exact-ID selection when possible.
- [ ] Add **Continuing / Ended** production-status badge.
- [ ] Improve More Seasons request popup.
- [ ] Exclude fully available/non-actionable seasons from More Seasons.
- [ ] Preserve actionable partially available seasons.
- [ ] Avoid duplicate selected season in More Seasons.

## Watchlist

- [ ] Add dedicated watchlist navigation destination.
- [ ] Add Movie/Series filter.
- [ ] Sort by date added.
- [ ] Sort by year.
- [ ] Add genre filtering/grouping.
- [ ] Add collection/franchise grouping.
- [ ] Persist preferred view configuration.
- [ ] Expose watchlist state on media cards outside the Watchlist page.

## Collections / Franchises

- [ ] Add dedicated movie Collections page.
- [ ] Combine available and unavailable collection items.
- [ ] Add option to surface unavailable collections related to watchlisted content.
- [ ] Reuse TMDB collection IDs where appropriate.
- [ ] Design broader franchise/universe relationship model.
- [ ] Add release-order browsing.
- [ ] Add chronological-order browsing.

## Discover

- [ ] Design new Discover landing page.
- [ ] Add Movies tab.
- [ ] Add Series tab.
- [ ] Add Animation/Anime tab.
- [ ] Add Franchises tab.
- [ ] Add Browse by Genre.
- [ ] Add Browse by Decade/year range.
- [ ] Explore studio/network/country/language browsing.
- [ ] Show local availability directly on Discover cards.
- [ ] Show request/download state directly on Discover cards.

## Smart Features

- [ ] Design unified media-state model reusable across screens.
- [ ] Add collection/franchise-aware suggestions.
- [ ] Add availability-aware suggestions.
- [ ] Add watchlist-aware suggestions.
- [ ] Design automatic next-season acquisition.
- [ ] Add opt-in and safeguards for automatic requests.
- [ ] Investigate quality-upgrade actions.
- [ ] Detect available higher quality profiles.

## Playback

- [ ] Validate external-player resume behavior across more scenarios.
- [ ] Validate watched/completed reporting.
- [ ] Investigate external-player progress reporting.
- [ ] Investigate Jellyfin chapter thumbnail availability to external players.

## External Integrations

- [ ] Investigate Trakt list integration.
- [ ] Investigate IMDb list integration.
- [ ] Investigate TMDB list integration.
- [ ] Define import versus live-sync behavior.
- [ ] Ensure external integrations remain optional.

## Architecture / Product

- [x] Define the upstream/base boundary and semantic capability-gate architecture.
  - [x] Implement and validate the documented high-level capability gate for fork-added enhanced features.
  - [x] Confirm at runtime that disabling it preserves upstream/base Wholphin surfaces and behavior.
  - [x] Add and validate an incremental shared media-identity foundation and read-only acquisition index.
  - [x] Add and validate an incremental read-only integrity source keyed by shared media identity.
  - [x] Add and validate a read-only acquisition + integrity product-state coordinator and migrate one Series season-card projection.
  - [x] Add an indexed coarse series-acquisition summary foundation without migrating a card or page.
- [ ] Centralize library/request/acquisition/watchlist state.
- [ ] Make shared media state available to cards across Library, Discover, Watchlist, Collections, and Suggestions.
  - [ ] Avoid feature-specific duplicate implementations of the same media-state logic.

## Downstream repository maintenance standardization

Wholphin is the reference implementation for a shared maintained-downstream operating model that will later extend to Seerr, the three currently pending repository activities, and future modified services.

### Completed foundations

- [x] Protect `main` with pull-request-only integration, a required `CI / Full validation` check, and blocked force pushes/deletion.
- [x] Establish purpose-specific branches, dedicated upstream-sync branches, repository-specific Fast/Standard/Full validation, and local-to-CI parity.
- [x] Establish fork-owned, read-only PR CI and guard inherited upstream development and stable-release publishers from running in the downstream fork.
- [x] Establish safe manual upstream synchronization with deliberate semantic conflict resolution.
- [x] Maintain authoritative repository-local agent, handoff, roadmap, upstream-sync, and PR-preparation documentation.
- [x] Implement and dogfood the guarded Wholphin `prepare-pr` v1 workflow, including complete PR-scope review, Git-native snapshot/tree verification, validation, exact staging, commit verification, safe push/PR creation, and persistent diagnostics.

### Target operating architecture

Wholphin defines the safety/workflow contract: one appropriate protected integration branch with equivalent guarantees. It does not mandate `main`, PowerShell, Gradle, identical CI, or identical release mechanics across downstream repositories.

| Owner | Responsibilities |
| --- | --- |
| Local / Codex | Implementation, fast/high-value feedback, downstream workspace safety, minimum useful pre-publication validation, autonomous publication after explicit user authorization. |
| GitHub | Authoritative PR CI, native auto-merge after protection, exact-tree protected-main reuse/fallback, PR Debug artifacts, automatic Development Build/Sign/Publish, zero-input Stable Promotion/Hold, and hosted Upstream Synchronization. |
| Human | Publication authorization, semantic/product judgment, Android TV/manual runtime validation where required, Environment approvals, and merge/reject authority for upstream REVIEW/conflict Drafts. |

Prefer deterministic GitHub/tooling for objective checks. Evaluate established tools before building custom alternatives; future agents assist fuzzy/semantic analysis and never replace deterministic validation. Ordinary work has one pre-publication human boundary before prepare-pr arms native auto-merge; upstream REVIEW/conflict Drafts retain a later human merge/reject boundary. [PREPARE_PR.md](PREPARE_PR.md) owns the detailed workflow.

### P0 - immediate publication handoff

- [x] Complete, validate and integrate Autonomous PR handoff v2 through PR #9; it is CURRENT on `main`.
- [x] Establish authenticated `gh` as the standard GitHub publication interface and dogfood successful PR creation.
- Keep prepare-pr thin and autonomous after explicit publication authorization. Preserve scope/tree checks, required validation, PR-only integration and inherited publisher guards as ongoing invariants.

### P1 - completed: move toil off the workstation

Current Mosaic release sequence:

1. **Permanent signing identity - COMPLETE / LIVE VALIDATED.**
2. **Updater routing - COMPLETE / LIVE VALIDATED.**
3. **Rolling development release - COMPLETE / LIVE VALIDATED.**
4. **Live device in-place update acceptance - COMPLETE / LIVE VALIDATED.**
5. **Stable promotion + channel UX - COMPLETE / LIVE VALIDATED.**
6. **Automatic Development delivery - COMPLETE / LIVE VALIDATED.**

Upstream Sync publication, Item 6 consolidation, and T0-1 simplification are complete. The
historical evidence below records how the final operating model was reached.

[Automatic acceptance](MOSAIC_DEVELOPMENT_RELEASE.md#historical-automatic-development-and-channel-migration-acceptance)
records PR #20, automatic release #2, v1.0.8/build-8 and in-app 1.0.5 -> 1.0.8 with preserved
settings and Development migration. Stable build-5 promotion is live validated and remains
manual; Development is continuous after authoritative main CI. See the follow-up phase below.
Completed evidence and remaining work:

- [x] Merge hosted upstream detection/candidate preparation on `main`, with isolated normal merges, exact-SHA deduplication, durable blocked issues, and offline safety tests.
- [x] Establish **Detection: OPERATIONAL**. First manual smoke [run 34281315948](https://github.com/constbogdan/Wholphin/actions/runs/34281315948) succeeded with `no_delta`, zero incoming commits and successful ancestry validation. Exact SHAs and external App setup are recorded in [UPSTREAM_SYNC](UPSTREAM_SYNC.md#hosted-upstream-synchronization-v2).
- [x] **I06 native Upstream Sync: COMPLETE / LIVE VALIDATED.** PR #55 proved a genuine clean FOLLOW range through native two-parent candidate, required PR Full, human merge, unchanged candidate/tested/main tree, accepted upstream ancestry, exact-tree protected-main reuse, Development publication, and subsequent quiet `no_delta`. Checkpoint 5 removed the duplicated Issue/journal/finalizer/priority lifecycle while preserving ownership, Draft/conflict, orphan, retry, provenance, least-privilege and required-CI safety. Natural textual-conflict live acceptance remains opportunistic evidence, not an I06 completion blocker.
- [x] Implement PR-only universal defaultDebug artifacts from successful Full CI, reusing its existing APK with seven-day retention, head/tested/base SHA metadata and a job-summary download link. OPERATIONAL + LIVE VALIDATED through PR #12 / run 34284819578: the exact artifact was downloaded, installed and run on the emulator after removing an old Debug installation. See [retrieval and install instructions](CODEX_HANDOFF.md#pr-debug-apk-artifacts-2026-09-09).
- [x] Implement approved Mosaic technical identity (`io.github.constbogdan.mosaic`, Debug `.debug`, upstream Kotlin namespace retained) and frozen-epoch first-parent versions (`1.0.N`). Signing is live-validated below; updater routing and rolling development delivery are live validated; stable promotion is live validated; visual branding remains pending. See [implementation boundaries](CODEX_HANDOFF.md#mosaic-technical-identity-and-versions-implemented-2026-09-09).
- [x] Persist the [downstream Release identity proposal](CODEX_HANDOFF.md#downstream-release-identity-contract-proposal-2026-09-09): separate app ID, owned Release key, anchored first-parent version sequence, common updater source and exact-artifact promotion. Identity/version allocation is approved and implemented; signing is live-validated; manual development publication is live validated through unsigned recovery; automatic Development after exact-main CI is live validated; Stable stays manual.
- [x] Prepare [Mosaic signing infrastructure](MOSAIC_SIGNING.md): explicitly unsigned Gradle Release builds, public fingerprint policy/verifier and user-only custody/restore instructions. The manual exercise remains available; normal Development now uses the isolated signer after successful main CI. Identity/versioning is operational on main.
- [x] Permanent Mosaic Release signing identity established: user confirms two independent encrypted backups and successful restore/hash/certificate/private-key-access verification. Only the public SHA256 is recorded in the pinned signing policy.
- [x] Historical signing isolation was proven with `mosaic-release-signing`; active Development signing now uses the main-restricted `release-sign` Environment with the same four secrets. No active workflow/script references the retired Environment, which may be deleted manually after operator review.
- [x] **Permanent signing / first signing acceptance COMPLETE + OPERATIONAL:** run 34323962085, source `055dde77b00c9b6e814d1115422bc60f8fd334b3`, Mosaic 1.0.3/code 3. Hosted signing, independent certificate/package/provenance verification and emulator installation succeeded. Build ~16m43s, signing job ~34s. See [acceptance evidence](MOSAIC_SIGNING.md#first-permanent-release-signing-acceptance). That exercise published no GitHub Release. Its preserved 1.0.3 installation has since been updated in place to 1.0.5 through Mosaic; see delivery acceptance above.
- [x] Implement shared Mosaic updater source routing for checks, APK metadata and installed-version notes, with custom URL overrides and legacy-default migration. [Contract and compatibility](MOSAIC_SIGNING.md#updater-routing-contract). Downstream discovery, notes/source metadata, alias download and in-place update are COMPLETE / LIVE VALIDATED.
- [x] Implement the [Mosaic rolling development publication mechanism](MOSAIC_DEVELOPMENT_RELEASE.md), reusing successful exact-main CI and the shared isolated signer. Manual exact-SHA authorization only; unsigned artifact 10099950969 was recovered using tooling 7d55b98b22e2d440599dfef7288f2ac066a0f8b1 and published as downstream-build-5/develop. Publication and in-place device acceptance are COMPLETE / LIVE VALIDATED.
- [x] Keep PR Debug artifacts as the operator-installable tested bytes; protected main does not retain another Debug artifact because exact-tree policy evidence already authenticates the PR result. Final-context Release assembly remains separate.
- [x] **I03 superseded and completed by the T0-1 two-class authority model:** every PR runs changed-range pre-commit plus the complete offline suite; `NON_ANDROID` omits Android validation and `ANDROID_FULL` runs the complete defaultDebug graph. Both classes and protected-main exact-tree reuse are hosted validated.
- [x] Formalize minimal release remediation: native failed-job reruns before publication; Development forward-fix; emergency Stable Hold followed by forward-fix and exact-byte promotion; manual correctly signed higher-version APK if the updater is broken. No rollback/repoint framework.

### P2 - future established security, dependency and review tooling

- [ ] Evaluate/adopt GitHub-native security and dependency capabilities, CodeQL, dependency review, secret scanning/push protection, and Renovate or Dependabot before custom alternatives. Audit actual settings/eligibility; choose one low-noise dependency-update owner per ecosystem.
- [ ] Evaluate Codex PR review, CodeRabbit and Copilot review where appropriate; trial one advisory reviewer and measure signal, noise, latency, access and actual cost/eligibility before adoption.
- [ ] Revisit validation performance only from repeated post-CP7 evidence; preserve broad hosted coverage, exact-tree authentication, and protected-main fallback.
- [ ] Prefer GitHub checks, issues and native notifications for operational status; add other notifications only for a demonstrated need.

### P3 - future downstream releases and broader portability

- [ ] Define release ownership, versioning, signing, provenance, retention, and approval separately before enabling publishers in another downstream repository; Wholphin's Development/Stable/Hold model is already operational.
- [ ] Port the proven contract to Seerr only after deliberately reconciling `origin/develop` versus `upstream/develop` divergence and auditing inherited container/chart/Pages/tag/release/issue/PR mutation workflows.
- [ ] Preserve Seerr's appropriate `develop` integration branch, pnpm/Node/Docker validation and repository-specific release lifecycle; adapt the contract to the other pending downstream repositories with their own toolchains and policies.

### Later - future semantic assistance

- [ ] Evaluate specialized agents for upstream-delta analysis, semantic conflict assistance, CI diagnosis, release readiness and cross-repository compatibility. Agents must not replace objective checks or autonomously resolve/publish semantic conflicts.
- [ ] Consider review apps only for services with a real web preview boundary, and self-hosted runners only if measured constraints justify the maintenance cost.

### Pre-main validation model

Wholphin does not currently need a permanent staging/develop branch. The preferred model is:

``` text
feature/fix branch
  -> PR
  -> required CI
  -> downloadable PR Debug APK (operational/live validated, seven-day retention)
  -> optional Android TV/device validation where needed
  -> main
  -> short-lived main debug artifact (P1, not yet retained)
  -> eventual RC/stable release process (P3)
```

Seerr is different: upstream `develop` genuinely participates in its integration and development-container lifecycle. Portability preserves that distinction rather than adding a staging branch to Wholphin or renaming every downstream integration branch.

The shared identity/index work is a compatibility seam, not a mandatory model migration. Existing `BaseItem`, `DiscoverItem`, pagers, destinations, ViewModels, and cards remain usable directly; new consumers may adopt identity-indexed sources one at a time. Combined product state remains ephemeral and must not require a database migration.

External Standard validation completed successfully for this checkpoint in `00:08:19.0908900`, covering its focused identity/index tests, production Kotlin compilation, established acquisition/tracker/pagination/Downloads regressions, and the Git whitespace check.

---

# Longer-Term Vision

Wholphin should be able to answer the important questions about a title without making the user think about which backend owns the information:

- **Can I watch it?**
- **Do I own part of it?**
- **What am I missing?**
- **Is it already being downloaded?**
- **Can I request it?**
- **Is it on my watchlist?**
- **Is there a better-quality version available?**
- **What comes next?**
- **What is this connected to?**

The distinction between Jellyfin, Seerr, Sonarr/Radarr, and external metadata providers should remain an implementation detail wherever possible.

The user should experience one media library.

### Stable channel implementation and future Settings UX

[Stable promotion/channel contract](MOSAIC_STABLE.md) is implemented: exact existing
signed build promotion with no rebuild/re-sign, explicit manual authorization, latest
routing and Stable/Development/Custom migration. Stable promotion of downstream-build-5, selector/device migration and automatic Development
after successful main CI are COMPLETE / LIVE VALIDATED. Stable promotion remains manual. Broader Settings
redesign, General/Playback/Library/Downloads/Updates/Integrations/Advanced grouping,
notification improvements where warranted and consistent progress/status UX are future
work, separate from this Updates-only change and from recurrence-driven performance work.


### Post-delivery optimization and product follow-ups

**Item 6 and T0-1 infrastructure work is complete.** The implemented graph uses bounded local Fast,
authoritative two-class PR validation, native auto-merge, authenticated exact-tree reuse with a
complete main fallback, independent Development eligibility, final-context Release assembly,
isolated signing, publication, optional Stable promotion, and authenticated upstream observation /
publication. CP7 retained already-fast trust boundaries and rejected further optimization without
repeatable evidence. The complete timing and decision record is in
[the CP7 audit](T0_1_CP7_PERFORMANCE_AUDIT.md).

Future pipeline work is demand-driven: T0-2 may audit security/reproducibility questions, and a
measured recurrence may reopen a specific performance candidate. Existing check/job/artifact/
version identities remain audited KEEP decisions, not unfinished naming work. Store/AAB
distribution remains unsupported until a deliberately owned pipeline is designed.

**Update and Settings UX (PENDING):** automatic discovery worked but proactive notification
was absent during normal use, re-entry and force-stop/reopen; Settings/About showed the
available update. Investigate a non-blocking Mosaic vX available banner/toast/notification
with de-duplication. Audit/consolidate Install update appearing in two Settings/About
surfaces. Improve states: Up to date / Update available / Downloading / Ready to install.
Retain broader Settings redesign, clearer General / Playback / Library / Downloads / Updates /
Integrations / Advanced groups, consistent status/progress and improved channel-selector
presentation. Keep Custom URL advanced, not normal configuration.

**Fluid download progress / water-meter effect (INVESTIGATE TELEMETRY FIRST):** inspect
Sonarr/Radarr/Seerr byte/progress telemetry before implementation. If trustworthy samples
exist, explore a flowing byte counter/progress bar using recent measured throughput and a
bounded/adaptive smoothing buffer. Interpolation is UI-only: never mutate authoritative
acquisition state, move displayed progress backward or simulate motion indefinitely after
fresh evidence stops. Converge to authoritative samples and exact completion. No smoothing
implementation is approved until telemetry is understood.

**Recovery policy (COMPLETE / LIVE VALIDATED):** Development bad build -> fix/revert ->
higher-version forward recovery. Urgent Stable regression -> Hold Release -> fix/revert on
main -> higher-version Development -> validate -> manually promote exact bytes.
If Stable cannot launch its updater, manually install a newer correctly signed Mosaic APK
over the existing package without clearing data. Never mutate/re-version an old APK to
force downgrade. No rollback, repoint, unhold or generic recovery machinery is planned.
See [recovery boundaries](MOSAIC_STABLE.md#forward-recovery-policy).

**Post-plumbing repository organization (PENDING):** rewrite README around what Mosaic is,
why it exists and how Stable/Development work. Describe a personal experimentation and
integration downstream, not an official Wholphin replacement. Credit Wholphin and other
upstreams; explicitly encourage adoption of Mosaic features/fixes/designs/ideas by Wholphin
and relevant open-source projects rather than treating them as exclusive. Add useful workflow/
version badges, a compact label taxonomy and historical PR label backfill. Backfill meaningful
completed Issues linked to PRs and create actionable roadmap Issues. Establish one Mosaic
GitHub Project, meaningful product/release milestones (not every iteration), and useful
Issue ? PR ? Project automation. Rename Wholphin-release.apk to Mosaic-release.apk only
atomically with updater compatibility; retain the legacy alias temporarily for older Mosaic
versions if required. No README, assets, labels, Issues, Project or GitHub changes occur here.


### Historical upstream publication blocker correction (superseded by completed I06)

Live run/artifact audit corrected the earlier credential-binding hypothesis: read-only
observation found SeriesOverview.kt / SeriesViewModel.kt conflicts; the ready-only App
mint step correctly skipped. Durable issue recording failed and repository Issues are
currently disabled. See [historical diagnosis](CODEX_HANDOFF.md#historical-v1-upstream-publication-diagnosis-superseded-by-i06).
This evidence is retained only to explain the path to I06. The completed native lifecycle no
longer depends on Issues or journal reporting; current behavior and remaining natural-conflict
acceptance are documented in [UPSTREAM_SYNC](UPSTREAM_SYNC.md) and
[the I06 migration record](I06_NATIVE_UPSTREAM_MIGRATION_PLAN.md).
