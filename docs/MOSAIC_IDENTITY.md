# Mosaic identity

This document is the permanent current owner for Mosaic product and machine identity. Historical
R1–R4 implementation and acceptance evidence remains in
[the T0-3.0 identity record](T0_3_R2_MOSAIC_IDENTITY.md).

## Canonical downstream identity

| Surface | Current identity |
| --- | --- |
| Product | Mosaic |
| Repository | `constbogdan/Mosaic` |
| Android application ID | `io.github.constbogdan.mosaic` |
| Debug application ID | `io.github.constbogdan.mosaic.debug` |
| Development APK | `Mosaic-release.apk` |
| Development manifest | `mosaic-release.json` |
| Rolling Development channel | `develop` |
| Immutable Development provenance | `downstream-build-N` |
| Stable tag | `mosaic-v1.0.N` |
| Stable assets | `Mosaic-v1.0.N.apk`, `Mosaic-v1.0.N.json` |
| Custom URI | `mosaic:` |
| Root Gradle project | `Mosaic` |
| Fresh-install Room database | `mosaic` |

The updater uses `constbogdan/Mosaic`. Stable discovery uses `/releases/latest`; Development uses
the `develop` release. Stable Release API names remain `v1.0.N` because installed update discovery
parses that numeric identity. Stable assets are versioned; Development assets retain fixed names.

Repository authentication is exact and accepts only `constbogdan/Mosaic`. Lookalikes, forks,
different owners, casing variants, malformed identities, and the retired downstream repository name
are rejected before privileged targeting or credential acquisition.

## Intentionally retained Wholphin identities

These names are not incomplete branding work:

- `damontecres/Wholphin` is the actual upstream repository.
- `com.github.damontecres.wholphin` source namespaces remain inherited implementation identity where
  changing them has no product benefit.
- `wholphin-pr-policy-v1-*` is the stable authenticated PR-evidence protocol/artifact family.
- `wholphin-upstream-*` identifies the established upstream candidate protocol.
- Historical records retain old product, repository, asset, workflow, bot, and protocol names when
  those values were true at the recorded time.

Do not rename a retained machine identity for cosmetic consistency. Any future migration requires a
complete producer/consumer and provenance audit.

## Signing and version identity

The permanent Mosaic signer and Android application ID define update continuity. Android
`versionCode` is derived from protected-main first-parent history and `versionName` is `1.0.N`.
Development and Stable represent the same numeric product identity; Stable promotes the exact
authenticated Development APK bytes under its Stable tag and versioned public filename.

Signing custody and verification are owned by [MOSAIC_SIGNING.md](MOSAIC_SIGNING.md); publication
and updater behavior are owned by the Development and Stable manuals.
