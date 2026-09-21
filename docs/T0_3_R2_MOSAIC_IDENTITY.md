# T0-3.0 R2 — canonical Mosaic identity

> **Historical R1–R4 implementation and acceptance record.** Current identity is owned by
> [MOSAIC_IDENTITY.md](MOSAIC_IDENTITY.md). This path and its anchors remain for historical links.

Status: **R2 canonical Mosaic identity — HOSTED VALIDATED. R3 — COMPLETE / HOSTED AND REAL-DEVICE
VALIDATED. R4 — COMPLETE / HOSTED VALIDATED. T0-3.0 Mosaic identity establishment — COMPLETE.**

The **Current identity contract** below records the accepted R2 state but is superseded as the
current owner by [MOSAIC_IDENTITY.md](MOSAIC_IDENTITY.md).
Later R2/R3/R4 implementation, incident, and acceptance sections are historical evidence and do
not define alternate current identities. They remain in place to preserve established paths and
anchors; see the [history index](history/README.md).

## Current identity contract

New downstream state uses one canonical product identity:

| Surface | Identity |
| --- | --- |
| Product | Mosaic |
| Repository | `constbogdan/Mosaic` |
| Android package | `io.github.constbogdan.mosaic` |
| Development APK | `Mosaic-release.apk` |
| Development manifest | `mosaic-release.json` |
| Stable assets | `Mosaic-v1.0.N.apk`, `Mosaic-v1.0.N.json` |
| Custom URI | `mosaic:` |
| Root Gradle project | `Mosaic` |
| Room database for fresh installs | `mosaic` |

Development retains rolling `develop` plus immutable `downstream-build-N` with exactly
`Mosaic-release.apk` and `mosaic-release.json`; Stable retains `mosaic-v1.0.N` and publishes the
same authenticated bytes as `Mosaic-v1.0.N.apk` and `Mosaic-v1.0.N.json`. Neither channel publishes
a legacy Wholphin alias. Existing historical releases and evidence remain untouched.

The updater resolves Stable and Development directly from `constbogdan/Mosaic`; it selects the
fixed Development APK or one unambiguous canonical versioned Stable APK. Package, permanent signer,
increasing `versionCode`, digest, source, and provenance checks remain authoritative. Store-oriented
flavors continue to keep self-update disabled.

## Identities intentionally retained

- `damontecres/Wholphin`, `com.github.damontecres.wholphin`, source paths, upstream-derived class
  names, themes, modules, and translation links truthfully identify upstream heritage.
- `wholphin-pr-policy-v1-*` is the stable exact-tree validation protocol.
- `wholphin-upstream-*` is the stable managed upstream-candidate protocol.
- Current downstream authentication accepts only `constbogdan/Mosaic`. R4 removed the temporary R1
  old-name authorization after hosted and device acceptance; historical old-name evidence remains
  intentionally unchanged.
- Historical PRs, runs, Releases, URLs, assets, and checkpoints keep their truthful old identities.

## Initial installation

Until a store distribution is deliberately established, install Mosaic from
[`constbogdan/Mosaic` Releases](https://github.com/constbogdan/Mosaic/releases/latest):

1. Download `Mosaic-release.apk` from Stable (or intentionally choose `develop` for Development).
2. Permit installs from the chosen browser/file manager when Android requests it.
3. Sideload the APK and confirm Android package `io.github.constbogdan.mosaic`.
4. Future in-app checks use the selected Mosaic Stable or Development channel.

Official upstream Wholphin is a separate application and is not an installation or migration path
for Mosaic.

## GitHub App metadata — manual presentation change

After repository-code review, the operator may edit only the existing installed App's presentation:

- App name/display name: `Wholphin Sync Bot` → `Mosaic Sync Bot`;
- description: identify it as Mosaic's operation-scoped upstream synchronization App;
- homepage/repository URL: `https://github.com/constbogdan/Mosaic`.

GitHub documents the name, description, and homepage URL as editable basic information. The name is
not purely cosmetic: its slug and resulting `[bot]` login may also change. This repository does not
authenticate that login; token issuance is bound to the existing App ID, private key, installation,
selected repository, and operation state. Record the old slug/login, App ID, client ID, and
installation ID before saving, then confirm the same App/installation and credentials remain and
record the resulting single slug/login. Cancel if GitHub requests recreation, reinstallation,
permission approval, key rotation, or any authority change. Do not change webhook/callback fields,
selected repository, permissions (metadata read; contents and pull requests read/write), or the
token-state allowlist. Historical bot attribution remains historical; do not add a dual actor
allowlist.

Repository description/homepage may be updated manually to describe Mosaic as an Android TV
Jellyfin client derived from Wholphin and to link to `https://github.com/constbogdan/Mosaic`.
Topics, fork relationship, rulesets, merge methods, Environments, secrets, variables, visibility,
default branch, and Actions authority need no change.

## Hosted acceptance

The R2 PR must naturally select `ANDROID_FULL`, pass complete offline tooling and Android Full, and
merge through native auto-merge. Protected main must authenticate and reuse the exact tested tree,
then Build → Sign → Publish a new Development release containing exactly:

- `Mosaic-release.apk`;
- `mosaic-release.json`, whose `assetName`, size, and digest authenticate that exact APK.

Inspect the published release itself: verify exact two-asset inventory, package
`io.github.constbogdan.mosaic`, permanent signer, increasing version, accepted source/provenance,
and absence of `Wholphin-release.apk`. Do not promote Stable or dispatch Hold merely as a naming
test; their fail-closed consumers are covered by contract tests. A later intentional Stable
promotion must reuse the same authenticated bytes and canonical inventory.

If hosted validation or publication fails, do not create mixed-name assets manually. Keep the
repository named Mosaic, leave historical releases untouched, and use the existing fail-closed
native-rerun/forward-fix model.

### PR #91 publication incident and hosted recovery

PR #91 head `dbbbfd920694f7f5d830aecd671f9a86f0afc40f` passed authoritative `ANDROID_FULL`
in run `35360523986`. Evidence artifact `10555305386`, digest
`sha256:0f530769a2910adcccc06721096c844d1638d7e572a5b53cef03eb0dbfb5c0ba`, authenticated
synthetic merge `2e3b0ac807e6810048cb71df10ecefc82f58c5f6` and tree
`a1f7a692fd9ffa94899bb765ffcda542c0e91cac`. Native merge
`44fcf58043272ce07b200fab74cb869b21d720d6` retained the same tree and the expected base/head
parents. Protected-main run `35361944415` reused that exact evidence; Build and Sign succeeded and
produced the first canonical signed Mosaic payload. Signed artifact `10554713948`, digest
`sha256:4b0c3d0b78fdaaec140e020afe8055cfa8675568108bd1f778508cddae41500a`, became valid immutable
`downstream-build-72` with exact `Mosaic-release.apk` and `mosaic-release.json`. Rolling v72 did not
publish successfully.

Attempt 1 began rolling mutation before authenticating the complete existing inventory: it removed
recognized mutable state, then encountered historical `Wholphin-release.apk` and correctly refused,
leaving a recoverable partial state. The defect was ordering, not the refusal. After authenticating
immutable v68 and v72, the rolling Release and stale asset ID, current `develop`, and Stable, the
only manual Release-state mutation was the reviewed removal of that stale APK from the mutable draft
Release. This was not the final recovery. The failed-job rerun then exposed the second defect: final
publication omitted explicit `tag_name=develop` and authenticated `target_commitish`, so GitHub
detached Release `385461835` under `untagged-0d30e3a083c52d7b0ee5` while the real `develop` Git ref
remained separate. Final verification correctly rejected it. Neither defect indicated bad APK
bytes, signing, package identity, manifest provenance, repository authority, or weakened
authentication.

PR #92 (`fix/t0-3-r2-development-publisher-atomicity`) head
`f7fd1e6716da3da1e174de08775c2fae58f86f13` passed authoritative `ANDROID_FULL` in run
`35420809976`. Evidence artifact `10576718759`, digest
`sha256:20fb9d0526ebda19030c1530fda217a5920ce3e23cf24b6aa8046bbb123a4289`, authenticated
synthetic merge `fa3cde3ac9acbf941ae05865a1bdd03605a0fd5e` and tree
`5227d6f727222d4f950e70f0fb6b2e3f679277e1`. Native merge
`3d2093354090ad81a3d06efbeb98a9d55b6c1b5c` has the expected base/head parents and the same tree;
protected-main run `35421307208` authenticated and reused it without fallback.

The correction performs complete read-only preflight before rolling mutation; authenticates only
the exact interrupted/detached state; explicitly binds `tag_name=develop` and the authenticated
`target_commitish`; independently authenticates `refs/tags/develop`; verifies the complete final
Release/ref/asset/provenance state; removes only the exact obsolete generated identity after
successful recovery; verifies strictly again; and treats an exact completed state idempotently. Its
focused GitHub Release/Git-ref model and adversarial fixtures cover both defects and interruption
recovery without changing signing, provenance, validation, or publication authority.

The detached v72 state was not accepted as a normal published baseline. Run `35421307208` therefore
selected conservative `unknown / high`, `releaseRequired=true`, and built, signed, and published a
fresh v1.0.73 from source `3d2093354090ad81a3d06efbeb98a9d55b6c1b5c`. Signed artifact
`10576878975` has archive digest
`sha256:840246bcba28378c7cb20f115b94ea6b8463f4385fff48dec37b5dabdb781c16`.
Current rolling `develop` and immutable `downstream-build-73` contain exactly the same two assets:

- `Mosaic-release.apk`, SHA-256
  `1c84efececec51f3b2ae602f1ba5d0d5b2d1334724cbb37760a4c3c455cb04cd`;
- `mosaic-release.json`, GitHub asset digest
  `sha256:b42f178ddf9fd56bf0691afa7c1d92f6026a5ce0c3554a3c593968bf59b7ca2b`.

Both manifests bind package `io.github.constbogdan.mosaic`, version `1.0.73`, source and tree,
permanent signer, run/attempt, and canonical `Mosaic-release.apk`. The rolling Release is
`Development v1.0.73`, published prerelease/non-draft, and `refs/tags/develop` resolves to the v73
source. Canonical links target `constbogdan/Mosaic`; no new `Wholphin-release.apk` exists.
At hosted recovery completion, `downstream-build-72` remained unchanged immutable canonical history,
Stable still identified `mosaic-v1.0.34`, and live inspection found zero `untagged-*` Releases and
zero `untagged-*` Git refs; specifically, `untagged-0d30e3a083c52d7b0ee5` no longer existed in
either namespace.

Status: **Publisher correction — COMPLETE / HOSTED RECOVERY VALIDATED. R2 canonical Mosaic
identity — HOSTED VALIDATED. R3 — COMPLETE / HOSTED AND REAL-DEVICE VALIDATED.**

### Canonical Stable v1.0.73

Stable Promotion run `35495689386`, attempt 1, successfully completed **Prepare → Release**. Prepare
authenticated `downstream-build-73`; Release reauthenticated the candidate and promoted its exact
bytes as latest Stable `mosaic-v1.0.73`, Release `392342793`, from source
`3d2093354090ad81a3d06efbeb98a9d55b6c1b5c`. The immutable candidate, rolling Development, and
Stable each contain exactly `Mosaic-release.apk` and `mosaic-release.json`.

The three published APK assets are byte-identical:

```text
downstream-build-73 APK  ┐
Development v1.0.73 APK ├─ SHA-256 1c84efececec51f3b2ae602f1ba5d0d5b2d1334724cbb37760a4c3c455cb04cd
Stable v1.0.73 APK      ┘
```

All three APK assets are 27,821,299 bytes. Their manifests also share GitHub asset digest
`sha256:b42f178ddf9fd56bf0691afa7c1d92f6026a5ce0c3554a3c593968bf59b7ca2b`. The annotated immutable
and Stable tags both bind source `3d2093354090ad81a3d06efbeb98a9d55b6c1b5c`; rolling `develop`
resolves directly to that source. No `untagged-*` Release or Git ref remains.

The operator installed the APK linked from the immutable `downstream-build-73` candidate in the
Stable Promotion Prepare summary before the final Stable Release existed. Because the candidate,
rolling Development, and final Stable APK digests authenticate as identical, those physical-device
observations remain valid evidence for the exact Stable v1.0.73 bytes.

## R3 real-device acceptance

R2 is complete on the hosted engineering path. R3 is complete through hosted and physical-device
acceptance.

The pre-fix physical-device evidence was:

```text
Android tablet launcher:      Mosaic
Android TV launcher/app menu: Wholphin
```

The manifest already used `android:label="@string/app_name"`, and `app_name` resolved to `Mosaic`.
Android TV separately uses `android:banner="@mipmap/ic_banner"`; both
`app/src/main/res/mipmap-xhdpi/ic_banner.png` and
`app/src/main/res/mipmap-xhdpi/ic_banner_foreground.png` contained the inherited Wholphin
wordmark.

The source defect is **FIXED / HOSTED AND REAL-DEVICE VALIDATED** through PR #95. Both 320×180
banner paths now preserve the established cube,
palette, safe layout, opaque fallback background, and transparent adaptive foreground while using
the Mosaic wordmark. The existing adaptive XML, background color, manifest reference, launcher
intent filters, and package identity are unchanged. The defaultDebug compile, unit-test, and APK
assembly graph passed, and inspection of the generated APK authenticated the manifest reference,
resource table entries, and exact packaged banner bytes. An existing product-identity contract test
now pins the approved banner dimensions and SHA-256 values so accidental restoration of the old
rasters fails. After the fix merged, real-device acceptance confirmed:

```text
TV launcher/app menu → Mosaic
tablet launcher      → Mosaic
```

This closes the Android TV banner finding. The wider update and state-preservation lifecycle was
then closed by the v1.0.73 to v1.0.76 in-place acceptance below.

Repository and hosted evidence cannot close device acceptance. On a fresh Android/Android TV test
device with neither app installed:

1. Download the first canonical `Mosaic-release.apk` and `mosaic-release.json` from the accepted
   Development release; verify manifest digest, package, signer, version, source, and asset name.
2. Sideload the APK. Confirm launcher label Mosaic, package `io.github.constbogdan.mosaic`, and the
   permanent Mosaic certificate.
3. Complete normal Jellyfin setup/login and verify ordinary playback/navigation and fresh Mosaic
   state stored under the new database identity.
4. Launch a representative `mosaic:` search/view/play URI and verify routing; confirm `wholphin:`
   is not registered by Mosaic.
5. Confirm Development discovery requests `constbogdan/Mosaic`, selects `Mosaic-release.apk`, and
   recognizes a strictly newer version. Repeat Stable discovery when an intentional canonical
   Stable candidate exists.
6. Install canonical Mosaic N+1 through the normal updater. Android must treat it as an in-place
   update, not a separate app; package and signer remain unchanged, `versionCode` increases, and
   Jellyfin/login/preferences/database state survive.
7. Confirm no request or downloaded asset requires `constbogdan/Wholphin` or
   `Wholphin-release.apk`.

The operator installed canonical Mosaic v1.0.76 from PR #95 over the existing v1.0.73 installation
without uninstalling. Package and permanent signer continuity produced a normal Android in-place
update. The Android TV launcher/app menu showed Mosaic; login/session, preferences and local app
state survived; and normal launch and use remained functional.

PR #96 then passed authoritative hosted validation and merged the Stable presentation contract.
Stable Promotion run `35571701304`, attempt 1, authenticated `downstream-build-77`, waited on
`release-promote`, reauthenticated the exact handoff, and published byte-identical Stable
`mosaic-v1.0.77`. Prepare and Release summaries rendered the approved pending/released forms, and
Release `392754451` published `Mosaic-v1.0.77.apk` plus `Mosaic-v1.0.77.json`. The Stable APK and
immutable candidate share SHA-256
`207a9d2affb5925ca93aa9276d599709b9daf86fb505140a08ff935767d32d84`.

R3 is therefore **COMPLETE / HOSTED AND REAL-DEVICE VALIDATED**. R4 removed the old repository name
from current authorization and is **COMPLETE / HOSTED VALIDATED** through PR #98, protected-main
exact-tree reuse, and manual Upstream check run `35579104934`. Current downstream authentication is
Mosaic-only; historical old-name evidence remains intentionally unchanged. T0-3.0 Mosaic identity
establishment is complete, and dedicated documentation/wiki/roadmap consolidation is next.

## Visual follow-up

The existing launcher icon is text-free and remains usable. PR #95 replaced the inherited
Wholphin banner wordmark in both Android TV banner assets with the approved Mosaic treatment and
passed real-device acceptance. Reviewing inherited README screenshots remains separate and does
not alter the canonical functional identity above.
