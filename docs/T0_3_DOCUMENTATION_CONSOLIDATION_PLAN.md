# T0-3 documentation consolidation audit and plan

Status: **D1-D5 COMPLETE; D6 implemented pending review. D7 has not begun.**

This audit describes the documentation set at `main` commit
`44564901cbdb7ffce4b1c0704aa2d53f93c40fa9`. It does not move, delete, rename, or broadly rewrite
existing documents. T0-3 and Baseline T0 remain open. The next checkpoint requires separate review
and authorization.

## Principles

- Repository documentation is authoritative for engineering, security, validation, publication,
  signing, and upstream-integration contracts.
- Public and Wiki content may explain those contracts but must link back rather than fork them.
- Current instructions, current architecture, continuity, and historical evidence are distinct
  information classes even when they currently share a file.
- Historical evidence remains immutable in meaning. Moving it later must preserve headings or add
  redirects/navigation so existing links remain useful.
- A filename containing `Wholphin` is not automatically stale: upstream identity, source namespace,
  protocol identity, and truthful history remain Wholphin where appropriate.
- Consolidation must be incremental, docs-only, and link-checked at every checkpoint.

## Documentation inventory and classification

Roles are descriptive, not exclusive. Multiple roles in one row identify overlap to resolve.

| Document | Current role(s) | Finding | Long-term disposition |
| --- | --- | --- | --- |
| `README.md` | Public product entry point | Correctly concise: product, installation, updates, compatibility. It lacks deeper-document navigation. | Keep public and short; add a contributor/documentation link during D2. |
| `CONTRIBUTING.md` | Public contributor entry; developer guidance | Small generic contribution guide; does not explain Mosaic's PR authority or prepare-pr path. | Keep public-facing; point to `docs/README.md` and `PREPARE_PR.md`, without duplicating their rules. |
| `DEVELOPMENT.md` | Developer guidance; partial architecture | Build setup and source organization are useful but separate from current delivery operations. | Keep at root unless D2 establishes a clearer developer section; link from the documentation index. |
| `Intents.md` | Current technical reference; upstream-derived source documentation | Describes intent/deep-link behavior and shell examples. Product naming is generic; filename is not a brand defect. | Keep as a focused technical reference; index under application architecture. |
| `docs/AGENTS.md` | Agent/developer guidance; current operating rules; product architecture | Authoritative but long. It duplicates validation, PR, upstream, and product architecture that have dedicated owners. It still uses Wholphin as the downstream product in current prose. | Keep filename because repository tooling selects it. Reduce later to permanent agent rules plus links to canonical docs. |
| `docs/UI_AGENTS.md` | Agent guidance; product architecture | Large partial copy of `AGENTS.md` with a small UI-specific delta; unclear authority. | Consolidate unique UI rules into a clearly scoped UI guide or `AGENTS.md`, then archive/remove the duplicate only after link and consumer review. |
| `docs/CODEX_HANDOFF.md` | Current continuity; current architecture; historical acceptance/evidence; historical design | At about 431 KB it is a permanent ledger, architecture store, incident archive, and handoff simultaneously. Multiple old `CURRENT` headings survive. Stable knowledge is buried and fresh sessions must read it. | Retain concise current checkpoint, recent continuity, unresolved risks, invariants, and negative knowledge. Promote stable material to canonical docs; move closed chronology to navigable historical records. Do not discard evidence. |
| `docs/Wholphin_ROADMAP.md` | Product roadmap; current architecture; acceptance chronology; historical design | The title is downstream-stale, while the body is already Mosaic. At about 101 KB it mixes future direction, completed implementation ledgers, and historical acceptance. | Keep until a dedicated rename/migration. Long term use `MOSAIC_ROADMAP.md` for direction and phase status; link historical ledgers instead of embedding them. |
| `docs/PREPARE_PR.md` | Current operating source of truth | Best current owner for local preparation, publication authority, failure behavior, and PR lifecycle. A historical PR #9 integration note is harmless but not operating guidance. | Keep canonical and current-focused; move old acceptance detail to history when touched. |
| `docs/UPSTREAM_SYNC.md` | Current operating source of truth; architecture; historical acceptance | Canonical branching, ownership, resolver, and hosted synchronization contract, but includes reference-sync and acceptance chronology. | Keep canonical current policy; extract completed episodes/evidence later while retaining links. |
| `docs/MOSAIC_DEVELOPMENT_RELEASE.md` | Current operating source of truth; historical acceptance/evidence | Opens with the current model, then contains several explicitly historical implementation and removed-recovery sections. | Keep current Development contract and recovery rules; move historical checkpoints to delivery history. |
| `docs/MOSAIC_SIGNING.md` | Current operating source of truth; security architecture; historical acceptance | Signing invariants and custody are authoritative, but first-acceptance chronology and retired architecture occupy much of the file. | Keep signing/custody/verification authority current; extract acceptance and creation history without exposing secrets. |
| `docs/MOSAIC_STABLE.md` | Current operating source of truth; historical acceptance | Current exact-byte promotion, approval, Hold, and updater behavior coexist with first-promotion history. | Keep canonical Stable/Hold operating contract; link extracted acceptance chronology. |
| `docs/I07_PUBLISHED_RELEASE_REMEDIATION.md` | Current recovery decision; historical design/evidence | Durable negative decision explaining why Hold plus forward-fix replaces rollback/repoint machinery. Some behavior overlaps Stable. | Preserve as an architecture decision record; summarize the actionable rule in `MOSAIC_STABLE.md`. |
| `docs/I06_NATIVE_UPSTREAM_MIGRATION_PLAN.md` | Historical design/audit; historical acceptance | Completed migration plan with detailed native-ancestry proof. Current behavior belongs in `UPSTREAM_SYNC.md`. | Preserve under historical/decisions navigation; stop treating it as an operating manual. |
| `docs/RELEASE_PIPELINE_SIMPLIFICATION_PLAN.md` | Historical design/audit; architecture decision record | Explains why the current release topology exists. It is not a runbook. | Preserve as historical decision evidence; canonical release docs own current behavior. |
| `docs/ITEM_6_CONSOLIDATION_CHECKLIST.md` | Historical program ledger; acceptance evidence | Large completed checkpoint tracker with some statements later superseded by T0 work. | Historical program record; move/index as a closed ledger, preserving anchors. |
| `docs/ITEM_6_I05_PRESENTATION.md` | Historical design/audit; acceptance evidence | Before/after presentation and compatibility ledger; referenced by current release docs for history. | Preserve as historical delivery evidence and maintain inbound links or redirects. |
| `docs/ITEM_6_UPSTREAM_AUTOMATION_AUDIT.md` | Historical design/audit | Pinned workflow inventory that led to the current ownership model. | Preserve as historical audit; current ownership policy remains in `UPSTREAM_SYNC.md`. |
| `docs/T0_1_OPERATOR_UX_INVENTORY.md` | Historical program ledger; acceptance evidence | About 170 KB of completed CP1–CP8 evidence; authoritative for that completed program, not current operation. | Preserve as a closed T0-1 ledger under historical navigation. |
| `docs/T0_1_CP7_PERFORMANCE_AUDIT.md` | Historical audit; accepted KEEP decisions | Measured performance corpus and decision record. Measurements are dated, while conclusions explain retained architecture. | Preserve as a historical performance audit; link only surviving decisions from current architecture docs. |
| `docs/T0_3_R2_MOSAIC_IDENTITY.md` | Current identity architecture; historical acceptance/evidence | The current identity matrix is valuable; R2/R3/R4 acceptance and incident chronology share the file. | Keep or evolve into a canonical Mosaic identity document; extract completed lifecycle evidence later. |
| `docs/OBSERVED_MINOR_ISSUES.md` | Current continuity; issue ledger | Small actionable open/resolved list. It risks duplicating GitHub Issues but records validated product observations. | Keep until an explicit issue-tracking decision; clearly separate open from historical resolved observations. |
| `docs/fixture/fixture_commands.md` | Test fixture | Test data, not repository guidance. | Keep excluded from documentation navigation. |
| `wholphin-mpv-stub/README.md` | Component reference; upstream/source identity | Scoped build/runtime information for an inherited component. | Keep beside the component; index only if contributors commonly need it. |
| `app/.../font-roboto/README.md` | Third-party asset documentation | License/source material. | Keep in place; exclude from product-doc consolidation. |
| `app/.../util/profile/README.md` | Component technical reference | Narrow source-adjacent design explanation. | Keep beside code; optionally link from future architecture navigation. |

## Topic overlap and duplication

| Topic | Current descriptions | Finding |
| --- | --- | --- |
| Prepare-pr and PR lifecycle | `AGENTS.md`, `PREPARE_PR.md`, `CODEX_HANDOFF.md`, roadmap | `PREPARE_PR.md` is the natural owner. Agent guidance should state authority boundaries and link to it; handoff and roadmap should not repeat the procedure. |
| Validation classes and exact-tree reuse | `AGENTS.md`, handoff, roadmap, T0-1 inventory, performance audit | The active contract is embedded mostly in agent guidance and history. A dedicated validation architecture page is missing. |
| Development publication | Development manual, signing manual, Stable manual, handoff, roadmap, release simplification plan | Current lifecycle is recoverable from the Development manual, but authority and acceptance history are repeated across five files. |
| Signing | Signing manual, Development manual, Stable manual, handoff, roadmap | `MOSAIC_SIGNING.md` should own credentials, custody, signer identity, isolation, and verification. Other docs should state only their dependency on that boundary. |
| Stable Promotion | Stable manual, I07 record, Development manual, handoff, roadmap | Stable manual should own operation; I07 should remain the rationale for Hold/forward-fix. |
| Hold and recovery | Stable manual, I07 record, Development manual, handoff | Recovery types are easy to conflate. Canonical docs need a small routing table: Development failed-job/forward-fix vs Stable Hold. |
| Upstream synchronization | `UPSTREAM_SYNC.md`, `AGENTS.md`, I06, Item 6 audit, handoff, roadmap | Current policy is duplicated in agent instructions; I06 and Item 6 are historical. |
| Mosaic identity | R2 identity record, handoff, roadmap, README, release docs | The stable current identity matrix should have one canonical owner; acceptance chronology should not dominate it. |
| Product architecture | roadmap, `AGENTS.md`, `UI_AGENTS.md`, handoff, `DEVELOPMENT.md`, source-adjacent READMEs | No concise architecture index exists. Stable architecture is buried in agent and historical files. |
| Baseline T0 status | roadmap, handoff, T0-1 ledger, R2 identity record | Roadmap should own phase status. Handoff should contain only the current checkpoint and immediate continuity. |
| Agent instructions | `AGENTS.md`, `UI_AGENTS.md`, handoff | Two agent guides overlap, and the handoff is required bootstrap reading despite its size. |

## Contradictory or stale-current-state findings

These are findings for later checkpoints, not edits authorized by D1:

1. `docs/AGENTS.md` repeatedly calls the downstream product Wholphin in current principles. Some
   occurrences mean upstream/source identity, but product-facing uses should become Mosaic.
2. `docs/UI_AGENTS.md` presents itself as `AGENTS.md` and repeats most permanent guidance without an
   explicit scope or precedence rule.
3. `CODEX_HANDOFF.md` contains several older `CURRENT` labels, including superseded release and PR
   implementation checkpoints. The top status is current, but search results can land on obsolete
   assertions without sufficient context.
4. `Wholphin_ROADMAP.md` is titled Mosaic internally but retains the old downstream filename. It
   also embeds detailed completed acceptance records beside future product work.
5. The roadmap's older completed checklist says visual branding remains pending, while R3 has
   completed hosted and real-device branding acceptance. That line is truthful chronology only if
   kept inside an explicitly historical ledger.
6. `MOSAIC_DEVELOPMENT_RELEASE.md` contains old-name manual commands only inside sections labelled
   historical/removed. They must not be copied into current recovery guidance, but should remain
   truthful evidence.
7. `PREPARE_PR.md` describes current behavior accurately but uses PR #9 as its prominent `CURRENT`
   integration marker. The behavior is current; the evidence link is historical and should become
   secondary.
8. The repository has no `docs/README.md`, so discovery depends on agent bootstrap rules, incidental
   cross-links, or knowing filenames.
9. `CODEX_HANDOFF.md` notes an old missing-anchor incident multiple times even though the referenced
   anchor was later restored. Those records are historical negative knowledge, not a current defect.

No disagreement found in the current Mosaic-only repository identity, validation authority,
release authority, signing isolation, or upstream ownership contracts. The primary defect is
information placement and precedence, not demonstrated runtime ambiguity.

## Canonical source-of-truth matrix

| Topic | Recommended canonical owner | Secondary references | Documents that should stop duplicating current instructions |
| --- | --- | --- | --- |
| Product overview | Root `README.md` | Future Wiki landing/install pages | Roadmap and handoff should not repeat installation prose. |
| Product roadmap | Future `docs/MOSAIC_ROADMAP.md` (current file until migrated) | `docs/README.md`, handoff current checkpoint | Handoff and completed ledgers should not carry future priority lists. |
| PR workflow | `docs/PREPARE_PR.md` | `CONTRIBUTING.md`, `AGENTS.md`, docs index | Handoff, roadmap, and upstream manual should link rather than restate ordinary PR procedure. |
| Validation architecture | New `docs/VALIDATION.md` proposed in D3 | `PREPARE_PR.md`, workflow summaries, agent guidance | Handoff, roadmap, T0-1 inventory, and performance audit become evidence/secondary rationale. |
| Development releases | `docs/MOSAIC_DEVELOPMENT_RELEASE.md` | Signing, Stable, validation docs | Handoff and roadmap retain only status/invariants. |
| Signing | `docs/MOSAIC_SIGNING.md` | Development and Stable manuals | Handoff and roadmap stop carrying operational credential detail. |
| Stable releases | `docs/MOSAIC_STABLE.md` | Signing and Development manuals | Handoff, roadmap, I05 ledger stop presenting current steps. |
| Hold/recovery | `docs/MOSAIC_STABLE.md` for operations; I07 as decision rationale | Development recovery section | Handoff and roadmap use links and concise boundaries. |
| Upstream synchronization | `docs/UPSTREAM_SYNC.md` | `AGENTS.md`, I06 and Item 6 history | Handoff and roadmap stop duplicating mechanics. |
| Mosaic identity | Evolve `docs/T0_3_R2_MOSAIC_IDENTITY.md` into a current identity contract, with history linked separately | README, release manuals | Handoff and roadmap retain only summary/status. |
| Product/application architecture | New `docs/ARCHITECTURE.md`, assembled from stable roadmap/agent/handoff material | `DEVELOPMENT.md`, source-adjacent READMEs | Handoff and agent files stop being the sole owner. |
| Agent guidance | `docs/AGENTS.md` | A narrowly scoped UI guide if unique rules justify it | `UI_AGENTS.md` must not remain a competing general guide. |
| Current engineering continuity | A substantially shorter `docs/CODEX_HANDOFF.md` | Canonical docs and history index | It should not remain the only home of stable architecture or old acceptance chronology. |
| Historical acceptance/evidence | `docs/history/README.md` plus preserved ledgers/audits | Canonical docs link to relevant decisions | Current manuals should not embed long completed run narratives. |
| Open product observations | `docs/OBSERVED_MINOR_ISSUES.md` pending issue-tracker decision | Roadmap links only when strategically relevant | Handoff should not duplicate routine issue state. |

## Proposed `docs/README.md` structure

D2 should create a small navigation page, not a new source of operational truth:

1. **Start here** — Mosaic summary and link back to root `README.md`.
2. **New contributors** — `CONTRIBUTING.md`, `DEVELOPMENT.md`, `PREPARE_PR.md`.
3. **Current project state** — roadmap, concise handoff, observed issues.
4. **Engineering architecture** — future `ARCHITECTURE.md`, validation, identity.
5. **Delivery and security** — Development, signing, Stable/Hold.
6. **Upstream integration** — current upstream policy and resolver entry point.
7. **Agent instructions** — `AGENTS.md` and any explicitly scoped UI guidance.
8. **Historical decisions and acceptance** — history index grouped by T0, Item 6, I06/I07,
   performance, presentation, and identity migration.
9. **Component references** — intent protocol and source-adjacent READMEs.

Every entry should state whether it is authoritative current guidance, architecture, continuity, or
historical evidence. The index must not reproduce runbooks.

## Wiki boundary

Good future Wiki candidates are user-oriented and can be generated or reviewed against canonical
repository sources:

- installation and updating;
- Stable versus Development channels;
- Seerr integration and setup;
- user troubleshooting;
- contributor orientation and links into repository docs.

The Wiki must not independently define validation classes, exact-tree reuse, PR authority, release
or signing authority, repository authentication, provenance, Hold behavior, or upstream ownership.
Those pages should summarize and link to versioned in-repository contracts. A Wiki page must name
its canonical repository source and should be updated in the same reviewed change when behavior
changes. Publishing Wiki content remains a separate operator action.

## Naming, rename, and move assessment

| Proposal | Recommendation | Consumers to migrate or verify |
| --- | --- | --- |
| `Wholphin_ROADMAP.md` → `MOSAIC_ROADMAP.md` | Reasonable current downstream rename, but defer until D5. Preserve a small redirect stub if external links merit it. | `AGENTS.md`, `UI_AGENTS.md`, `CODEX_HANDOFF.md`, roadmap self-links, `scripts/prepare-pr.config.psd1`, tests/classification fixtures, and any external bookmarks. |
| `T0_3_R2_MOSAIC_IDENTITY.md` → `MOSAIC_IDENTITY.md` | Prefer evolving/renaming after historical sections are separated in D4/D5. | Handoff, roadmap, docs index, historical links and anchors. |
| Move completed `ITEM_6_*`, `T0_1_*`, I06, and release-simplification records under `docs/history/` | Good organization, but use one bounded move checkpoint and preserve discoverability. | Numerous handoff/roadmap/current-manual links; no workflow/script runtime consumers found. Relative links inside moved files require rewriting. |
| `UI_AGENTS.md` | Do not blindly rename. First identify its 16-line unique delta relative to `AGENTS.md`; retain as a scoped UI guide only if that delta deserves its own owner. | Agent bootstrap references and future docs index; no production consumer found. |
| `CODEX_HANDOFF.md` | Keep filename and shrink in D5; tooling and agent instructions rely on it. | `AGENTS.md`, `prepare-pr.config.psd1`, handoff links, fresh-session workflow. |
| Wholphin strings in upstream/source docs | Keep. | `damontecres/Wholphin`, Kotlin namespace, component paths, history and `wholphin-*` protocols are intentional. |

Before any move, generate an inbound-link manifest across Markdown, workflows, scripts, tests, and
agent instructions. `scripts/prepare-pr.config.psd1` explicitly classifies `AGENTS.md`,
`UPSTREAM_SYNC.md`, `CODEX_HANDOFF.md`, and `Wholphin_ROADMAP.md`; those paths are machine-adjacent
consumers and require coordinated tests if renamed.

## Historical-evidence strategy

Use a navigable history collection rather than deletion or silent rewriting:

- `docs/history/README.md` explains that records are point-in-time evidence, not current runbooks.
- Group records by program (`item-6`, `t0-1`, `t0-2`, `t0-3`) or by decision domain only when that
  improves navigation; avoid deep nesting.
- Preserve Git history and the meaning of old repository names, asset names, workflow names, SHAs,
  run IDs, failures, and rejected approaches.
- Canonical current docs may summarize a surviving decision and link to its evidence.
- Historical documents must begin with a clear status banner and a link to the current owner.
- If moves would break durable external anchors, prefer leaving the record in place and indexing it,
  or retain a stub. Do not fabricate redirect behavior GitHub Markdown does not support.

## Bounded implementation sequence

### D1 — Inventory and target architecture

Scope: this audit and plan only.

Files: add this document; no existing source-of-truth rewrite.

Separation rationale: establishes reviewed ownership before moving content.

Acceptance: complete inventory, overlap/staleness findings, canonical matrix, Wiki boundary,
consumer-aware rename assessment, bounded checkpoints, docs-only validation.

### D2 — Canonical navigation

Scope: create `docs/README.md`; add minimal links from root README, contributing/development entry
points, and agent bootstrap guidance.

Files: new index plus only the few entry-point files needed for navigation.

Separation rationale: navigation can improve immediately without changing any document's authority.

Acceptance: every current contract and historical collection is reachable; each link labels the
document role; no operating text is duplicated; all links and anchors pass.

### D3 — Current operating-document consolidation

Scope: make prepare-pr, validation, Development, signing, Stable/Hold, and upstream manuals describe
only current operation; create `VALIDATION.md` if the focused audit confirms no adequate owner.

Files: `PREPARE_PR.md`, `UPSTREAM_SYNC.md`, `MOSAIC_DEVELOPMENT_RELEASE.md`, `MOSAIC_SIGNING.md`,
`MOSAIC_STABLE.md`, optional new `VALIDATION.md`, and bounded cross-links.

Separation rationale: operating contracts can be corrected before relocating historical evidence.

Acceptance: each lifecycle has one authoritative current runbook, explicit authority/failure/
recovery boundaries, no contradictory current instructions, and contract tests remain unchanged.

### D4 — Historical evidence separation and navigation

Scope: add the history index; classify and, where link-safe, move completed Item 6, T0-1, I06,
performance, presentation, and migration evidence. Extract long historical blocks from operating
manuals only after their destination is established.

Files: history index, historical records, and all verified inbound links.

Separation rationale: evidence movement is mechanically risky and deserves a dedicated link-focused
change, independent from rewriting current behavior.

Acceptance: no evidence loss; old names remain truthful; every moved/extracted record is reachable;
no historical page presents itself as current operation; repository-wide link check passes.

### D5 — Roadmap, architecture, identity, and handoff reduction

Scope: create/promote stable architecture and identity owners; rename the roadmap if approved;
reduce roadmap chronology; reduce the handoff to current checkpoint, recent continuity, unresolved
risks, invariants, and negative knowledge.

Files: roadmap, handoff, proposed `ARCHITECTURE.md`, identity document, agent references, config/tests
for any renamed machine-adjacent path.

Separation rationale: only safe after current manuals and historical destinations exist.

Acceptance: future work and phase status are quickly visible; stable architecture is not handoff-only;
fresh-session reading is bounded; historical detail remains linked; rename consumers all pass.

### D6 — Public and Wiki boundary

Scope: refine public navigation and prepare reviewed Wiki source material; do not publish without an
explicit operator action.

Files: root public docs and, if useful, versioned `docs/wiki-source/` pages or a Wiki publication
plan. Engineering contracts remain in place.

Separation rationale: user-facing prose depends on settled canonical sources but has different
audience and publication authority.

Acceptance: installation, updating, channels, Seerr setup, troubleshooting, and onboarding are clear;
each operational/security topic links to its repository authority; no second source of truth exists.

### D7 — Final consistency and status audit

Scope: repository-wide link/anchor, naming, status, precedence, and stale-current-language sweep.

Files: documentation-only corrections discovered by the final audit.

Separation rationale: verifies the resulting system rather than combining verification with major
moves.

Acceptance: all topics have one owner; navigation answers every target reader question; no competing
current instructions; history is discoverable; Wiki boundary is explicit; Fast and documentation
checks pass; T0-3 may then be closed. Baseline T0 closes only if its independent acceptance checklist
also passes.

## T0-3 completion criteria

T0-3 is complete only when:

1. `docs/README.md` provides role-labelled navigation for contributors, operators, agents, current
   architecture, roadmap, continuity, and history.
2. Each major topic in the canonical matrix has one explicit current owner.
3. Current operating manuals contain no known contradictory or superseded instructions.
4. Validation and application architecture are no longer available only through agent guidance,
   roadmap chronology, or handoff history.
5. The handoff is bounded to useful continuity and negative knowledge rather than a complete ledger.
6. The roadmap emphasizes direction, remaining work, and major completed phases rather than detailed
   run chronology.
7. Historical evidence remains complete, truthful, navigable, and clearly non-operational.
8. Wholphin occurrences are classified correctly as upstream/source identity, stable protocol,
   historical evidence, or an intentionally retained compatibility/source name.
9. Wiki/public guidance cannot silently diverge from versioned engineering contracts.
10. Repository-relative links, anchors, agent/config consumers, documentation checks, `git diff
    --check`, and Fast validation pass.
11. T0-3 is explicitly closed in the roadmap and handoff only after D7. Baseline T0 is declared
    complete only after its separate acceptance requirements are satisfied.

## D1 acceptance

This branch changes only this audit document plus the one `.gitignore` allowlist entry required to
make the new document visible to Git. It deliberately does not create `docs/README.md`, move history,
rename the roadmap, reduce the handoff, publish Wiki pages, or begin D2.

## D2 and D3 implementation status

D2 created the role-labelled `docs/README.md` index and added only minimal entry-point links. D3
confirmed the missing validation owner, created `docs/VALIDATION.md`, and made the five operating
manuals current-first through concise ownership, boundary, and recovery summaries plus canonical
cross-links. Existing acceptance and incident material remains in place and is explicitly secondary
where it was prominent. No operating behavior changed.

D3 changed no operating behavior and retained existing anchors.

## D4 implementation decisions

D4 created `docs/history/README.md` as the single historical-evidence boundary and replaced the
temporary flat history list in `docs/README.md` with that grouped index. Historical records now
state near their top that they are point-in-time evidence, name the current canonical owner, and
link to the history index where useful.

No existing record moved. Repository inventory found many normal Markdown consumers, including
heading-level links from the roadmap, handoff, validation and delivery manuals, plus likely external
GitHub links to these established paths and anchors. No workflow, script, test, or configuration
consumer required their paths, but relocating the files would create unnecessary anchor and inbound
link risk for no authority benefit. The flat files therefore remain deliberately stable and are
classified through navigation rather than duplicated behind pointer stubs.

The in-place decision applies to `ITEM_6_CONSOLIDATION_CHECKLIST.md`,
`ITEM_6_I05_PRESENTATION.md`, `ITEM_6_UPSTREAM_AUTOMATION_AUDIT.md`,
`T0_1_OPERATOR_UX_INVENTORY.md`, `T0_1_CP7_PERFORMANCE_AUDIT.md`,
`I06_NATIVE_UPSTREAM_MIGRATION_PLAN.md`, `I07_PUBLISHED_RELEASE_REMEDIATION.md`, and
`RELEASE_PIPELINE_SIMPLIFICATION_PLAN.md`. Each is now indexed and has an explicit historical
boundary near its top; the two Item 6 records that already had accurate historical banners received
only navigation links.

No long acceptance block was extracted from a current manual. D3 already placed concise current
contracts first and labelled embedded acceptance sections historical. Those headings have inbound
links from the roadmap/handoff and may have external references, so D4 preserved them in place and
used the history index to make their role unambiguous. `T0_3_R2_MOSAIC_IDENTITY.md` likewise remains
the current identity owner until D5; its acceptance sections are explicitly historical without
renaming or splitting the file.

At D4 closure, evidence and historical names remained unchanged, established paths and anchors were
preserved, and current ownership remained with the D3 manuals. D5 subsequently resolved the
roadmap, architecture, identity, handoff, and agent-guidance decisions recorded below.

## D5 implementation decisions

D5 created `ARCHITECTURE.md`, `MOSAIC_IDENTITY.md`, and `MOSAIC_ROADMAP.md` as the permanent current
owners for stable architecture, identity, and product/engineering direction. Established
`Wholphin_ROADMAP.md` and `T0_3_R2_MOSAIC_IDENTITY.md` paths remain as explicitly historical records
because their many heading-level and likely external links make deletion or stub replacement unsafe.
Current consumers now point to the Mosaic-named owners.

`CODEX_HANDOFF.md` was reduced from the multi-thousand-line ledger to bounded current continuity,
invariants, risks, negative knowledge, and canonical links. Its complete pre-D5 bytes are preserved
as `history/CODEX_HANDOFF_PRE_D5.txt`; the machine-required live filename remains unchanged.

`AGENTS.md` remains the unambiguous general authority. `UI_AGENTS.md` now contains only scoped
Android TV presentation, focus, navigation, shared-state, and manual-validation guidance, with
explicit precedence back to `AGENTS.md`. Roadmap path consumers in agent guidance, upstream docs,
and `prepare-pr.config.psd1` migrated to `MOSAIC_ROADMAP.md`; architecture and identity are retained
as high-risk documentation inputs rather than weakening classification.

At D5 closure, D6 public/Wiki work and D7 final consistency remained open; T0-3 and Baseline T0
were not complete.

## D6 implementation decisions

D6 keeps the root `README.md` as the concise public product entry point and creates
`USER_GUIDE.md` as the versioned public owner for installation, updates, Stable versus Development,
optional Seerr integration, bounded troubleshooting, and bug-reporting orientation. Contributor
entry points now route explicitly to `PREPARE_PR.md`, `VALIDATION.md`, and the documentation index
instead of restating those contracts.

No `docs/wiki-source/` tree was created. Maintaining parallel Wiki-source pages would duplicate the
new user guide before a Wiki publication workflow or demonstrated audience need exists. A future
GitHub Wiki is therefore an optional presentation layer: it may summarize user-facing subjects,
must link its versioned repository source, cannot own engineering or security contracts, and may be
published only through a separate explicit operator action.

`docs/README.md` now distinguishes public product documentation, contributor orientation,
versioned engineering authority, current continuity, history, and the optional Wiki boundary. No
validation, publication, signing, provenance, Hold, repository-authentication, or upstream contract
changed. D7 remains the final consistency and status audit; T0-3 and Baseline T0 remain open.
