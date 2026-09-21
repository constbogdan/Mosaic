# Historical engineering records

This index groups Mosaic's point-in-time engineering evidence. These records preserve the names,
repository identities, SHAs, run and PR numbers, workflow and artifact identities, failures,
measurements, rejected approaches, and decisions that were true when recorded. They are not current
operating runbooks.

For current behavior, start with the [documentation index](../README.md). It links the canonical
owners for PR preparation, validation, Development publication, signing, Stable/Hold, upstream
integration, identity, roadmap, and continuity. When a historical statement differs from a current
manual, the current canonical owner governs operation and the historical statement remains evidence
of the earlier state.

The records remain in their established locations. D4 found extensive heading-level links from the
roadmap, handoff, and current manuals, plus likely external references to these long-lived paths and
anchors. No machine consumer requires the paths, but moving them would add needless link and anchor
risk without changing their authority. This index provides the historical boundary without a deep
archive tree or duplicate copies.

## Item 6 and earlier consolidation

- [Item 6 delivery and tooling consolidation](../ITEM_6_CONSOLIDATION_CHECKLIST.md) — completed
  multi-checkpoint program ledger and acceptance evidence.
- [I05 delivery presentation and compatibility](../ITEM_6_I05_PRESENTATION.md) — historical
  presentation inventory, compatibility classification, and migration evidence.
- [Upstream automation audit](../ITEM_6_UPSTREAM_AUTOMATION_AUDIT.md) — pinned workflow inventory
  and ownership analysis preceding the current upstream model.
- [Release pipeline simplification plan](../RELEASE_PIPELINE_SIMPLIFICATION_PLAN.md) — decisions and
  evidence that produced the surviving validation, build, signing, and publication boundaries.

## T0-1 validation and operator experience

- [T0-1 operator UX inventory](../T0_1_OPERATOR_UX_INVENTORY.md) — completed CP1–CP8 implementation
  and hosted-acceptance ledger.
- [CP7 performance audit](../T0_1_CP7_PERFORMANCE_AUDIT.md) — dated performance measurements,
  accepted optimization, and KEEP decisions.

Current validation authority belongs to [Validation architecture](../VALIDATION.md), and current PR
operation belongs to [Safe pull-request preparation](../PREPARE_PR.md).

## Upstream migration

- [I06 native upstream migration](../I06_NATIVE_UPSTREAM_MIGRATION_PLAN.md) — completed design,
  migration, and native-ancestry acceptance evidence.

Current ownership, Observe/Publish, waiting, resolver, credential, and failure behavior belongs to
[Upstream synchronization](../UPSTREAM_SYNC.md).

## Release remediation and delivery decisions

- [I07 published Release remediation](../I07_PUBLISHED_RELEASE_REMEDIATION.md) — decision rationale
  and acceptance evidence for Hold plus higher-version forward recovery.
- [Release pipeline simplification plan](../RELEASE_PIPELINE_SIMPLIFICATION_PLAN.md) — retained
  architectural rationale for the current pipeline topology.

Current operation belongs to [Development publication](../MOSAIC_DEVELOPMENT_RELEASE.md),
[Release signing](../MOSAIC_SIGNING.md), and [Stable/Hold](../MOSAIC_STABLE.md).

## Mosaic identity migration and T0-3 evidence

- [R2–R4 Mosaic identity record](../T0_3_R2_MOSAIC_IDENTITY.md) — retained repository rename,
  canonicalization, presentation, and old-name retirement acceptance.
- [Pre-D5 roadmap and engineering ledger](../Wholphin_ROADMAP.md) — detailed completed-program
  chronology retained at its established path after the concise current roadmap was created.
- [Pre-D5 Codex handoff snapshot](CODEX_HANDOFF_PRE_D5.txt) — byte-identical preserved continuity,
  incident, architecture, and acceptance ledger from immediately before handoff reduction.
- [Documentation consolidation plan](../T0_3_DOCUMENTATION_CONSOLIDATION_PLAN.md) — D1 inventory,
  reviewed ownership model, and D1–D7 implementation decisions.

Current identity is owned by [MOSAIC_IDENTITY.md](../MOSAIC_IDENTITY.md), and the current roadmap by
[MOSAIC_ROADMAP.md](../MOSAIC_ROADMAP.md). The older records remain evidence, not alternate owners.
