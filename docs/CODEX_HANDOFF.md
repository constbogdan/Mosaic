# Codex handoff: Mosaic

## Current checkpoint

T0-3 documentation consolidation D1–D4 is complete. D5 is implementing canonical architecture,
identity, roadmap, agent guidance, and bounded continuity. D6 public/Wiki work and D7 final
consistency remain open. T0-3 and Baseline T0 are not complete.

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

- D5 must finish consumer-aware documentation path migration and comprehensive link, anchor,
  classification, prepare-pr, pre-commit, and Fast validation.
- D6 must not begin in D5. Do not create or publish Wiki content or substantially rewrite public
  installation/help prose.
- D7 will own the final documentation consistency and status audit. Do not declare T0-3 or Baseline
  T0 complete before that separately reviewed acceptance.

## Non-obvious invariants and negative knowledge

- Local Fast feedback is not hosted validation authority. Every PR receives complete offline
  tooling; uncertain Android/build scope selects `ANDROID_FULL`. Protected main reuses evidence only
  for an exactly authenticated tested tree and otherwise runs the complete conservative fallback.
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

- The current documentation phase still needs D6 and D7 acceptance.
- External GitHub settings remain manually administered; documentation may describe them, but no
  configuration doctor or scheduled drift monitor is justified without repeated evidence.
- Android TV visual/focus/device behavior is not fully proven by hosted compilation and JVM tests.
- Cross-repository tooling standardization remains evidence-driven; do not copy Mosaic-specific
  Gradle, PowerShell, branch, artifact, or release assumptions into another repository.

## Handoff maintenance

Keep this file bounded to the active checkpoint, unfinished work, risks, invariants, and negative
knowledge that cannot be inferred safely from code. Promote stable facts to their canonical owner
and completed chronology to the history boundary. Do not rebuild another permanent activity ledger.
