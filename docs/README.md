# Mosaic documentation

This page answers where to find Mosaic documentation. It is an index, not an operating runbook.
Role labels distinguish public guidance, contributor orientation, engineering authority, current
continuity, and point-in-time evidence.

## Start here

- **Public product entry point** — [Mosaic README](../README.md): what Mosaic is, installation,
  updates, and compatibility.
- **Public user guidance** — [Mosaic user guide](USER_GUIDE.md): installation and sideloading,
  Stable versus Development, automatic updates, optional Seerr integration, and troubleshooting.
- **Product roadmap** — [Mosaic roadmap](MOSAIC_ROADMAP.md): current program status, product
  direction, major completed phases, and remaining work.
- **Current continuity** — [Codex handoff](CODEX_HANDOFF.md): current checkpoint, recent continuity,
  non-obvious invariants, risks, and retained engineering history.

## New contributors

- **Contributor guidance** — [Contributing](../CONTRIBUTING.md): ways to contribute and submission
  expectations.
- **Developer guidance** — [Developer guide](../DEVELOPMENT.md): development environment, project
  layout, and local build information.
- **Current operating authority** — [Safe pull-request preparation](PREPARE_PR.md): publication
  authority, local preparation, validation handoff, and PR behavior.
- **Current architecture / validation authority** — [Validation architecture](VALIDATION.md):
  local feedback, authoritative PR validation, exact-tree reuse, fallback, and delivery boundaries.
- **Agent guidance** — [Repository agent instructions](AGENTS.md): permanent repository-specific
  rules for coding agents.

## Public and engineering boundary

- **Public product documentation** — the root [README](../README.md) is the concise product entry
  point; the [user guide](USER_GUIDE.md) owns user-facing setup, channels, and troubleshooting.
- **Contributor orientation** — [Contributing](../CONTRIBUTING.md) and the
  [developer guide](../DEVELOPMENT.md) explain where to start and route deeper work here.
- **Versioned engineering authority** — architecture, validation, publication, signing,
  repository identity, and upstream rules are owned only by the canonical repository documents
  indexed below.
- **Optional GitHub Wiki** — no Wiki content is currently published or maintained from this
  repository. A future Wiki may offer user-facing summaries, but must identify and link its
  versioned source and cannot become engineering authority.

## Current project state

- **Product roadmap** — [Mosaic roadmap](MOSAIC_ROADMAP.md): where the product and engineering
  program are going.
- **Current continuity** — [Codex handoff](CODEX_HANDOFF.md): what a future engineering session
  cannot safely infer from code alone.
- **Current issue ledger** — [Observed minor issues](OBSERVED_MINOR_ISSUES.md): bounded open and
  resolved product observations.
- **Current planning record** — [T0-3 documentation consolidation plan](T0_3_DOCUMENTATION_CONSOLIDATION_PLAN.md):
  reviewed ownership, consolidation sequence, and acceptance criteria for this documentation phase.

## Engineering architecture

- **Current architecture / validation authority** — [Validation architecture](VALIDATION.md):
  local Fast feedback, hosted evidence classes, protected-main reuse, and conservative fallback.
- **Current architecture** — [Application architecture](ARCHITECTURE.md): stable product, state,
  integration, and engineering authority boundaries.
- **Current architecture** — [Canonical Mosaic identity](MOSAIC_IDENTITY.md): repository,
  product, package, updater, asset, URI, and intentionally retained identities.
- **Developer guidance** — [Developer guide](../DEVELOPMENT.md): concise application organization
  and development setup.
- **Current architecture and direction** — [Mosaic roadmap](MOSAIC_ROADMAP.md): product priorities
  and future direction.
- **Current continuity** — [Codex handoff](CODEX_HANDOFF.md): non-obvious implementation constraints
  not yet promoted to a canonical architecture owner.


## Delivery and security

Current lifecycle boundaries are: [PR preparation](PREPARE_PR.md) →
[validation authority](VALIDATION.md) → [Development publication](MOSAIC_DEVELOPMENT_RELEASE.md)
→ [isolated signing](MOSAIC_SIGNING.md) → [Stable/Hold](MOSAIC_STABLE.md). Upstream
`REVIEW`/conflict work follows the separate [upstream resolver lifecycle](UPSTREAM_SYNC.md).

- **Current operating authority** — [Development releases](MOSAIC_DEVELOPMENT_RELEASE.md): rolling
  and immutable Development publication, eligibility, provenance, and recovery.
- **Current operating authority** — [Release signing](MOSAIC_SIGNING.md): signing identity, custody,
  isolation, authorization, and verification.
- **Current operating authority** — [Stable and Hold](MOSAIC_STABLE.md): exact-byte Stable promotion,
  approval, channel behavior, Hold, and recovery boundaries.
- **Historical decision** — [Published Release remediation](I07_PUBLISHED_RELEASE_REMEDIATION.md):
  why Stable Hold plus forward-fix is retained instead of rollback/repoint machinery.

## Upstream integration

- **Current operating authority** — [Upstream synchronization](UPSTREAM_SYNC.md): remotes, branching,
  ownership, hosted observation/publication, REVIEW resolution, and failure behavior.
- **Historical decision and acceptance** — [I06 native upstream migration](I06_NATIVE_UPSTREAM_MIGRATION_PLAN.md):
  the completed native-ancestry migration and its proof.

## Agent instructions

- **Agent guidance** — [Repository agent instructions](AGENTS.md): authoritative permanent agent and
  development rules, including the required fresh-session bootstrap.
- **Scoped UI agent guidance** — [UI agent instructions](UI_AGENTS.md): Android TV presentation,
  navigation, focus, and shared-state UI rules. `AGENTS.md` remains the general authority.

## Historical decisions and acceptance

- **Historical evidence index** — [Historical engineering records](history/README.md): grouped Item 6,
  T0-1, performance, upstream-migration, release-remediation, and Mosaic-identity evidence. These
  records explain decisions and acceptance; they are not current operating runbooks.
- **Historical decision still referenced by current operation** —
  [Published Release remediation](I07_PUBLISHED_RELEASE_REMEDIATION.md): rationale for the current
  Stable Hold plus forward-fix policy owned by `MOSAIC_STABLE.md`.

## Component references

- **Component reference** — [Intent and deep-link reference](../Intents.md): supported intent forms
  and shell examples.
- **Component reference** — [MPV stub](../wholphin-mpv-stub/README.md): inherited native playback
  component information.
- **Component reference** — [Profile utilities](../app/src/main/java/com/github/damontecres/wholphin/util/profile/README.md):
  source-adjacent profile implementation notes.
- **Third-party asset reference** — [Roboto font assets](../app/src/main/assets/font-roboto/README.md):
  bundled font source and licensing information.
