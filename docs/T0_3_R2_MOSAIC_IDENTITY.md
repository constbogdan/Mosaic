# T0-3.0 R2 — canonical Mosaic identity

Status: **IMPLEMENTED / LOCAL VALIDATED; HOSTED + DEVICE ACCEPTANCE PENDING**.

## Current identity contract

New downstream state uses one canonical product identity:

| Surface | Identity |
| --- | --- |
| Product | Mosaic |
| Repository | `constbogdan/Mosaic` |
| Android package | `io.github.constbogdan.mosaic` |
| Public APK | `Mosaic-release.apk` |
| Release manifest | `mosaic-release.json` |
| Custom URI | `mosaic:` |
| Root Gradle project | `Mosaic` |
| Room database for fresh installs | `mosaic` |

Development retains rolling `develop` plus immutable `downstream-build-N`; Stable retains
`mosaic-v1.0.N`. New Development and Stable releases contain exactly `Mosaic-release.apk` and
`mosaic-release.json`. They do not publish or accept a second legacy APK alias. Existing historical
releases and evidence remain untouched.

The updater resolves Stable and Development directly from `constbogdan/Mosaic` and release builds
select the exact `Mosaic-release.apk`. Package, permanent signer, increasing `versionCode`, digest,
source, and provenance checks remain authoritative. Store-oriented flavors continue to keep
self-update disabled.

## Identities intentionally retained

- `damontecres/Wholphin`, `com.github.damontecres.wholphin`, source paths, upstream-derived class
  names, themes, modules, and translation links truthfully identify upstream heritage.
- `wholphin-pr-policy-v1-*` is the stable exact-tree validation protocol.
- `wholphin-upstream-*` is the stable managed upstream-candidate protocol.
- The R1 exact authorization set still contains `constbogdan/Wholphin` and `constbogdan/Mosaic`.
  R4 removes the old repository name only after hosted and device acceptance.
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

## R3 real-device acceptance

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

Only after hosted and device acceptance may R4 remove the old repository name from the R1 bridge.

## Visual follow-up

The existing launcher icon is text-free and remains usable. The Android TV banner contains literal
Wholphin artwork; no approved Mosaic artwork exists, so R2 deliberately does not invent a logo.
Replacing that banner and reviewing inherited README screenshots are post-R2 design work and do not
alter the canonical functional identity above.
