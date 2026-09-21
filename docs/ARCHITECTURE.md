# Mosaic architecture

This document is the canonical owner for Mosaic's stable application and engineering architecture.
It describes boundaries and direction, not implementation chronology or operator procedure.

## Product model

Mosaic is a downstream Android TV product derived from
[Wholphin](https://github.com/damontecres/Wholphin). Jellyfin remains the source of truth for the
local, playable library. Seerr extends the experience with discovery, requests, availability,
acquisition state, watchlists, and metadata for content not yet present in Jellyfin.

Extended integrations are optional. When they are disabled, unreachable, or incomplete, ordinary
Jellyfin browsing and playback must remain usable. Mosaic should present one coherent media product,
while retaining truthful source authority rather than treating every backend signal as equivalent.

## Application structure

The Android application follows the existing navigation, ViewModel, repository/service, and shared
domain/data boundaries. New product state should be computed outside individual composables and
made reusable across Library, Series, Downloads, Discover, Watchlist, Collections, and Suggestions.
Adoption is incremental: existing upstream models and screens remain valid consumers.

### Authority and state

- **Jellyfin library/playability:** only fresh usable Jellyfin items prove that content can be
  navigated to or played.
- **Seerr discovery/request state:** describes catalog availability, request intent, and request
  lifecycle; it does not prove Jellyfin readiness.
- **Acquisition lifecycle:** answers whether content is queued, downloading, importing, recently
  completed, or awaiting Jellyfin discovery. It is transient operational state.
- **Library integrity:** compares persisted trusted released-episode expectations with fresh
  Jellyfin playability and current acquisition coverage. It is not acquisition history.
- **Persistent season expectations:** retain only stable identity and expected released episode
  numbers. Live counts, missing results, and complete/incomplete conclusions are recomputed.

TV navigation should use the most specific verified Jellyfin destination available, including exact
season and episode identities. Seerr availability alone must never be used as a playback target.

### Shared media direction

Media identity and independently sourced state should support composition rather than screen-local
copies. A title may simultaneously be local, partially available, requested, acquiring, incomplete,
watchlisted, related to a collection, or eligible for an upgrade. Capabilities should gate
fork-added orchestration at semantic boundaries without disabling unrelated upstream behavior,
bug fixes, navigation, or presentation.

## Engineering and delivery architecture

Canonical operating owners are:

| Concern | Owner |
| --- | --- |
| Pull-request preparation and publication | [PREPARE_PR.md](PREPARE_PR.md) |
| Local and hosted validation authority | [VALIDATION.md](VALIDATION.md) |
| Development eligibility and publication | [MOSAIC_DEVELOPMENT_RELEASE.md](MOSAIC_DEVELOPMENT_RELEASE.md) |
| Signing identity, custody, isolation, verification | [MOSAIC_SIGNING.md](MOSAIC_SIGNING.md) |
| Stable Promotion and Hold | [MOSAIC_STABLE.md](MOSAIC_STABLE.md) |
| Upstream ownership and integration | [UPSTREAM_SYNC.md](UPSTREAM_SYNC.md) |
| Product and machine identity | [MOSAIC_IDENTITY.md](MOSAIC_IDENTITY.md) |

PR validation and protected-main exact-tree reuse are distinct from Development release
eligibility. Release assembly occurs only in the authenticated final-main context when required.
Signing credentials exist only inside the isolated signing boundary. Stable authenticates and
promotes exact Development bytes; it never rebuilds or re-signs them.

Ordinary downstream publication, protected-branch merge execution, Environment authorization, and
upstream REVIEW/conflict resolution have separate authorities. Automation may prepare and validate
an upstream candidate, but human/Codex semantic resolution and Draft merge/reject authority remain
explicit. Force push and uncertain identity are never recovery mechanisms.

The exact downstream repository is `constbogdan/Mosaic`; the upstream source remains
`damontecres/Wholphin`. Historical `wholphin-*` protocol identities are intentionally stable where
they authenticate existing evidence or candidate lifecycles.

## Historical rationale

Detailed T0, Item 6, I06, I07, performance, delivery, and identity acceptance evidence is grouped in
the [historical engineering index](history/README.md). Historical records explain why these
boundaries exist but do not replace the current owners above.
