# Mosaic documentation

This page answers where to find Mosaic documentation. It is an index, not an operating runbook.
Role labels distinguish current authority from continuity and point-in-time engineering evidence.

## Start here

- **Public product entry point** — [Mosaic README](../README.md): what Mosaic is, installation,
  updates, and compatibility.
- **Product roadmap** — [Mosaic roadmap](Wholphin_ROADMAP.md): current program status, product
  direction, major completed phases, and remaining work. The inherited filename remains until the
  reviewed D5 migration.
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

## Current project state

- **Product roadmap** — [Mosaic roadmap](Wholphin_ROADMAP.md): where the product and engineering
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
- **Current architecture** — [Canonical Mosaic identity](T0_3_R2_MOSAIC_IDENTITY.md): repository,
  product, package, updater, asset, URI, and intentionally retained identities.
- **Developer guidance** — [Developer guide](../DEVELOPMENT.md): concise application organization
  and development setup.
- **Current architecture and direction** — [Mosaic roadmap](Wholphin_ROADMAP.md): current product
  architecture until a dedicated architecture document is created in a later checkpoint.
- **Current continuity** — [Codex handoff](CODEX_HANDOFF.md): non-obvious implementation constraints
  not yet promoted to a canonical architecture owner.

A dedicated `ARCHITECTURE.md` owner remains planned for a later reviewed checkpoint; it does not
exist yet.

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
- **Agent guidance pending consolidation** — [UI agent instructions](UI_AGENTS.md): overlapping
  UI/product guidance whose unique scope and long-term authority will be resolved in D5. Until then,
  it does not supersede `AGENTS.md`.

## Historical decisions and acceptance

The following files are point-in-time engineering evidence, not current operating runbooks. D4 may
add historical navigation or move records only after preserving their links and meaning.

- **Historical acceptance/evidence** — [T0-1 operator UX inventory](T0_1_OPERATOR_UX_INVENTORY.md):
  completed CP1–CP8 implementation and hosted acceptance ledger.
- **Historical decision and evidence** — [T0-1 performance audit](T0_1_CP7_PERFORMANCE_AUDIT.md):
  measured performance corpus and accepted optimization decisions.
- **Historical acceptance/evidence** — [Item 6 consolidation checklist](ITEM_6_CONSOLIDATION_CHECKLIST.md):
  delivery/tooling consolidation program ledger.
- **Historical design and acceptance** — [I05 presentation ledger](ITEM_6_I05_PRESENTATION.md):
  delivery presentation and compatibility decisions.
- **Historical design/audit** — [Upstream automation audit](ITEM_6_UPSTREAM_AUTOMATION_AUDIT.md):
  pinned workflow inventory and ownership analysis.
- **Historical decision** — [Release pipeline simplification plan](RELEASE_PIPELINE_SIMPLIFICATION_PLAN.md):
  rationale for the surviving validation and release topology.
- **Historical decision and acceptance** — [I06 native upstream migration](I06_NATIVE_UPSTREAM_MIGRATION_PLAN.md):
  migration from the superseded synchronization lifecycle.
- **Historical decision** — [I07 published Release remediation](I07_PUBLISHED_RELEASE_REMEDIATION.md):
  accepted containment and forward-recovery policy.
- **Current architecture with historical acceptance** — [R2 Mosaic identity record](T0_3_R2_MOSAIC_IDENTITY.md):
  canonical identity plus R2–R4 evidence retained in its current location until later consolidation.

## Component references

- **Component reference** — [Intent and deep-link reference](../Intents.md): supported intent forms
  and shell examples.
- **Component reference** — [MPV stub](../wholphin-mpv-stub/README.md): inherited native playback
  component information.
- **Component reference** — [Profile utilities](../app/src/main/java/com/github/damontecres/wholphin/util/profile/README.md):
  source-adjacent profile implementation notes.
- **Third-party asset reference** — [Roboto font assets](../app/src/main/assets/font-roboto/README.md):
  bundled font source and licensing information.
