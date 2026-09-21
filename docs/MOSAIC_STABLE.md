# Mosaic Stable channel and promotion

Status: **Stable promotion COMPLETE / LIVE VALIDATED** (through downstream-build-77 / Stable
v1.0.77). Permanent signing, downstream
routing, development delivery and in-place updater acceptance remain COMPLETE / LIVE
VALIDATED. Channel selector and existing-user migration are now LIVE VALIDATED through 1.0.5 -> 1.0.8. T0-1 CI/developer-velocity simplification is COMPLETE / HOSTED VALIDATED; further
performance work is evidence-driven rather than a pending migration. No stable release was created
while implementing this contract.

## Stable promotion acceptance

Current operation supersedes the historical form procedure below. Stable Promotion is a
zero-input manual **Prepare -> Release** workflow. Prepare resolves the current rolling
`develop` publication and derives/authenticates protected-main tooling, its immutable
`downstream-build-N`, source SHA/tree, signed APK hash, version, producer run/attempt,
tag/release/assets, signer, package and payload. Its compact summary was historically
`Stable v1.0.N ready for approval`; the R3 correction now leads with
`Mosaic v1.0.N pending approval` and keeps the authenticated immutable build link in technical
details. Release alone uses
`release-promote`; after approval it reauthenticates protected main and proves rolling
`develop` still identifies the exact prepared candidate before invoking the unchanged
exact-byte publisher. Movement or ambiguity refuses instead of selecting another build.

Canonical Mosaic acceptance used run `35495689386`, attempt 1. Prepare authenticated
`downstream-build-73`; Release promoted the exact candidate bytes as latest Stable
`mosaic-v1.0.73` from source `3d2093354090ad81a3d06efbeb98a9d55b6c1b5c`. The immutable,
rolling Development, and Stable APK assets are byte-identical at SHA-256
`1c84efececec51f3b2ae602f1ba5d0d5b2d1334724cbb37760a4c3c455cb04cd`.

### R3 presentation cleanup — complete / hosted validated

Development and immutable provenance retain the fixed `Mosaic-release.apk` plus
`mosaic-release.json` inventory. Stable now publishes the same authenticated bytes under exactly
`Mosaic-vX.Y.Z.apk` plus `Mosaic-vX.Y.Z.json`. Stable and Hold normalize those public names back to
the canonical Development identities before comparing sizes, digests, manifest bytes, and signed
APK provenance. The updater accepts the fixed Development name and one unambiguous canonical
versioned Stable APK; legacy Wholphin aliases remain rejected.

Before approval, lead with product state:

```text
Mosaic vX.Y.Z pending approval
```

Do not show “Explicitly promote the authenticated Development build for normal consumption,” the
prominent “Download the exact candidate APK,” or “Waiting for `release-promote` approval. Nothing
has been published by this run.” Put the immutable candidate under **Technical details**, preferably
as `Authenticated immutable build: downstream-build-N`, with the build identity itself linked to
the authenticated candidate APK. Do not add explanatory prose or a standalone candidate-download
action.

After approval, lead with:

```text
Mosaic vX.Y.Z released

Download Mosaic vX.Y.Z · Release details · Compare changes
```

The download must target the APK on the actual Stable Release, never `downstream-build-N`. The
Stable Release body heading is `Mosaic vX.Y.Z`; remove “Explicitly promoted trusted build for
normal consumption” and retain “Choose Stable in the app update channel for normal updates.” The GitHub
Release API `name` remains `vX.Y.Z` because the installed updater parses that field as the numeric
version; changing it would be an updater-contract migration, not presentation-only cleanup. Preserve
detailed technical provenance beneath the primary product actions.

The durable distinction is:

```text
BEFORE APPROVAL
downstream-build-N = immutable candidate = technical/provenance information

AFTER APPROVAL
mosaic-vX.Y.Z = actual Stable Release = normal product download
```

Stable workflow UX follows **product state first → primary operator action second → technical and
provenance details secondary but available**. Candidate authentication, exact signed-byte
promotion, signer and manifest verification, approval authority, exact inventory, Hold, and every
fail-closed boundary remain unchanged.

Hosted acceptance completed in PR #96 and Stable Promotion run `35571701304`, attempt 1, at tooling
SHA `12b827c675ad828dc474232650ed628314f03940`. Prepare authenticated `downstream-build-77`, rendered
`Mosaic v1.0.77 pending approval`, and linked the immutable candidate only within collapsed technical
details. Release waited on `release-promote`, reauthenticated the same candidate, and rendered
`Mosaic v1.0.77 released` with Stable download, Release-details, and comparison actions. Published
Release `392754451` / `mosaic-v1.0.77` has exactly `Mosaic-v1.0.77.apk` and
`Mosaic-v1.0.77.json`. The Stable APK and immutable Development candidate are byte-identical at
SHA-256 `207a9d2affb5925ca93aa9276d599709b9daf86fb505140a08ff935767d32d84`.

I07 forward-recovery acceptance promoted `downstream-build-34` unchanged in run
`34694610864`. Stable release `387569996` / v1.0.34 became latest with APK SHA-256
`1d84dfb922765b28f75e422e25b7fbdc5123beb86fc0148d5e324f5547514e5d`, identical to
Development. The held v1.0.5 release remains preserved as a prerelease.

The workflow displays **Stable Promotion** and uses the same fixed run identity. Prepare now leads
with `Mosaic v1.0.N pending approval` and contains no explanatory prose or standalone download
action. Collapsed technical details contain only `Authenticated immutable build:
downstream-build-N`, with that identity linked to the authenticated candidate APK.
Successful publication leads with `Mosaic v1.0.N released`, followed by the actual Stable APK,
Stable Release and authenticated comparison links. Exact candidate/source/digest/build-run
provenance stays in collapsed technical details. API name
`v1.0.N`, workflow path, verification/publisher separation, manual authorization and assets remain
unchanged. Historical bodies are not changed. See the [I05 ledger](ITEM_6_I05_PRESENTATION.md).

New Stable bodies lead with the Mosaic product identity and verified Stable state. When an
older valid Stable exists, the publisher derives its immutable `mosaic-v1.0.N` tag from the same
release inventory used for monotonicity/ownership refusal and adds a GitHub Compare Changes link to the
new immutable Stable tag. The Release API `name`, stable/development tags, APK/manifest assets and
updater endpoints remain unchanged. Invalid or mutable comparison identities are never rendered.

User confirms downstream-build-5 / v1.0.5 was promoted unchanged to tag `mosaic-v1.0.5`;
the reported release label is **Mosaic stable 1.0.5**. `/releases/latest` resolves to it.
The updater-visible numeric release-name contract below remains `v1.0.N`; this checkpoint
records the supplied label without changing publisher metadata or parser behavior.
Signed APK SHA-256 remains exactly
`af0dcb7fb1c89800c61e7a7a0558cbb2e6fc65fbf880069dbe08c3c4df8bf578`.
Verification took 38s, publication 20s, total approximately 1m05s. There was no Gradle
build and no signing. Stable promotion is COMPLETE / LIVE VALIDATED and remains manual.

## Exact-byte stable promotion

[Manual promotion workflow](../.github/workflows/mosaic-stable-promotion.yml) runs only
in `constbogdan/Mosaic` on protected main. The four former form values remain exact
authenticated machine facts, but are derived automatically from current protected main
and the current authenticated rolling/immutable Development state. Current-tooling and
original-source CI must pass. Original source must be on the current main first-parent
chain after the pinned epoch. Old source is never executed.

Its final operator-facing job sequence is **Prepare → Release**. Prepare is read-only and
has no mutation Environment. Release alone uses `release-promote`; approval authorizes
the mutation but does not replace the authentication and freshness checks below.

The read-only verification job authenticates the annotated development tag, its canonical
manifest, published development prerelease and exact asset IDs. It downloads the existing
`Mosaic-release.apk` and `mosaic-release.json`; GitHub asset digests, sizes and downloaded
bytes must agree with the ledger and authenticated candidate. Full source/tree/upstream/version
provenance is checked against Git objects and the original successful build job. Fresh SDK
apksigner/aapt verification enforces the pinned single signer, non-debuggable
`io.github.constbogdan.mosaic`, original version and signed hash. No key is available.

Only the separate Release job gets Contents write (plus Actions read). It receives the
successful verification job's immutable Actions artifact ID with digest mismatch rejection,
then rechecks the original source release, manifest, APK and acceptance record. No signing
credentials, Sync Bot, Gradle build, repackaging, alignment mutation or re-signing
exists in promotion. Transport archives do not alter the APK file's bytes.

| Identity | Contract |
| --- | --- |
| Development origin | Existing annotated `downstream-build-N` and its prerelease |
| Stable tag | Annotated `mosaic-v1.0.N` at the SAME original source commit |
| Stable release name | `v1.0.N` |
| Stable assets | Exact Development bytes as `Mosaic-v1.0.N.apk` and `Mosaic-v1.0.N.json` |
| Stable visibility | `prerelease: false`; publish with `make_latest: true` |
| Development | Remains prerelease; `develop` and its assets are not changed |

The local inherited upstream `v1.0.5` tag already exists at
`cbcdb73a45c15b297060e7900f8b7b32cf90332d`; its name cannot safely serve as the permanent
Mosaic namespace even if a remote has not published it. Product-prefixed stable tags
avoid upstream-fetch collisions. No inherited tag is moved or deleted. The numeric
release **name**, not its tag, is what the updater compares.

The stable tag reserves the same canonical provenance before upload. Missing assets can
be added only to a matching draft. Published stable assets/tags are never replaced,
moved, deleted or re-uploaded by this publisher. A matching completed retry verifies exact
assets and performs no mutation. Unknown stable ownership, newer stable versions,
conflicting tags/bytes/manifests or missing published assets fail closed. `/releases/latest`
must identify the promoted stable after completion; numeric rollback is refused.

The stable manifest stays byte-identical to the development manifest. Its
`immutableIdentity: downstream-build-N` and `rollingChannel: develop` describe its origin,
not a stable updater redirect. Stable has independent versioned assets and does not depend
on mutable develop after publication. The shared resolver now tries `mosaic-v1.0.N` for downstream installed-version notes
before legacy version tags; custom repository lookup remains unchanged. No new version identity is allocated by promotion.

Immutability is enforced by the publisher's create-only ledger and conflict checks. This
task adds no external GitHub ruleset or release-immutability setting; administrators could
still mutate externally unprotected objects. Do not do that. Existing rules must permit
creating mosaic-v1.0.N tags/releases with GITHUB_TOKEN Contents write. `release-promote`
adds human authorization but no secrets. External settings were not inspected or changed in this implementation;
permission/rule conflicts must stop publication, never cause a bypass or replacement.

## Retry without build or signing

```text
build failure -> rebuild
sign failure -> reuse authoritative unsigned artifact
development publication failure -> reuse verified signed artifact
stable publication failure -> reauthenticate the exact signed development artifact
```

Retry the zero-input workflow from current protected main. Prepare resolves and
authenticates the then-current Development candidate. The
publisher resumes matching drafts without replacing existing assets. It downloads from
the durable immutable development release, so promotion is independent of seven-day
Actions artifact retention. After a successful stable publication, identical retry verifies
idempotency; an older promotion cannot roll latest back after a newer stable exists.
Development delivery and Stable/Hold publication share the publisher concurrency group.
No heavy validation/build is introduced by a promotion retry.

## Update channel preference and migration

The Updates section uses the existing remote/TV choice control: **Stable**, **Development**,
**Custom**. Automatically check for updates keeps its existing behavior. Only Custom shows
the URL editor; normal users do not need to edit GitHub endpoints. Other Settings sections
are unchanged.

A protobuf `update_channel` field (16) stores the explicit choice. Unspecified legacy
records migrate deterministically using their existing URL:

| Existing configuration | Migrated channel |
| --- | --- |
| Fresh/default or blank legacy URL | Stable |
| Exact bundled downstream stable web/API endpoint | Stable |
| Exact old bundled upstream stable API default | Stable (existing default migration retained) |
| Exact downstream develop web/API endpoint | Development |
| Any other existing URL | Custom, preserving its value |

An explicit stored channel remains authoritative on subsequent loads. Stable resolves
`https://api.github.com/repos/constbogdan/Mosaic/releases/latest`; Development resolves
`https://api.github.com/repos/constbogdan/Mosaic/releases/tags/develop`. Custom uses its
stored release API URL, retaining the existing web-to-API normalization where applicable.
An invalid/empty explicit Custom URL does not silently switch to Stable. A retained custom
URL is ignored in Stable/Development and reappears when Custom is selected; there is no
hidden active override. Fresh/default configurations select Stable. No stable release yet
means no offered Stable update, never an upstream/develop fallback.

Startup automatic checks, manual discovery/download and installed-version notes all
obtain the chosen URL through the shared resolver. Numeric comparison is preserved;
notifications and the installation screen do not offer equal/older versions. A newer
Development installation switched to Stable waits until Stable overtakes it; no downgrade
or uninstall/reset occurs. Invalid sources can report no newer update or an error.

The live 1.0.5 installation's exact manually retained downstream develop API URL migrates
to **Development** when a future selector-containing APK is installed. Promoting the old
build 5 does NOT add the selector to its bytes. It remains the already accepted version
and source; an installed 1.0.5 will not receive an equal-version update solely because the
same bytes are now Stable. Selector exposure, existing-user migration and Custom field exposure were accepted in
1.0.8; presentation/focus polish remains future UX work.

## First live stable promotion procedure (completed; retained reference)

Candidate: `downstream-build-5`, version 1.0.5/code 5, source
`41f9f83c36b8866211c9680d3b416d5ebede4888`, signed SHA-256
`af0dcb7fb1c89800c61e7a7a0558cbb2e6fc65fbf880069dbe08c3c4df8bf578`.
Its build, permanent signing, development publication, real Mosaic download, Android
in-place update, launch and settings preservation are already live validated. No reason
to rebuild or re-sign it has been identified.

That first promotion used four manually supplied machine identities. They remain useful
historical evidence, but current operation derives and authenticates the same facts from
protected main and current Development state. Use the GitHub **Run workflow** control
without inputs, inspect Prepare's authenticated candidate, and authorize Release through
`release-promote`. This document is not live-promotion authorization.

## Current development automation boundary

Development now follows successful protected-main push CI automatically; see the
[current Development contract](MOSAIC_DEVELOPMENT_RELEASE.md#current-delivery-and-recovery-model).
The automatic trigger and device delivery are COMPLETE / LIVE VALIDATED. No normal manual dispatch
is required. Device checks are unchanged and installs remain user-driven. Stable remains
manual, its exact-byte publisher is unchanged, and `release-promote` is the human
authorization boundary.

Future UX TODOs remain separate: broader Settings redesign; cleaner General / Playback /
Library / Downloads / Updates / Integrations / Advanced groups; improved update notifications
if warranted; consistent progress/status UX. Retain the measured CI-overlap work and
quiet prepare-pr/full-log plans without implementing them here.

## Forward recovery policy

Development problems are corrected by a higher-version forward fix. An urgent bad Stable
may be held while that fix is prepared, then the corrected Development build is promoted.
A broken updater requires manual installation of a correctly signed higher-version APK.
There is no rollback, repoint, unhold, Development remediation or generic release-recovery
mechanism.

Stable: fix/revert on main -> higher-version Development -> validate -> manually promote
that exact signed build. If a catastrophic Stable regression prevents launch/updater use,
manually install a newer correctly signed Mosaic APK over the same package without clearing
data. Never mutate/re-version an old APK or force a downgrade. Preserve signing identity.
